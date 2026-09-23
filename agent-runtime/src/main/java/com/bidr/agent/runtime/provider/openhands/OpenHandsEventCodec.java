package com.bidr.agent.runtime.provider.openhands;

import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.llm.agent.runtime.dto.TurnBlock;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Title: OpenHandsEventCodec
 * Description: OpenHands 事件 → 契约 §5.7 帧 / 场记块的**纯函数**映射（无 IO，可脱离上游单测）。
 * <p>
 * 事件形状口径来自 agent-server 1.49.4 实测（2026-09-23，探针记录见
 * {@code docs/openhands-接入形态决策.md} 附录 A）：
 * <ul>
 * <li>{@code MessageEvent}：正文在 {@code llm_message.content[].text}（**不是**顶层 content）；
 * {@code source=user} 为轮次起点，{@code source=agent} 为助手输出；</li>
 * <li>{@code ActionEvent}：{@code tool_name} + {@code tool_call_id} + {@code tool_call.arguments}
 * （**JSON 字符串**，需二次解析）+ {@code summary}；</li>
 * <li>{@code ObservationEvent}：{@code observation.content[].text} 为输出，
 * {@code observation.is_error} 判失败，{@code action_id} 回指其 ActionEvent；</li>
 * <li>{@code ConversationStateUpdateEvent}：{@code key=execution_status}
 * （running/finished/error/paused/idle）、{@code key=stats}（**会话累计**用量）；</li>
 * <li>{@code AgentErrorEvent}（工具级，会话可继续）/ {@code ConversationErrorEvent}
 * （会话级，{@code code}+{@code detail}，运行转 error）/ {@code InterruptEvent}（用户中断）；</li>
 * <li>{@code StreamingDeltaEvent} 不落盘（只走 WS），故历史里没有增量、只有终稿。</li>
 * </ul>
 * 终局口径（实测）：正常轮以 {@code execution_status=finished} 收尾，随后还会来一个
 * {@code idle}（**不得**把 idle 当终局、也不得用它冲正 completed）；中断轮是
 * {@code item_aborted(cancelled)} → {@code execution_status=paused} → {@code InterruptEvent}。
 *
 * @author sharp
 * @since 2026/9/23
 */
public final class OpenHandsEventCodec {

    /** 轮次起点：用户消息事件 */
    static final String KIND_MESSAGE = "MessageEvent";
    static final String KIND_ACTION = "ActionEvent";
    static final String KIND_OBSERVATION = "ObservationEvent";
    static final String KIND_STATE = "ConversationStateUpdateEvent";
    static final String KIND_AGENT_ERROR = "AgentErrorEvent";
    static final String KIND_CONV_ERROR = "ConversationErrorEvent";
    static final String KIND_INTERRUPT = "InterruptEvent";
    static final String KIND_PAUSE = "PauseEvent";

    static final String SOURCE_USER = "user";

    static final String KEY_EXECUTION_STATUS = "execution_status";
    static final String KEY_STATS = "stats";

    /** 执行状态：实测 finished 之后仍会来 idle，故 idle 不参与终局判定 */
    static final String STATUS_RUNNING = "running";
    static final String STATUS_FINISHED = "finished";
    static final String STATUS_ERROR = "error";
    static final String STATUS_STUCK = "stuck";
    static final String STATUS_PAUSED = "paused";

    static final String REPLY_COMPLETED = "completed";
    static final String REPLY_FAILED = "failed";
    static final String REPLY_CANCELLED = "cancelled";

    /** 契约 §5.7 帧类型 */
    static final String FRAME_ACCEPTED = "message.accepted";
    static final String FRAME_SNAPSHOT = "message.snapshot";
    static final String FRAME_STARTED = "message.started";
    static final String FRAME_TEXT_DELTA = "text.delta";
    static final String FRAME_THINKING_DELTA = "thinking.delta";
    static final String FRAME_TOOL_CALL = "tool.call";
    static final String FRAME_TOOL_RESULT = "tool.result";
    static final String FRAME_DONE = "message.done";
    static final String FRAME_ERROR = "message.error";
    static final String FRAME_CANCELLED = "message.cancelled";

