package com.bidr.agent.runtime.provider.openhands;

import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: OpenHandsTurnStreamTest
 * Description: 会话级 socket 帧 → 契约 §5.7 单轮流的适配口径。夹具
 * {@code openhands/socket-*-frames.json} 取自 agent-server 1.49.4 的**实测 socket 帧**
 * （探针 probe19/probe20 录制：{@code sync}/{@code durable(seq,event)}/{@code transient}/
 * {@code item_started}/{@code delta}/{@code item_aborted}）。
 * <p>
 * 守的口径：① 轮次绑定在**用户 MessageEvent**（其 id 即 message_id，与历史侧同源）；
 * ② durable 帧不严格按 seq 到（实测状态更新会抢在用户消息前），绑定前的事件要补放，
 * 否则丢 {@code message.started}；③ 上游 {@code delta} 可自由丢弃，故 durable 全量正文要补差额；
 * ④ 挂流恢复 = accepted + snapshot（重放归约）+ 续直播，重放里已终局则合成终局后收流；
 * ⑤ 取消终局来自 {@code item_aborted(cancelled)}（实测中断后状态是 paused 而非 cancelled）；
 * ⑥ 断流 ≠ 取消：链路异常直接上抛，**不臆造终局帧**。
 *
 * @author sharp
 * @since 2026/9/23
 */
class OpenHandsTurnStreamTest {

    private static final String SID = "conv-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 建轮即流 ====================

    @Test
    @DisplayName("建轮即流：绑定用户事件 → accepted/started/思考/工具/正文/done")
    void mapsLiveTurnToContractFrames() throws Exception {
        List<String> out = drain(OpenHandsTurnStream.forNewTurn(
                new ScriptedSource(frames("/openhands/socket-live-frames.json")),
                SID, "default", "1+1 等于几", 5000));

        assertEquals("[message.accepted, message.started, thinking.delta, thinking.delta, "
                        + "tool.call, tool.result, text.delta, text.delta, message.done]",
                types(out).toString());

        JsonNode accepted = MAPPER.readTree(out.get(0));
        assertEquals(SID, accepted.path("session_id").asText());
        assertEquals("u-1", accepted.path("message_id").asText(), "message_id = 用户 MessageEvent 的 id");

        JsonNode call = MAPPER.readTree(out.get(4));
        assertEquals("terminal", call.path("data").path("tool_name").asText());
        assertEquals("call_7bcad", call.path("data").path("tool_call_id").asText());
        assertEquals("echo 2", call.path("data").path("arguments").path("command").asText());

        JsonNode result = MAPPER.readTree(out.get(5));
        assertEquals("2", result.path("data").path("output").asText());

        JsonNode done = MAPPER.readTree(out.get(out.size() - 1));
        assertEquals("1 + 1 = 2", done.path("data").path("text").asText(), "done 带全文（权威冲正）");
        assertEquals(190, done.path("data").path("usage").path("prompt_tokens").asLong(),
                "59100 - 58910（基线取连接时 full_state 的会话累计值）");
        assertEquals(73, done.path("data").path("usage").path("completion_tokens").asLong());
    }

    @Test
    @DisplayName("非本轮用户消息不绑定（同会话并发发送时不误认）")
    void skipsOtherTurnsUserMessage() throws Exception {
        List<String> out = drain(OpenHandsTurnStream.forNewTurn(
                new ScriptedSource(frames("/openhands/socket-live-frames.json")),
                SID, "default", "别人的问题", 5000));

        assertTrue(out.isEmpty(), "内容不匹配则不绑定，也就没有任何帧");
    }

    @Test
    @DisplayName("增量丢失自愈：delta 被丢弃时，durable 全量正文补成一帧")
    void healsLostDeltas() throws Exception {
        List<String> script = new ArrayList<>();
        for (String frame : frames("/openhands/socket-live-frames.json")) {
            JsonNode node = MAPPER.readTree(frame);
            boolean lostText = "delta".equals(node.path("type").asText())
                    && "text".equals(node.path("kind").asText());
            if (!lostText) {
                script.add(frame);
            }
        }

        List<String> out = drain(OpenHandsTurnStream.forNewTurn(
                new ScriptedSource(script), SID, "default", "1+1 等于几", 5000));

        assertEquals("[message.accepted, message.started, thinking.delta, thinking.delta, "
                        + "tool.call, tool.result, text.delta, message.done]",
                types(out).toString());
        JsonNode healed = MAPPER.readTree(out.get(6));
        assertEquals("1 + 1 = 2", healed.path("data").path("delta").asText(), "整段正文作为增量补齐");
    }

    // ==================== 挂流恢复 ====================

