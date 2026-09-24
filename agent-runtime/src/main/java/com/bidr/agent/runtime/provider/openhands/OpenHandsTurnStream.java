package com.bidr.agent.runtime.provider.openhands;

import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.FRAME_CANCELLED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.FRAME_DONE;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.FRAME_ERROR;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.FRAME_TEXT_DELTA;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.FRAME_THINKING_DELTA;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_ACTION;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_AGENT_ERROR;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_CONV_ERROR;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_INTERRUPT;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_OBSERVATION;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.KIND_PAUSE;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.REPLY_CANCELLED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.REPLY_COMPLETED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.REPLY_FAILED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.STATUS_ERROR;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.STATUS_FINISHED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.STATUS_PAUSED;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.STATUS_RUNNING;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.STATUS_STUCK;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.acceptedFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.deltaFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.executionStatus;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.id;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.isAgentMessage;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.isUserMessage;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.kind;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.messageText;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.observationText;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.snapshotFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.startedFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.statsValue;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.terminalFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.toolCallFrame;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.toolCallId;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.toolName;
import static com.bidr.agent.runtime.provider.openhands.OpenHandsEventCodec.toolResultFrame;

/**
 * Title: OpenHandsTurnStream
 * Description: OpenHands **会话级 socket** → 我方**单轮流**的适配器（{@link RuntimeTurnLink} 实现）。
 * <p>
 * 上游一个 socket 承载整条会话（多轮交错），我方契约是"一轮一条流"，故本类做两件事：
 * <ol>
 * <li><b>轮次绑定</b>：{@code openTurn} 先连后发，把连接后见到的首个用户 {@code MessageEvent}
 * 认作本轮（并用 {@code expectedContent} 二次校验，防同会话并发发送时误认），其 {@code event.id}
 * 即我方 {@code message_id}——与历史侧同一口径（上游 {@code last_user_message_id} 也是它），故两路同形；</li>
 * <li><b>帧映射</b>：{@code delta(kind=text|reasoning)} → {@code text.delta}/{@code thinking.delta}；
 * {@code durable ActionEvent} → {@code tool.call}；{@code durable ObservationEvent} → {@code tool.result}；
 * {@code durable MessageEvent(agent)} → 补齐增量差额；{@code execution_status=finished} → {@code message.done}；
 * {@code item_aborted(cancelled)}/{@code InterruptEvent}/{@code paused} → {@code message.cancelled}；
 * {@code ConversationErrorEvent}/{@code error|stuck} → {@code message.error}。</li>
 * </ol>
 * <b>挂流恢复不用 socket 重放</b>：前缀由调用方经 REST 归约好（{@link OpenHandsEventCodec.AttachPrefix}）
 * 再注入本流，socket 只连直播、并按事件 id 去重。理由是实测两条：① socket 重放完就静默，
 * 没有"重放结束"的可靠信号（已终局的会话挂流会一直等到空闲超时）；② durable 帧不严格按 {@code seq}
 * 到（实测状态更新会抢在用户消息前），按 seq 判定重放边界会漏事件、导致 UI 出现重复块。
 * REST 前缀 + 先连后读 + id 去重则既不重也不漏。
 * <p>
 * <b>不变式</b>：{@link #close()} 只关 socket，**绝不**调 {@code /interrupt}（断流 ≠ 取消）；
 * socket 层 {@code error} 帧是链路问题、不是轮次失败，按断流上抛由客户端重挂（不臆造终局）。
 * <p>
 * 增量丢失自愈：上游 {@code delta} 可自由丢弃（协议明文），故收到 {@code durable MessageEvent} 时
 * 用其全量正文补齐差额；终局 {@code message.done} 再带一次全文（契约「全文权威，冲正覆盖」）。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Slf4j
final class OpenHandsTurnStream implements RuntimeTurnLink {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 绑定前缓冲上限：正常一两条（状态更新），设限只为防止迟迟绑不上时无界增长
     */
    private static final int PRE_BIND_MAX = 64;

    private final FrameSource socket;
    private final String sessionId;
    private final String agentCode;
    private final long idleTimeoutMs;

    /**
     * 本轮用户输入原文（建轮即流时用于确认绑定的是自己那一轮）
     */
    private final String expectedContent;

    /**
     * 待发帧（仅泵线程访问，无需同步）
     */
    private final Deque<String> pending = new ArrayDeque<>();

    /**
     * 增量累积：item_id → 已下发正文（用于与 durable 全量对账）
     */
    private final Map<String, StringBuilder> streamedText = new HashMap<>();

    /**
     * 绑定轮次之前收到的事件（实测 durable 帧不严格按 seq 到，状态更新可能抢在用户消息前）
     */
    private final List<JsonNode> preBind = new ArrayList<>();

    /**
     * 已进快照的事件 id（挂流恢复时直播侧重放去重）
     */
    private final Set<String> prefixEventIds;

    private String turnId;
    private String finalText = "";
    private OpenHandsEventCodec.TokenUsage baseline;
    private OpenHandsEventCodec.TokenUsage latest;
    private boolean started;
    private boolean terminalSent;

    private OpenHandsTurnStream(FrameSource socket, String sessionId, String agentCode,
                                long idleTimeoutMs, String expectedContent) {
        this.socket = socket;
        this.sessionId = sessionId;
        this.agentCode = agentCode;
        this.idleTimeoutMs = idleTimeoutMs;
        this.expectedContent = expectedContent;
        this.prefixEventIds = new HashSet<>();
    }

    /**
     * 建轮即流：连接后由调用方发消息，本流把首个匹配的用户事件认作本轮
     */
    static OpenHandsTurnStream forNewTurn(FrameSource socket, String sessionId, String agentCode,
                                          String expectedContent, long idleTimeoutMs) {
        return new OpenHandsTurnStream(socket, sessionId, agentCode, idleTimeoutMs, expectedContent);
    }

    /**
     * 挂流恢复：注入 REST 归约好的前缀（accepted + snapshot[+ 合成终局]），socket 只续直播
     */
    static OpenHandsTurnStream forAttach(FrameSource socket, String sessionId, String agentCode,
                                         String turnId, OpenHandsEventCodec.AttachPrefix prefix,
                                         long idleTimeoutMs) {
        OpenHandsTurnStream stream = new OpenHandsTurnStream(socket, sessionId, agentCode,
                idleTimeoutMs, null);
        stream.turnId = turnId;
        stream.prefixEventIds.addAll(prefix.getEventIds());
        stream.finalText = prefix.getFinalText();
        stream.baseline = prefix.getBaseline();
        stream.latest = prefix.getLatest();
        stream.pending.add(acceptedFrame(sessionId, turnId));
        stream.pending.add(snapshotFrame(sessionId, turnId, prefix.getSnapshotFrames()));
        if (prefix.getTerminalFrame() != null) {
            stream.terminalSent = true;
            stream.pending.add(prefix.getTerminalFrame());
        }
        return stream;
    }

    @Override
    public String nextFrame() throws Exception {
        while (pending.isEmpty()) {
            if (terminalSent) {
                return null;
            }
            String raw = socket.nextFrame(idleTimeoutMs);
            if (raw == null) {
                // 对端收流：不臆造终局，交客户端按「未终局 + 断流」重挂
                return null;
            }
            handle(raw);
        }
        return pending.poll();
    }

    @Override
    public void close() {
        socket.close();
    }

    // ==================== 帧处理 ====================

    private void handle(String raw) throws IOException {
        JsonNode frame;
        try {
            frame = MAPPER.readTree(raw);
        } catch (Exception e) {
            log.debug("会话 socket 非 JSON 帧，忽略：{}", raw.length() > 200 ? raw.substring(0, 200) : raw);
            return;
        }
        String type = frame.path("type").asText("");
        if ("durable".equals(type)) {
            liveEvent(frame.path("event"));
            return;
        }
        if ("transient".equals(type)) {
            // 不落盘的事件：实测只有 full_state（连接即推），它带**会话累计**用量，
            // 正好当本轮基线（否则单轮 token 只能算轮内差值，会漏掉首次调用）
            JsonNode event = frame.path("event");
            if ("full_state".equals(event.path("key").asText("")) && baseline == null) {
                JsonNode stats = event.path("value").path("stats");
                if (stats.isObject()) {
                    baseline = OpenHandsEventCodec.TokenUsage.from(stats);
                }
            }
            return;
        }
        if ("delta".equals(type)) {
            delta(frame);
            return;
        }
        if ("item_started".equals(type)) {
            streamedText.put(frame.path("item_id").asText(""), new StringBuilder());
            return;
        }
        if ("item_aborted".equals(type)) {
            if (turnId != null && "cancelled".equals(frame.path("reason").asText(""))) {
                terminal(REPLY_CANCELLED, null, null);
            }
            return;
        }
        if ("error".equals(type)) {
            // socket 级错误（非会话失败）：按断流上抛，由客户端重挂
            throw new IOException("会话 socket 错误：" + frame.path("code").asText("")
                    + " " + frame.path("detail").asText(""));
        }
        // sync 等其余帧：本流不做重放，忽略
    }

    private void delta(JsonNode frame) {
        if (turnId == null) {
            return;
        }
        String content = frame.path("content").asText("");
        if (content.isEmpty()) {
            return;
        }
        boolean reasoning = "reasoning".equals(frame.path("kind").asText("text"));
        pending.add(deltaFrame(reasoning ? FRAME_THINKING_DELTA : FRAME_TEXT_DELTA,
                sessionId, turnId, content));
        if (!reasoning) {
            StringBuilder builder = streamedText.get(frame.path("item_id").asText(""));
            if (builder != null) {
                builder.append(content);
            }
        }
    }

    /**
     * 直播事件 → 契约帧（未绑定轮次前只认用户消息，其余先攒着）
     */
    private void liveEvent(JsonNode event) {
        String kind = kind(event);
        if (kind == null) {
            return;
        }
        if (prefixEventIds.contains(id(event))) {
            // 已进快照（REST 前缀与直播窗口重叠）：跳过，避免过程树出现重复块
            return;
        }
        if (isUserMessage(event)) {
            if (turnId == null) {
                bind(event);
            } else {
                // 本轮尚未终局就来了新的用户消息（同会话并发发送）：以已有全文收口，不悬挂转圈
                log.warn("会话 {} 轮次 {} 未终局即出现新用户消息，按已完成收口", sessionId, turnId);
                terminal(REPLY_COMPLETED, null, null);
            }
            return;
        }
        if (turnId == null) {
            // 实测 durable 帧不严格按 seq 到，绑定前先攒着、绑定后补放，否则会丢 message.started
            if (preBind.size() < PRE_BIND_MAX) {
                preBind.add(event);
            }
            return;
        }
        JsonNode stats = statsValue(event);
        if (stats != null) {
            OpenHandsEventCodec.TokenUsage usage = OpenHandsEventCodec.TokenUsage.from(stats);
            if (baseline == null) {
                baseline = usage;
            }
            latest = usage;
            return;
        }
        if (KIND_ACTION.equals(kind)) {
            if (OpenHandsEventCodec.isFinishAction(event)) {
                // 答复载体：FinishAction.action.message → 正文增量（不是工具步）
                String text = OpenHandsEventCodec.finishMessage(event);
                if (!text.isEmpty()) {
                    pending.add(deltaFrame(FRAME_TEXT_DELTA, sessionId, turnId, text));
                    finalText = text;
                }
            } else {
                pending.add(toolCallFrame(sessionId, turnId, event));
            }
            return;
        }
        if (OpenHandsEventCodec.isFinishObservation(event)) {
            // 回声：正文已由 FinishAction 给出
            return;
        }
        if (KIND_OBSERVATION.equals(kind)) {
            pending.add(toolResultFrame(sessionId, turnId, OpenHandsEventCodec.resultToolName(event),
                    toolCallId(event), OpenHandsEventCodec.resultOutput(event)));
            return;
        }
        if (KIND_AGENT_ERROR.equals(kind)) {
            pending.add(toolResultFrame(sessionId, turnId, toolName(event), toolCallId(event),
                    event.path("error").asText("")));
            return;
        }
        if (isAgentMessage(event)) {
            String text = messageText(event);
            // 与已下发增量对账：丢帧则补差额（终局帧还会再带一次全文）
            StringBuilder builder = streamedText.get(id(event));
            String sent = builder == null ? "" : builder.toString();
            if (text.length() > sent.length() && text.startsWith(sent)) {
                pending.add(deltaFrame(FRAME_TEXT_DELTA, sessionId, turnId, text.substring(sent.length())));
            } else if (builder == null && !text.isEmpty()) {
                // 未直播过的助手消息（如挂流后续到的新消息）：整段作为增量给出
                pending.add(deltaFrame(FRAME_TEXT_DELTA, sessionId, turnId, text));
            }
            streamedText.put(id(event), new StringBuilder(text));
            finalText = text;
            return;
        }
        if (KIND_INTERRUPT.equals(kind) || KIND_PAUSE.equals(kind)) {
            terminal(REPLY_CANCELLED, null, null);
            return;
        }
        if (KIND_CONV_ERROR.equals(kind)) {
            terminal(REPLY_FAILED, event.path("code").asText(null), event.path("detail").asText(null));
            return;
        }
        String status = executionStatus(event);
        if (status == null) {
            return;
        }
        if (STATUS_RUNNING.equals(status)) {
            if (!started) {
                started = true;
                pending.add(startedFrame(sessionId, turnId, agentCode));
            }
            return;
        }
        if (STATUS_FINISHED.equals(status)) {
            terminal(REPLY_COMPLETED, null, null);
        } else if (STATUS_ERROR.equals(status) || STATUS_STUCK.equals(status)) {
            terminal(REPLY_FAILED, "agent." + status, "Agent 运行" + status);
        } else if (STATUS_PAUSED.equals(status)) {
            // 实测：中断轮的状态是 paused（不是 cancelled），故 paused 即取消终局
            terminal(REPLY_CANCELLED, null, null);
        }
    }

    private void bind(JsonNode userMessage) {
        String text = messageText(userMessage);
        if (expectedContent != null && !expectedContent.equals(text)) {
            log.debug("会话 {} 跳过非本轮用户消息（{}）", sessionId, id(userMessage));
            return;
        }
        turnId = id(userMessage);
        pending.add(acceptedFrame(sessionId, turnId));
        // 补放绑定前抢到的事件（如 execution_status=running → message.started）
        List<JsonNode> buffered = new ArrayList<>(preBind);
        preBind.clear();
        for (JsonNode event : buffered) {
            liveEvent(event);
        }
    }

    /**
     * 终局：三态共用出口，发完即定格（后续帧一律忽略，对齐契约「终局定格」）
     */
    private void terminal(String replyStatus, String errorCode, String errorMessage) {
        if (terminalSent || turnId == null) {
            return;
        }
        terminalSent = true;
        OpenHandsEventCodec.TokenUsage usage = latest == null ? null : latest.delta(baseline);
        pending.add(terminalFrame(sessionId, turnId, replyStatus, finalText, usage,
                errorCode, errorMessage));
    }

    /**
     * 诊断用：当前绑定的轮次 id
     */
    String turnId() {
        return turnId;
    }
}
