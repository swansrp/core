package com.bidr.agent.runtime.provider.openhands;

import com.bidr.llm.agent.runtime.dto.TurnBlock;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: OpenHandsTaskDelegationTest
 * Description: 子 Agent 委派的 OEM 映射口径（夹具 {@code openhands/task-events.json} 取自
 * spike 实测：profile 开 {@code enable_sub_agents} + 放文件式子 agent {@code worker} 后，
 * 真跑一次 {@code task} 工具录下的 ActionEvent/ObservationEvent 原样 JSON）。
 * <p>
 * 🔴 关键事实（与早期假设相反，故单独立档）：OpenHands 的子 agent **不产生子会话**
 * （实测 {@code sub_conversation_ids=[]}、{@code forked_from=null}），子 agent 的报告直接作为
 * 父级 {@code task} 工具的输出回来。所以"缝合"= 把 {@code task} 映射成规范 spawn 容器步
 * （{@code spawn_subagent} + {@code agent_code}），**不去伪造子事件流**。
 * <p>
 * 守的口径：① 直播与历史两路对同一次委派产出**完全相同**的工具名与结果（两路同形）；
 * ② 子 agent 类型进 {@code arguments.agent_code}、任务描述进 {@code label}，前端据此显示
 * "子 Agent · worker"；③ 委派失败包成规范错误 JSON（{@code outcome=failed}），
 * 让前端把容器步标红而不是当成功。
 *
 * @author sharp
 * @since 2026/9/24
 */
class OpenHandsTaskDelegationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<JsonNode> events() throws Exception {
        try (InputStream in = OpenHandsTaskDelegationTest.class.getResourceAsStream("/openhands/task-events.json")) {
            assertNotNull(in, "缺少夹具：/openhands/task-events.json");
            List<JsonNode> list = new ArrayList<>();
            for (JsonNode item : MAPPER.readTree(in)) {
                list.add(item);
            }
            return list;
        }
    }

    private static TurnBlock find(List<TurnBlock> blocks, String type, String toolName) {
        for (TurnBlock block : blocks) {
            if (type.equals(block.getType()) && (toolName == null || toolName.equals(block.getToolName()))) {
                return block;
            }
        }
        return null;
    }

    @Test
    @DisplayName("历史侧：task 委派映射为 spawn 容器步，agent_code 与报告齐备")
    void historyMapsTaskToSpawnStep() throws Exception {
        TurnItem turn = OpenHandsEventCodec.toTurns(events()).get(0);
        List<TurnBlock> blocks = turn.getReply().getBlocks();

        TurnBlock call = find(blocks, "tool_call", OpenHandsEventCodec.SPAWN_TOOL);
        assertNotNull(call, "tool_call 的 tool_name 应为规范 spawn 容器名，实际=" + blockNames(blocks));
        String args = String.valueOf(call.getArguments());
        assertTrue(args.contains("worker"), "arguments 应带子 agent 编码：" + args);
        assertTrue(args.contains("Count entries"), "arguments 应带任务描述 label：" + args);

        TurnBlock result = find(blocks, "tool_result", OpenHandsEventCodec.SPAWN_TOOL);
        assertNotNull(result, "tool_result 也应是 spawn 容器名（与 call 同形），实际=" + blockNames(blocks));
        assertTrue(result.getOutput().contains("**1**"), "子 agent 报告应作为该步结果：" + result.getOutput());
        assertEquals(call.getToolCallId(), result.getToolCallId(), "call/result 必须同 id 供前端配对");

        assertFalse(blocks.stream().anyMatch(b -> "subagent".equals(b.getType())),
                "上游没有子会话事件流，不得凭空造 subagent 容器块");
    }

    @Test
    @DisplayName("直播侧与历史侧对同一次委派产出同样的工具名（两路同形）")
    void liveAndHistoryAgree() throws Exception {
        List<JsonNode> events = events();
        List<String> frames = OpenHandsEventCodec.turnFrames(events, "s-1", "default", null);
        String joined = String.join("\n", frames);
        assertTrue(joined.contains(OpenHandsEventCodec.SPAWN_TOOL), "直播帧应含 spawn 容器名");
        assertTrue(joined.contains("\"agent_code\":\"worker\""), "直播帧应带 agent_code");

        TurnBlock historyCall = find(OpenHandsEventCodec.toTurns(events).get(0).getReply().getBlocks(),
                "tool_call", null);
        assertNotNull(historyCall);
        assertEquals(OpenHandsEventCodec.SPAWN_TOOL, historyCall.getToolName());
    }

    @Test
    @DisplayName("委派失败：输出包成规范错误 JSON 供前端标红")
    void failedTaskBecomesStructuredError() throws Exception {
        JsonNode observation = MAPPER.readTree("{\"kind\":\"ObservationEvent\",\"tool_name\":\"task\","
                + "\"tool_call_id\":\"c1\",\"observation\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"Unknown agent 'worker'.\"}],\"is_error\":true,\"subagent\":\"worker\","
                + "\"status\":\"failed\",\"kind\":\"TaskObservation\"}}");

        String output = OpenHandsEventCodec.resultOutput(observation);
        JsonNode parsed = MAPPER.readTree(output);
        assertEquals("failed", parsed.path("outcome").asText(), "失败须给规范 outcome，前端据此标红");
        assertEquals("worker", parsed.path("agent_code").asText());
        assertTrue(parsed.path("error").asText().contains("Unknown agent"));
        assertEquals(OpenHandsEventCodec.SPAWN_TOOL, OpenHandsEventCodec.resultToolName(observation));
    }

    @Test
    @DisplayName("普通工具不受委派映射影响")
    void plainToolUnaffected() throws Exception {
        JsonNode action = MAPPER.readTree("{\"kind\":\"ActionEvent\",\"tool_name\":\"terminal\","
                + "\"tool_call_id\":\"c9\",\"action\":{\"command\":\"ls -A\",\"kind\":\"TerminalAction\"},"
                + "\"tool_call\":{\"id\":\"c9\",\"name\":\"terminal\",\"arguments\":\"{\\\"command\\\":\\\"ls -A\\\"}\"}}");
        assertFalse(OpenHandsEventCodec.isTaskAction(action));
        String frame = OpenHandsEventCodec.toolCallFrame("s", "t", action);
        assertTrue(frame.contains("\"tool_name\":\"terminal\""), frame);
    }

    private static String blockNames(List<TurnBlock> blocks) {
        StringBuilder builder = new StringBuilder();
        for (TurnBlock block : blocks) {
            builder.append(block.getType()).append(':').append(block.getToolName()).append(" ");
        }
        return builder.toString();
    }
}