    @Test
    @DisplayName("挂流恢复：REST 前缀归约成 accepted + snapshot，socket 只续直播到 done")
    void attachReplaysSnapshotThenGoesLive() throws Exception {
        List<String> recorded = frames("/openhands/socket-replay-frames.json");
        OpenHandsEventCodec.AttachPrefix prefix = OpenHandsEventCodec.attachPrefix(
                durableEvents(recorded, 23), SID, "u-1", "default");

        List<String> out = drain(OpenHandsTurnStream.forAttach(
                new ScriptedSource(liveScript(recorded, 23)), SID, "default", "u-1", prefix, 5000));

        assertEquals("[message.accepted, message.snapshot, text.delta, text.delta, message.done]",
                types(out).toString());

        JsonNode snapshot = MAPPER.readTree(out.get(1));
        assertEquals("u-1", snapshot.path("message_id").asText());
        assertFalse(snapshot.path("data").path("truncated").asBoolean());
        assertEquals("[message.started, tool.call, tool.result]", innerTypes(snapshot).toString(),
                "重放里已落盘的工具步归约进快照，正文增量留给直播");
        assertEquals("1 + 1 = 2", MAPPER.readTree(out.get(4)).path("data").path("text").asText());
    }

    @Test
    @DisplayName("晚挂且已终局：前缀里就有终局 → 快照 + 合成终局帧后收流")
    void attachSynthesizesTerminalFromReplay() throws Exception {
        List<String> recorded = frames("/openhands/socket-replay-frames.json");
        OpenHandsEventCodec.AttachPrefix prefix = OpenHandsEventCodec.attachPrefix(
                durableEvents(recorded, 27), SID, "u-1", "default");

        // 已跑完的会话挂流后 socket 是静默的：脚本为空即模拟这种"没有后续帧"
        List<String> out = drain(OpenHandsTurnStream.forAttach(
                new ScriptedSource(new ArrayList<String>()), SID, "default", "u-1", prefix, 5000));

        assertEquals("[message.accepted, message.snapshot, message.done]", types(out).toString());
        JsonNode done = MAPPER.readTree(out.get(2));
        assertEquals("1 + 1 = 2", done.path("data").path("text").asText());
        assertEquals(190, done.path("data").path("usage").path("prompt_tokens").asLong(),
                "59100 - 58910（基线取本轮之前最后一次 stats）");
        assertEquals("[message.started, tool.call, tool.result, text.delta]",
                innerTypes(MAPPER.readTree(out.get(1))).toString(),
                "历史无增量，正文以单帧 text.delta 落进快照（与直播同形）");
    }

    @Test
    @DisplayName("前缀事件 id 去重：REST 与直播窗口重叠时不产生重复块")
    void deduplicatesPrefixEvents() throws Exception {
        List<String> recorded = frames("/openhands/socket-replay-frames.json");
        OpenHandsEventCodec.AttachPrefix prefix = OpenHandsEventCodec.attachPrefix(
                durableEvents(recorded, 27), SID, "u-1", "default");

        // 直播窗口把已归约过的事件又推了一遍（重叠窗口的真实形态）
        List<String> out = drain(OpenHandsTurnStream.forAttach(
                new ScriptedSource(liveScript(recorded, 20)), SID, "default", "u-1", prefix, 5000));

        assertEquals("[message.accepted, message.snapshot, message.done]", types(out).toString(),
                "重复的 tool.call/tool.result 必须被 id 去重吃掉");
    }

    // ==================== 终局与断流 ====================

    @Test
    @DisplayName("取消：item_aborted(cancelled) → message.cancelled，其后 paused/InterruptEvent 不再产帧")
    void cancelIsTerminalAndFreezes() throws Exception {
        List<String> script = new ArrayList<>();
        script.add("{\"type\":\"sync\",\"through_seq\":9}");
        script.add(durable(1, "{\"id\":\"u-c\",\"timestamp\":\"2026-09-23T06:03:46\",\"source\":\"user\","
                + "\"llm_message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"sleep 15\"}]},"
                + "\"kind\":\"MessageEvent\"}"));
        script.add(durable(3, state("execution_status", "\"running\"")));
        script.add("{\"type\":\"item_aborted\",\"item_id\":\"x-1\",\"attempt\":1,\"reason\":\"cancelled\"}");
        script.add(durable(8, state("execution_status", "\"paused\"")));
        script.add(durable(9, "{\"id\":\"i-9\",\"timestamp\":\"2026-09-23T06:04:02\",\"source\":\"user\","
                + "\"kind\":\"InterruptEvent\"}"));

        List<String> out = drain(OpenHandsTurnStream.forNewTurn(
                new ScriptedSource(script), SID, "default", "sleep 15", 5000));

        assertEquals("[message.accepted, message.started, message.cancelled]", types(out).toString());
        JsonNode cancelled = MAPPER.readTree(out.get(2));
        assertEquals("user.request", cancelled.path("data").path("reason").asText());
    }