    /** 取消原因取值（契约 A4） */
    static final String CANCEL_BY_USER = "user.request";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 快照上限（对齐契约 A3：5000 帧 / 2MB，先到为准） */
    static final int SNAPSHOT_MAX_FRAMES = 5000;
    static final int SNAPSHOT_MAX_BYTES = 2 * 1024 * 1024;

    private OpenHandsEventCodec() {
    }

    // ==================== 事件读取 ====================

    static String kind(JsonNode event) {
        return event == null ? null : event.path("kind").asText(null);
    }

    static String id(JsonNode event) {
        return event == null ? null : event.path("id").asText(null);
    }

    static String timestamp(JsonNode event) {
        return event == null ? null : event.path("timestamp").asText(null);
    }

    static String source(JsonNode event) {
        return event == null ? null : event.path("source").asText(null);
    }

    static boolean isUserMessage(JsonNode event) {
        return KIND_MESSAGE.equals(kind(event)) && SOURCE_USER.equals(source(event));
    }

    static boolean isAgentMessage(JsonNode event) {
        return KIND_MESSAGE.equals(kind(event)) && !SOURCE_USER.equals(source(event));
    }

    /**
     * 消息正文：{@code llm_message.content[]} 里所有 text 段拼接
     */
    static String messageText(JsonNode event) {
        return contentText(event == null ? null : event.path("llm_message").path("content"));
    }

    /**
     * 工具输出：{@code observation.content[]} 里所有 text 段拼接
     */
    static String observationText(JsonNode event) {
        return contentText(event == null ? null : event.path("observation").path("content"));
    }

    static String toolName(JsonNode event) {
        return event == null ? null : event.path("tool_name").asText(null);
    }

    /**
     * 🔴 结束动作 = 答复载体：实测 OpenHands 正常收口时**不发助手 MessageEvent**，
     * 最终答复在 {@code ActionEvent(kind=FinishAction).action.message}（tool_name=finish）。
     * 若按普通工具映射，答复会被关进 tool_result、正文恒空（历史与直播两路都空）。
     */
    static boolean isFinishAction(JsonNode event) {
        return KIND_ACTION.equals(kind(event))
                && ("finish".equals(toolName(event))
                || "FinishAction".equals(event.path("action").path("kind").asText(null)));
    }

    /** 结束动作的答复正文 */
    static String finishMessage(JsonNode event) {
        return event == null ? "" : event.path("action").path("message").asText("");
    }

    /** finish 的 ObservationEvent 是同一答复的回声，不再作为工具结果下发（否则正文重复 + 假工具步） */
    static boolean isFinishObservation(JsonNode event) {
        return KIND_OBSERVATION.equals(kind(event)) && "finish".equals(toolName(event));
    }

    static String toolCallId(JsonNode event) {
        return event == null ? null : event.path("tool_call_id").asText(null);
    }

