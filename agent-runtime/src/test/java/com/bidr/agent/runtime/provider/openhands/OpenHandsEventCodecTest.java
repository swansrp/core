package com.bidr.agent.runtime.provider.openhands;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.agent.runtime.dto.TurnBlock;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: OpenHandsEventCodecTest
 * Description: 事件 → 轮次/帧的映射口径。夹具 {@code openhands/history-events.json} 取自
 * agent-server 1.49.4 的**实测事件**（探针 probe17/18 录制，长文本已裁剪、字段名与结构原样保留）。
 * <p>
 * 守的是四条容易悄悄跑偏的口径：① 正文在 {@code llm_message.content[]} 而非顶层；
 * ② {@code tool_call.arguments} 是 JSON 字符串、必须二次解析；③ stats 是**会话累计**、
 * 单轮用量只能取差值且无基线时不给数字；④ {@code finished} 之后还会来 {@code idle}，
 * 不得把 idle 当终局冲正。
 *
 * @author sharp
 * @since 2026/9/23
 */
class OpenHandsEventCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<JsonNode> events(String resource) throws Exception {
        try (InputStream in = OpenHandsEventCodecTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "缺少夹具：" + resource);
            JsonNode array = MAPPER.readTree(in);
            List<JsonNode> events = new ArrayList<>();
            for (JsonNode item : array) {
                events.add(item);
            }
            return events;
        }
    }

    @Test
    @DisplayName("按用户消息切轮：工具轮 / 纯文本轮 / 中断轮")
    void cutsTurnsOnUserMessages() throws Exception {
        List<TurnItem> turns = OpenHandsEventCodec.toTurns(events("/openhands/history-events.json"));

        assertEquals(3, turns.size(), "SystemPrompt 不成轮，三条用户消息 = 三轮");
        assertEquals("u-a", turns.get(0).getMessageId());
        assertTrue(turns.get(0).getUserContent().contains("1+1"), "正文取自 llm_message.content[]");
        assertEquals("u-b", turns.get(1).getMessageId());
        assertEquals("u-c", turns.get(2).getMessageId());
    }

    @Test
    @DisplayName("工具轮落块顺序：tool_call → tool_result → text，入参二次解析")
    void reducesToolTurnBlocks() throws Exception {
        TurnItem turn = OpenHandsEventCodec.toTurns(events("/openhands/history-events.json")).get(0);
        List<TurnBlock> blocks = turn.getReply().getBlocks();

        assertEquals(3, blocks.size());
        assertEquals("tool_call", blocks.get(0).getType());
        assertEquals("terminal", blocks.get(0).getToolName());
        assertEquals("call_10937e", blocks.get(0).getToolCallId());
        assertEquals("List workspace", blocks.get(0).getContent());

        JsonNode arguments = (JsonNode) blocks.get(0).getArguments();
        assertEquals("ls -la /workspace/probe18", arguments.path("command").asText(),
                "arguments 是 JSON 字符串，必须解析成对象再交前端");

        assertEquals("tool_result", blocks.get(1).getType());
        assertEquals("call_10937e", blocks.get(1).getToolCallId());
        assertTrue(blocks.get(1).getOutput().startsWith("total 12"), "输出取自 observation.content[]");

        assertEquals("text", blocks.get(2).getType());
        assertTrue(blocks.get(2).getContent().contains("1 + 1 = 2"));
        assertEquals("completed", turn.getReply().getStatus());
        assertTrue(turn.getReply().getContent().contains("1 + 1 = 2"), "reply.content = 最后一条助手消息");
    }

    @Test
    @DisplayName("中断轮：paused + InterruptEvent → cancelled（不是 failed）")
    void reducesInterruptedTurnAsCancelled() throws Exception {
        TurnItem turn = OpenHandsEventCodec.toTurns(events("/openhands/history-events.json")).get(2);

        assertEquals("cancelled", turn.getReply().getStatus());
        assertTrue(turn.getReply().getBlocks().isEmpty(), "中断轮没有助手产出");
        assertNull(turn.getError());
    }

    @Test
    @DisplayName("finished 之后的 idle 不冲正终局；用量取会话累计差值")
    void keepsTerminalAndComputesUsageDelta() throws Exception {
        List<TurnItem> turns = OpenHandsEventCodec.toTurns(events("/openhands/history-events.json"));

        assertEquals("completed", turns.get(0).getReply().getStatus(), "idle 不得把 completed 冲掉");
        JsonNode firstUsage = (JsonNode) turns.get(0).getReply().getUsage();
        assertEquals(11779, firstUsage.path("prompt_tokens").asLong(),
                "首轮之前无 stats = 会话从 0 起算；且只算到 finished 为止（终局后那次 stats 属 autotitle）");
        assertEquals(406, firstUsage.path("completion_tokens").asLong());

        JsonNode usage = (JsonNode) turns.get(1).getReply().getUsage();
        assertEquals(150, usage.path("prompt_tokens").asLong(),
                "11950 - 11800：基线是上一轮**终局后**那次累计值，本轮只算到 finished 为止");
        assertEquals(5, usage.path("completion_tokens").asLong(), "425 - 420");
        assertEquals("openai/qwen3.8-flash", usage.path("model").asText());
    }

    @Test
    @DisplayName("单轮 → 直播同形帧：started/tool.call/tool.result/text.delta/done（done 带全文）")
    void turnFramesMatchLiveShape() throws Exception {
        List<JsonNode> all = events("/openhands/history-events.json");
        List<JsonNode> turnA = new ArrayList<>();
        boolean inside = false;
        for (JsonNode event : all) {
            if (OpenHandsEventCodec.isUserMessage(event)) {
                if (inside) {
                    break;
                }
                inside = true;
            }
            if (inside) {
                turnA.add(event);
            }
        }

        List<String> frames = OpenHandsEventCodec.turnFrames(turnA, "conv-1", "default", null);
        List<String> types = new ArrayList<>();
        for (String frame : frames) {
            types.add(MAPPER.readTree(frame).path("type").asText());
        }
        assertEquals("[message.started, tool.call, tool.result, text.delta, message.done]", types.toString());

        JsonNode done = MAPPER.readTree(frames.get(frames.size() - 1));
        assertEquals("conv-1", done.path("session_id").asText());
        assertEquals("u-a", done.path("message_id").asText(), "message_id = 该轮用户消息事件 id");
        assertTrue(done.path("data").path("text").asText().contains("1 + 1 = 2"));
        assertEquals(11779, done.path("data").path("usage").path("prompt_tokens").asLong(),
                "基线为 null 即按 0 起算，与历史归约同一口径");
    }

    @Test
    @DisplayName("挂流前缀：切出目标轮、摘出终局、记下已归约事件 id")
    void buildsAttachPrefix() throws Exception {
        List<JsonNode> all = events("/openhands/history-events.json");
        OpenHandsEventCodec.AttachPrefix prefix =
                OpenHandsEventCodec.attachPrefix(all, "conv-1", "u-b", "default");

        List<String> types = new ArrayList<>();
        for (String frame : prefix.getSnapshotFrames()) {
            types.add(MAPPER.readTree(frame).path("type").asText());
        }
        assertEquals("[message.started, text.delta]", types.toString());
        assertEquals("好", prefix.getFinalText());
        assertNotNull(prefix.getTerminalFrame(), "已终局的轮次要合成终局帧");
        assertEquals("message.done", MAPPER.readTree(prefix.getTerminalFrame()).path("type").asText());
        assertEquals(150, MAPPER.readTree(prefix.getTerminalFrame()).path("data").path("usage")
                .path("prompt_tokens").asLong(), "与历史 reply.usage 同一算法（11950 - 11800）");
        assertTrue(prefix.getEventIds().contains("u-b") && prefix.getEventIds().contains("m-b"),
                "已归约事件 id 要能供直播去重");
        assertFalse(prefix.getEventIds().contains("u-c"), "不得越界到下一轮");
    }

    @Test
    @DisplayName("挂流轮次不存在 → 平台码 40403（前端据此收尾，不悬挂转圈）")
    void attachUnknownTurnMapsTo40403() throws Exception {
        List<JsonNode> all = events("/openhands/history-events.json");
        ServiceException error = assertThrows(ServiceException.class,
                () -> OpenHandsEventCodec.attachPrefix(all, "conv-1", "no-such-turn", "default"));
        assertEquals(40403, error.getErrCode().getErrCode());
    }

    @Test
    @DisplayName("快照超上限：截断最旧并置 truncated（契约 A3：5000 帧 / 2MB）")
    void snapshotTruncatesOldest() throws Exception {
        List<String> frames = new ArrayList<>();
        for (int i = 0; i < OpenHandsEventCodec.SNAPSHOT_MAX_FRAMES + 10; i++) {
            frames.add(OpenHandsEventCodec.deltaFrame(OpenHandsEventCodec.FRAME_TEXT_DELTA,
                    "conv-1", "u-a", "x" + i));
        }

        JsonNode snapshot = MAPPER.readTree(
                OpenHandsEventCodec.snapshotFrame("conv-1", "u-a", frames));
        assertEquals(OpenHandsEventCodec.SNAPSHOT_MAX_FRAMES, snapshot.path("data").path("frames").size());
        assertTrue(snapshot.path("data").path("truncated").asBoolean());
        // 截断的是最旧的：留下的最后一帧仍是最新那帧
        JsonNode kept = snapshot.path("data").path("frames");
        assertEquals("x" + (frames.size() - 1),
                kept.get(kept.size() - 1).path("data").path("delta").asText());
    }
}