    @Test
    @DisplayName("会话级错误事件 → message.error（带 code/message）")
    void conversationErrorMapsToErrorFrame() throws Exception {
        List<String> script = new ArrayList<>();
        script.add("{\"type\":\"sync\",\"through_seq\":5}");
        script.add(durable(1, "{\"id\":\"u-e\",\"timestamp\":\"2026-09-23T06:03:46\",\"source\":\"user\","
                + "\"llm_message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"跑一下\"}]},"
                + "\"kind\":\"MessageEvent\"}"));
        script.add(durable(4, "{\"id\":\"e-4\",\"timestamp\":\"2026-09-23T06:03:50\",\"source\":\"environment\","
                + "\"code\":\"LLMError\",\"detail\":\"upstream 500\",\"kind\":\"ConversationErrorEvent\"}"));

        List<String> out = drain(OpenHandsTurnStream.forNewTurn(
                new ScriptedSource(script), SID, "default", "跑一下", 5000));

        assertEquals("[message.accepted, message.error]", types(out).toString());
        JsonNode error = MAPPER.readTree(out.get(1));
        assertEquals("LLMError", error.path("data").path("code").asText());
        assertEquals("upstream 500", error.path("data").path("message").asText());
    }

    @Test
    @DisplayName("断流 ≠ 取消：链路异常上抛，且不产终局帧")
    void socketFailurePropagatesWithoutTerminal() {
        ScriptedSource source = new ScriptedSource(new ArrayList<String>());
        source.failAfter(0, new IOException("connection reset"));
        RuntimeTurnLink link = OpenHandsTurnStream.forNewTurn(source, SID, "default", "hi", 5000);

        IOException error = assertThrows(IOException.class, link::nextFrame);
        assertEquals("connection reset", error.getMessage());
    }

    @Test
    @DisplayName("close 只收 socket，不触碰上游（不调 interrupt）")
    void closeOnlyClosesSocket() throws Exception {
        ScriptedSource source = new ScriptedSource(frames("/openhands/socket-live-frames.json"));
        RuntimeTurnLink link = OpenHandsTurnStream.forNewTurn(source, SID, "default", "1+1 等于几", 5000);
        link.nextFrame();
        link.close();
        assertTrue(source.closed, "socket 必须被收流");
    }

    // ==================== 夹具与工具 ====================

    private static String durable(long seq, String eventJson) {
        return "{\"type\":\"durable\",\"seq\":" + seq + ",\"event\":" + eventJson + "}";
    }

    private static String state(String key, String valueJson) {
        return "{\"id\":\"st-" + key + "\",\"timestamp\":\"2026-09-23T06:03:47\",\"source\":\"environment\","
                + "\"key\":\"" + key + "\",\"value\":" + valueJson + ",\"kind\":\"ConversationStateUpdateEvent\"}";
    }

    private static List<String> frames(String resource) throws Exception {
        try (InputStream in = OpenHandsTurnStreamTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "缺少夹具：" + resource);
            JsonNode array = MAPPER.readTree(in);
            List<String> frames = new ArrayList<>();
            for (JsonNode item : array) {
                frames.add(item.toString());
            }
            return frames;
        }
    }

    /**
     * 录制的 socket 帧 → "REST 侧读得到的事件"（seq ≤ throughSeq 的 durable 事件，时间升序）
     */
    private static List<JsonNode> durableEvents(List<String> recorded, long throughSeq) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (String frame : recorded) {
            JsonNode node = MAPPER.readTree(frame);
            if ("durable".equals(node.path("type").asText()) && node.path("seq").asLong() <= throughSeq) {
                events.add(node.path("event"));
            }
        }
        return events;
    }

    /**
     * 录制的 socket 帧 → 直播窗口（seq > throughSeq 的 durable + 所有非 durable 帧）
     */
    private static List<String> liveScript(List<String> recorded, long throughSeq) throws Exception {
        List<String> script = new ArrayList<>();
        for (String frame : recorded) {
            JsonNode node = MAPPER.readTree(frame);
            boolean durable = "durable".equals(node.path("type").asText());
            if (!durable || node.path("seq").asLong() > throughSeq) {
                script.add(frame);
            }
        }
        return script;
    }

    private static List<String> innerTypes(JsonNode snapshot) {
        List<String> inner = new ArrayList<>();
        for (JsonNode frame : snapshot.path("data").path("frames")) {
            inner.add(frame.path("type").asText());
        }
        return inner;
    }

    private static List<String> drain(RuntimeTurnLink link) throws Exception {
        List<String> out = new ArrayList<>();
        String frame;
        while ((frame = link.nextFrame()) != null) {
            out.add(frame);
        }
        return out;
    }

    private static List<String> types(List<String> frames) throws Exception {
        List<String> types = new ArrayList<>();
        for (String frame : frames) {
            types.add(MAPPER.readTree(frame).path("type").asText());
        }
        return types;
    }

    /**
     * 脚本化帧源：按序吐帧，吐完即"对端收流"（返回 null）；可设定在第 N 帧后抛异常模拟断流
     */
    private static final class ScriptedSource implements FrameSource {

        private final Iterator<String> script;
        private int remaining;
        private IOException failure;
        private boolean closed;

        ScriptedSource(List<String> frames) {
            this.script = frames.iterator();
            this.remaining = frames.size();
        }

        void failAfter(int frames, IOException failure) {
            this.remaining = frames;
            this.failure = failure;
        }

        @Override
        public String nextFrame(long timeoutMs) throws IOException {
            if (remaining-- <= 0) {
                if (failure != null) {
                    throw failure;
                }
                return null;
            }
            return script.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