    /**
     * 工具入参：{@code tool_call.arguments} 是 **JSON 字符串**，解析成对象后交给前端
     * （解析失败即原样给字符串，绝不吞掉）
     */
    static Object toolArguments(JsonNode event) {
        JsonNode raw = event == null ? null : event.path("tool_call").path("arguments");
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        if (!raw.isTextual()) {
            return raw;
        }
        String text = raw.asText();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 执行状态变更值；非该状态事件返回 null
     */
    static String executionStatus(JsonNode event) {
        if (!KIND_STATE.equals(kind(event)) || !KEY_EXECUTION_STATUS.equals(event.path("key").asText(null))) {
            return null;
        }
        return event.path("value").asText(null);
    }

    /**
     * 用量快照值；非 stats 事件返回 null
     */
    static JsonNode statsValue(JsonNode event) {
        if (!KIND_STATE.equals(kind(event)) || !KEY_STATS.equals(event.path("key").asText(null))) {
            return null;
        }
        return event.path("value");
    }

    // ==================== 帧封装 ====================

    /**
     * 帧封套：{@code {type, session_id, message_id, data}}（字段名 snake_case，与平台一致）
     */
    static String frame(String type, String sessionId, String messageId, ObjectNode data) {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("type", type);
        envelope.put("session_id", sessionId);
        envelope.put("message_id", messageId);
        envelope.set("data", data == null ? MAPPER.createObjectNode() : data);
        return envelope.toString();
    }

    static ObjectNode data() {
        return MAPPER.createObjectNode();
    }

    static String acceptedFrame(String sessionId, String messageId) {
        return frame(FRAME_ACCEPTED, sessionId, messageId, null);
    }

    static String startedFrame(String sessionId, String messageId, String agentCode) {
        ObjectNode data = data();
        if (agentCode != null) {
            data.put("agent_code", agentCode);
        }
        return frame(FRAME_STARTED, sessionId, messageId, data);
    }

    static String deltaFrame(String type, String sessionId, String messageId, String delta) {
        ObjectNode data = data();
        data.put("delta", delta == null ? "" : delta);
        return frame(type, sessionId, messageId, data);
    }

    static String toolCallFrame(String sessionId, String messageId, JsonNode actionEvent) {
        ObjectNode data = data();
        data.put("tool_name", toolName(actionEvent));
        data.put("tool_call_id", toolCallId(actionEvent));
        Object arguments = toolArguments(actionEvent);
        if (arguments instanceof JsonNode) {
            data.set("arguments", (JsonNode) arguments);
        } else if (arguments != null) {
            data.put("arguments", String.valueOf(arguments));
        }
        return frame(FRAME_TOOL_CALL, sessionId, messageId, data);
    }

    static String toolResultFrame(String sessionId, String messageId, String toolName,
                                  String toolCallId, String output) {
        ObjectNode data = data();
        data.put("tool_name", toolName);
        data.put("tool_call_id", toolCallId);
        data.put("output", output == null ? "" : output);
        return frame(FRAME_TOOL_RESULT, sessionId, messageId, data);
    }

    static String doneFrame(String sessionId, String messageId, String text, TokenUsage usage) {
        ObjectNode data = data();
        data.put("text", text == null ? "" : text);
        if (usage != null) {
            data.set("usage", usage.toJson(MAPPER));
        }
        return frame(FRAME_DONE, sessionId, messageId, data);
    }

    static String errorFrame(String sessionId, String messageId, String code, String message) {
        ObjectNode data = data();
        data.put("code", code);
        data.put("message", message);
        return frame(FRAME_ERROR, sessionId, messageId, data);
    }

    static String cancelledFrame(String sessionId, String messageId, String reason) {
        ObjectNode data = data();
        data.put("reason", reason);
        return frame(FRAME_CANCELLED, sessionId, messageId, data);
    }

    /**
     * 快照帧：{@code frames[]} 与直播帧同形；超上限截断最旧并置 {@code truncated:true}
     */
    static String snapshotFrame(String sessionId, String messageId, List<String> frames) {
        ArrayNode array = MAPPER.createArrayNode();
        int dropped = 0;
        int bytes = 0;
        List<String> kept = new ArrayList<>(frames.size());
        for (int i = frames.size() - 1; i >= 0; i--) {
            String item = frames.get(i);
            if (kept.size() >= SNAPSHOT_MAX_FRAMES || bytes + item.length() > SNAPSHOT_MAX_BYTES) {
                dropped = i + 1;
                break;
            }
            kept.add(0, item);
            bytes += item.length();
        }
        for (String item : kept) {
            try {
                array.add(MAPPER.readTree(item));
            } catch (Exception e) {
                // 自建帧不会解析失败；真失败则跳过该帧，不影响其余
            }
        }
        ObjectNode data = data();
        data.set("frames", array);
        data.put("truncated", dropped > 0);
        return frame(FRAME_SNAPSHOT, sessionId, messageId, data);
    }

    // ==================== 历史归约 ====================

    /**
     * 事件序列（时间升序）→ 轮次列表：以 {@code source=user} 的 MessageEvent 切轮，
     * 其余事件按上表落成 blocks；首个用户消息之前的事件（SystemPrompt 等）丢弃。
     * <p>
     * 窗口跨轮复用：上一轮的最后一次 stats 就是下一轮的用量基线（上游 stats 是会话累计值）。
     */
    static List<TurnItem> toTurns(List<JsonNode> events) {
        List<TurnItem> turns = new ArrayList<>();
        TurnWindow window = new TurnWindow();
        for (JsonNode event : events) {
            JsonNode stats = statsValue(event);
            if (stats != null) {
                window.observeUsage(TokenUsage.from(stats));
                continue;
            }
            if (isUserMessage(event)) {
                TurnItem previous = window.close();
                if (previous != null) {
                    turns.add(previous);
                }
                window.start(event);
                continue;
            }
            window.accumulate(event);
        }
        TurnItem last = window.close();
        if (last != null) {
            turns.add(last);
        }
        return turns;
    }

    /**
     * 单轮归约窗口：持有当前轮、用量基线（OpenHands 的 stats 是**会话累计**，
     * 单轮用量只能取差值）与轮内最新用量。
     * <p>
     * 🔴 <b>轮次在终局处截止</b>（不是"到下一条用户消息为止"）：实测上游会在 {@code finished} 之后
     * 异步跑 autotitle（又一次 LLM 调用，约 1.1 万 prompt tokens）并追加 stats，若算进本轮，
     * 历史用量就会比直播多出一次调用（直播在终局帧处收流，看不到它）。故终局之后、下一条用户消息
     * 之前的事件一律不归本轮；但累计值仍要继续跟踪，它是**下一轮**的用量基线。
     */
    private static final class TurnWindow {

        private TurnItem turn;
        private TokenUsage baseline;
        private TokenUsage latest;
        private TokenUsage turnLatest;
        private boolean closed;

        void start(JsonNode userMessage) {
            turn = new TurnItem();
            turn.setMessageId(id(userMessage));
            turn.setUserContent(messageText(userMessage));
            turn.setCreatedAt(timestamp(userMessage));
            // 轮起点之前最后一次 stats 即基线；之前没有任何 stats 就是"会话从 0 起算"
            // ——与直播路径拿连接时 full_state 的累计值当基线同口径，两路用量才对得上
            baseline = latest == null ? TokenUsage.ZERO : latest;
            turnLatest = null;
            closed = false;
        }

        void observeUsage(TokenUsage usage) {
            latest = usage;
            if (!closed) {
                turnLatest = usage;
            }
        }

        void accumulate(JsonNode event) {
            if (turn == null || closed) {
                return;
            }
            String kind = kind(event);
            if (kind == null) {
                return;
            }
            if (KIND_ACTION.equals(kind)) {
                if (isFinishAction(event)) {
                    String text = finishMessage(event);
                    TurnBlock block = new TurnBlock();
                    block.setType("text");
                    block.setContent(text);
                    reply().getBlocks().add(block);
                    reply().setContent(text);
                    if (reply().getStatus() == null) {
                        reply().setStatus(REPLY_COMPLETED);
                    }
                } else {
                    reply().getBlocks().add(callBlock(event));
                }
            } else if (isFinishObservation(event)) {
                // 回声：正文已由 FinishAction 给出
            } else if (KIND_OBSERVATION.equals(kind) || KIND_AGENT_ERROR.equals(kind)) {
                reply().getBlocks().add(resultBlock(event));
            } else if (isAgentMessage(event)) {
                String text = messageText(event);
                TurnBlock block = new TurnBlock();
                block.setType("text");
                block.setContent(text);
                reply().getBlocks().add(block);
                reply().setContent(text);
                if (reply().getStatus() == null) {
                    reply().setStatus(REPLY_COMPLETED);
                }
            } else if (KIND_INTERRUPT.equals(kind) || KIND_PAUSE.equals(kind)) {
                reply().setStatus(REPLY_CANCELLED);
                closed = true;
                return;
            } else if (KIND_CONV_ERROR.equals(kind)) {
                reply().setStatus(REPLY_FAILED);
                turn.setError(event.path("code").asText("") + "：" + event.path("detail").asText(""));
                closed = true;
                return;
            } else {
                String status = executionStatus(event);
                if (status == null) {
                    return;
                }
                if (STATUS_FINISHED.equals(status)) {
                    reply().setStatus(REPLY_COMPLETED);
                    closed = true;
                } else if (STATUS_ERROR.equals(status) || STATUS_STUCK.equals(status)) {
                    reply().setStatus(REPLY_FAILED);
                    closed = true;
                } else if (STATUS_PAUSED.equals(status)) {
                    reply().setStatus(REPLY_CANCELLED);
                    closed = true;
                }
                return;
            }
            reply().setCreatedAt(timestamp(event));
        }

        /**
         * 收口：无助手产出则 reply 保持 null（契约口径）；用量只算到终局为止
         */
        TurnItem close() {
            if (turn == null) {
                return null;
            }
            TurnItem.Reply reply = turn.getReply();
            if (reply != null && turnLatest != null) {
                TokenUsage delta = turnLatest.delta(baseline);
                if (delta != null) {
                    reply.setUsage(delta.toJson(MAPPER));
                }
            }
            return turn;
        }

        private TurnItem.Reply reply() {
            TurnItem.Reply reply = turn.getReply();
            if (reply == null) {
                reply = new TurnItem.Reply();
                turn.setReply(reply);
            }
            if (reply.getBlocks() == null) {
                reply.setBlocks(new ArrayList<TurnBlock>());
            }
            return reply;
        }
    }

    private static TurnBlock callBlock(JsonNode actionEvent) {
        TurnBlock block = new TurnBlock();
        block.setType("tool_call");
        block.setToolName(toolName(actionEvent));
        block.setToolCallId(toolCallId(actionEvent));
        block.setArguments(toolArguments(actionEvent));
        block.setContent(actionEvent.path("summary").asText(null));
        return block;
    }

    private static TurnBlock resultBlock(JsonNode event) {
        TurnBlock block = new TurnBlock();
        block.setType("tool_result");
        block.setToolName(toolName(event));
        block.setToolCallId(toolCallId(event));
        block.setOutput(KIND_AGENT_ERROR.equals(kind(event))
                ? event.path("error").asText("") : observationText(event));
        return block;
    }

    /**
     * 单轮事件（含起点用户消息）→ 快照用的直播同形帧序列（历史侧无增量，正文一次性给全）。
     *
     * @param baseline 本轮**之前**最后一次 stats（会话累计值）；为 null 则不给 usage
     *                 ——必须与 {@link #toTurns} 同一口径，否则历史与快照两路的用量会对不上
     */
    static List<String> turnFrames(List<JsonNode> turnEvents, String sessionId, String agentCode,
                                   TokenUsage baseline) {
        String messageId = turnEvents.isEmpty() ? null : id(turnEvents.get(0));
        List<String> frames = new ArrayList<>();
        // 与直播同形：直播是 accepted → started → 增量 → 终局，快照里 likewise 以 started 打头
        frames.add(startedFrame(sessionId, messageId, agentCode));
        TokenUsage latest = null;
        String finalText = "";
        String terminal = null;
        String errorCode = null;
        String errorMessage = null;
        for (JsonNode event : turnEvents) {
            JsonNode stats = statsValue(event);
            if (stats != null) {
                latest = TokenUsage.from(stats);
                continue;
            }
            String kind = kind(event);
            if (kind == null || isUserMessage(event)) {
                continue;
            }
            if (KIND_ACTION.equals(kind)) {
                if (isFinishAction(event)) {
                    String text = finishMessage(event);
                    if (!text.isEmpty() && finalText.isEmpty()) {
                        finalText = text;
                        frames.add(deltaFrame(FRAME_TEXT_DELTA, sessionId, messageId, text));
                    }
                } else {
                    frames.add(toolCallFrame(sessionId, messageId, event));
                }
            } else if (isFinishObservation(event)) {
                // 回声：正文已由 FinishAction 给出
            } else if (KIND_OBSERVATION.equals(kind)) {
                frames.add(toolResultFrame(sessionId, messageId, toolName(event), toolCallId(event),
                        observationText(event)));
            } else if (KIND_AGENT_ERROR.equals(kind)) {
                frames.add(toolResultFrame(sessionId, messageId, toolName(event), toolCallId(event),
                        event.path("error").asText("")));
            } else if (isAgentMessage(event)) {
                finalText = messageText(event);
                frames.add(deltaFrame(FRAME_TEXT_DELTA, sessionId, messageId, finalText));
            } else if (KIND_INTERRUPT.equals(kind) || KIND_PAUSE.equals(kind)) {
                terminal = REPLY_CANCELLED;
            } else if (KIND_CONV_ERROR.equals(kind)) {
                terminal = REPLY_FAILED;
                errorCode = event.path("code").asText(null);
                errorMessage = event.path("detail").asText(null);
            } else {
                String status = executionStatus(event);
                if (STATUS_FINISHED.equals(status)) {
                    terminal = REPLY_COMPLETED;
                } else if (STATUS_ERROR.equals(status) || STATUS_STUCK.equals(status)) {
                    terminal = REPLY_FAILED;
                } else if (STATUS_PAUSED.equals(status)) {
                    terminal = REPLY_CANCELLED;
                }
            }
            // 轮次在终局处截止：终局之后的事件（如实测的异步 autotitle 及其 stats）不归本轮
            if (isTerminalEvent(event)) {
                break;
            }
        }
        if (terminal != null) {
            frames.add(terminalFrame(sessionId, messageId, terminal, finalText,
                    latest == null ? null : latest.delta(baseline == null ? TokenUsage.ZERO : baseline),
                    errorCode, errorMessage));
        }
        return frames;
    }

    /**
     * 终局帧（三态共用出口：直播终局与历史合成终局走同一路径，保证两路同形）
     */
    static String terminalFrame(String sessionId, String messageId, String replyStatus, String text,
                                TokenUsage usage, String errorCode, String errorMessage) {
        if (REPLY_CANCELLED.equals(replyStatus)) {
            return cancelledFrame(sessionId, messageId, CANCEL_BY_USER);
        }
        if (REPLY_FAILED.equals(replyStatus)) {
            return errorFrame(sessionId, messageId, errorCode, errorMessage);
        }
        return doneFrame(sessionId, messageId, text, usage);
    }

    /**
     * 挂流恢复的前缀：把目标轮**已落盘**的事件归约成快照帧，已终局则另给一个合成终局帧。
     * <p>
     * 为什么不用 socket 自带的全量重放（{@code after_seq=-1}）：实测重放完 socket 就静默，
     * 没有可靠的"重放结束"信号（已终局的会话挂流会一直等到空闲超时）；且 durable 帧不严格按
     * {@code seq} 到，按 seq 判边界会漏事件。REST 归约 + 直播按事件 id 去重则不重不漏。
     *
     * @param events    会话全量事件（时间升序）
     * @param turnId    目标轮次 id（= 该轮用户 MessageEvent 的 id）
     * @throws com.bidr.kernel.exception.ServiceException 轮次不在会话里 → 平台码 40403
     */
    static AttachPrefix attachPrefix(List<JsonNode> events, String sessionId, String turnId,
                                     String agentCode) {
        int from = -1;
        for (int i = 0; i < events.size(); i++) {
            JsonNode event = events.get(i);
            if (isUserMessage(event) && turnId.equals(id(event))) {
                from = i;
                break;
            }
        }
        if (from < 0) {
            throw AgentRuntimeErrors.of(AgentRuntimeErrors.CODE_TURN_MISSING,
                    "轮次 " + turnId + " 在会话 " + sessionId + " 中不存在");
        }
        List<JsonNode> turnEvents = new ArrayList<>();
        for (int i = from; i < events.size(); i++) {
            JsonNode event = events.get(i);
            if (i > from && isUserMessage(event)) {
                break;
            }
            turnEvents.add(event);
        }
        // 用量基线 = 本轮之前最后一次 stats（会话累计值）
        TokenUsage baseline = null;
        for (int i = 0; i < from; i++) {
            JsonNode stats = statsValue(events.get(i));
            if (stats != null) {
                baseline = TokenUsage.from(stats);
            }
        }
        List<String> frames = turnFrames(turnEvents, sessionId, agentCode, baseline);
        String terminal = null;
        if (!frames.isEmpty()) {
            String last = frames.get(frames.size() - 1);
            if (isTerminalFrame(last)) {
                terminal = last;
                frames.remove(frames.size() - 1);
            }
        }
        Set<String> eventIds = new HashSet<>();
        TokenUsage latest = null;
        String finalText = "";
        for (JsonNode event : turnEvents) {
            eventIds.add(id(event));
            JsonNode stats = statsValue(event);
            if (stats != null) {
                latest = TokenUsage.from(stats);
            }
            if (isAgentMessage(event)) {
                finalText = messageText(event);
            }
            if (isTerminalEvent(event)) {
                break;
            }
        }
        return new AttachPrefix(frames, terminal, eventIds, finalText, baseline, latest);
    }

    /**
     * 事件是否构成轮次终局（终局之后、下一条用户消息之前的事件不归本轮——实测那段会有异步
     * autotitle 的又一次 LLM 调用与 stats，算进来会让历史用量比直播多一次调用）
     */
    static boolean isTerminalEvent(JsonNode event) {
        String kind = kind(event);
        if (KIND_INTERRUPT.equals(kind) || KIND_PAUSE.equals(kind) || KIND_CONV_ERROR.equals(kind)) {
            return true;
        }
        String status = executionStatus(event);
        return STATUS_FINISHED.equals(status) || STATUS_ERROR.equals(status)
                || STATUS_STUCK.equals(status) || STATUS_PAUSED.equals(status);
    }

    /**
     * 是否终局帧（终局从快照里摘出来单发，保持契约帧序 accepted → snapshot → 增量 → 终局）
     */
    static boolean isTerminalFrame(String frameJson) {
        try {
            String type = MAPPER.readTree(frameJson).path("type").asText("");
            return FRAME_DONE.equals(type) || FRAME_ERROR.equals(type) || FRAME_CANCELLED.equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 挂流前缀（不可变）：快照帧 + 可选合成终局 + 已归约事件 id + 续算用量所需的状态
     */
    static final class AttachPrefix {

        private final List<String> snapshotFrames;
        private final String terminalFrame;
        private final Set<String> eventIds;
        private final String finalText;
        private final TokenUsage baseline;
        private final TokenUsage latest;

        AttachPrefix(List<String> snapshotFrames, String terminalFrame, Set<String> eventIds,
                     String finalText, TokenUsage baseline, TokenUsage latest) {
            this.snapshotFrames = snapshotFrames;
            this.terminalFrame = terminalFrame;
            this.eventIds = eventIds;
            this.finalText = finalText;
            this.baseline = baseline;
            this.latest = latest;
        }

        List<String> getSnapshotFrames() {
            return snapshotFrames;
        }

        String getTerminalFrame() {
            return terminalFrame;
        }

        Set<String> getEventIds() {
            return eventIds;
        }

        String getFinalText() {
            return finalText;
        }

        TokenUsage getBaseline() {
            return baseline;
        }

        TokenUsage getLatest() {
            return latest;
        }
    }

    private static String contentText(JsonNode contentArray) {
        if (contentArray == null || !contentArray.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Iterator<JsonNode> it = contentArray.elements(); it.hasNext(); ) {
            JsonNode part = it.next();
            if (part.path("type").asText("").equals("text")) {
                builder.append(part.path("text").asText(""));
            }
        }
        return builder.toString();
    }

    /**
     * 用量（OpenHands 的 stats 是**会话累计**，故单轮用量只能取差值——实测 2026-09-23）
     */
    static final class TokenUsage {

        /**
         * 零基线：会话在首轮之前没有任何 stats，即"尚未消耗"（累计计数从 0 起算）
         */
        static final TokenUsage ZERO = new TokenUsage(null, 0, 0);

        private final String model;
        private final long promptTokens;
        private final long completionTokens;

        private TokenUsage(String model, long promptTokens, long completionTokens) {
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        static TokenUsage from(JsonNode statsValue) {
            JsonNode metrics = statsValue == null ? null : statsValue.path("usage_to_metrics").path("default");
            if (metrics == null || metrics.isMissingNode()) {
                return new TokenUsage(null, 0, 0);
            }
            JsonNode tokens = metrics.path("accumulated_token_usage");
            return new TokenUsage(metrics.path("model_name").asText(null),
                    tokens.path("prompt_tokens").asLong(0),
                    tokens.path("completion_tokens").asLong(0));
        }

        /**
         * 与基线之差（基线缺失或差值为负即视为不可用，返回 null 而不是编造数字）
         */
        TokenUsage delta(TokenUsage baseline) {
            if (baseline == null) {
                return null;
            }
            long prompt = promptTokens - baseline.promptTokens;
            long completion = completionTokens - baseline.completionTokens;
            if (prompt < 0 || completion < 0) {
                return null;
            }
            return new TokenUsage(model, prompt, completion);
        }

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("model", model);
            node.put("prompt_tokens", promptTokens);
            node.put("completion_tokens", completionTokens);
            return node;
        }
    }
}
