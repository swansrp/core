package com.bidr.llm.agent.external;

import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.event.RuntimeEvents;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.AgentSessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: ExternalRuntimeTaskService
 * Description: <b>形态 B 接线</b>：把外部 agent runtime（OpenHands / agent-system…）当作
 * <b>本进程自主循环可派发的异步任务</b>，而不是让它直接面对浏览器。
 *
 * <p>🔴 一个任务 = 一个 core/llm 会话。提交时在上游开轮的同时，在本地
 * {@link AgentSessionStore} 里建一条会话（{@code agentKey=ext:<agentCode>}、
 * {@code subject=<父会话>}），后台泵线程把上游的<b>规范事件</b>逐条翻译成
 * {@link AgentEvent} 落进这条会话的事件流。于是：</p>
 * <ul>
 * <li>进度查询就是既有的 {@code store.events(taskId, sinceSeq)}——<b>不新建任务表</b>；</li>
 * <li>前端用同一棵统一过程树渲染外部 runtime 的执行过程（T2 的适配器天然吃这些事件）；</li>
 * <li>编排主体始终在本进程：意图分诊、归属、产物核验与业务化总结仍由 Java 侧的 LLM 循环负责，
 *     外部 runtime 只出"手"不出"脑"。</li>
 * </ul>
 *
 * <p>翻译近乎 1:1：core/llm 的工具事件 payload 键位已与规范事件对齐
 * （{@code tool_call_id/tool_name/arguments}｜{@code output}），故本类只做搬运不做重塑。</p>
 *
 * <p>🔴 不得阻塞：外部任务是分钟级的，工具面只暴露"提交/查进度/取结果/取消"，
 * 绝不在 {@code @Tool} 里同步等完结（会霸占轮次并撑爆上下文）。</p>
 *
 * @author sharp
 * @since 2026/9/24
 */
@Slf4j
public class ExternalRuntimeTaskService {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 同时在跑的外部任务上限（防派发失控；超限直接拒绝并让模型改期） */
    static final int MAX_RUNNING_TASKS = 8;

    /** live 快照落盘节流（毫秒）：高频 delta 不逐条写存储 */
    private static final long LIVE_FLUSH_MILLIS = 1000L;

    private final AgentSessionStore store;
    /** provider 延迟取（装配与否取决于消费项目是否引了 agent-runtime），不绑 Spring 容器类型 */
    private final java.util.function.Supplier<AgentRuntimeProvider> providerSupplier;

    /** 任务句柄 → 上游三元组与在途链（终局即清除；会话状态本身由 store 的 TTL 兜底回收） */
    private final Map<String, TaskRef> refs = new ConcurrentHashMap<>();
    private final AtomicInteger running = new AtomicInteger();
    private final ExecutorService pumps;

    public ExternalRuntimeTaskService(AgentSessionStore store,
                                      java.util.function.Supplier<AgentRuntimeProvider> providerSupplier) {
        this.store = store;
        this.providerSupplier = providerSupplier;
        this.pumps = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "ext-runtime-pump");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 是否已接入外部 runtime（未接入时工具面应明确回绝，而不是静默假成功） */
    public boolean available() {
        return providerSupplier.get() != null;
    }

    /**
     * 派发一个外部任务：上游建会话 + 开轮 → 本地建任务会话 → 后台泵。
     *
     * @param parentSessionId 发起的父会话（写入 subject，供归属与回链；可空）
     * @param agentCode       上游 agent 编码（空则用 provider 默认）
     * @param requirement     需求正文（含必要上下文；派单要打包，拆细纯亏）
     * @return 任务句柄（= 本地任务会话 id）
     */
    public String submit(String parentSessionId, String agentCode, String requirement) {
        AgentRuntimeProvider provider = providerSupplier.get();
        if (provider == null) {
            throw new IllegalStateException("未接入外部 agent runtime（未装配 AgentRuntimeProvider）");
        }
        if (requirement == null || requirement.trim().isEmpty()) {
            throw new IllegalArgumentException("任务需求不能为空");
        }
        if (running.get() >= MAX_RUNNING_TASKS) {
            throw new IllegalStateException("并发外部任务已达上限 " + MAX_RUNNING_TASKS + "，稍后再派");
        }

        String code = (agentCode == null || agentCode.trim().isEmpty()) ? "default" : agentCode.trim();
        SessionInfo session = provider.createSession(new SessionCreateCmd(
                code, "ext-task", null, new HashMap<>(), null, null));
        TurnOpenCmd cmd = new TurnOpenCmd();
        cmd.setSessionId(session.getSessionId());
        cmd.setAgentCode(code);
        cmd.setContent(requirement.trim());
        cmd.setIdempotencyKey(UUID.randomUUID().toString());

        String taskId = UUID.randomUUID().toString().replace("-", "");
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(taskId);
        state.setAgentKey("ext:" + code);
        state.setSkillCode(code);
        state.setDisplayName("外部任务 · " + code);
        state.setOperator(operatorOf(parentSessionId));
        state.setSubject(parentSessionId);
        state.setStatus(AgentSessionState.RUNNING);
        state.setStartedAt(System.currentTimeMillis());
        state.setHeartbeat(System.currentTimeMillis());
        state.setDetachPolicy("KEEP_RUNNING");
        store.saveState(state);
        store.appendEvent(taskId, AgentEvent.RUN_START, "外部任务已派发（上游 agent=" + code + "）");

        RuntimeTurnLink link = provider.openTurn(cmd);
        refs.put(taskId, new TaskRef(provider, session.getSessionId(), code));
        running.incrementAndGet();
        pumps.submit(() -> pump(taskId, link));
        return taskId;
    }

    /**
     * 查进度：状态 + 自 {@code sinceSeq} 起的新事件摘要（紧凑，避免把全文灌回模型上下文）。
     *
     * @param cursorIn 上次读到的 seq（首次传 0）
     */
    public Map<String, Object> progress(String taskId, long cursorIn, int maxEvents) {
        AgentSessionState state = requireTask(taskId);
        List<AgentEvent> events = store.events(taskId, cursorIn);
        List<Map<String, Object>> brief = new ArrayList<>();
        long cursor = cursorIn;
        int limit = maxEvents <= 0 ? 20 : Math.min(maxEvents, 50);
        for (AgentEvent ev : events) {
            cursor = ev.getSeq();
            if (brief.size() >= limit) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", ev.getSeq());
            item.put("type", ev.getType());
            item.put("text", summarize(ev.getPayload()));
            brief.add(item);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", taskId);
        res.put("status", state.getStatus());
        res.put("cursor", cursor);
        res.put("newEvents", brief);
        res.put("more", events.size() > brief.size());
        res.put("live", state.getLive());
        res.put("summary", state.getSummary());
        res.put("error", state.getError());
        return res;
    }

    /** 取结果：未终局返回 null（调用方据此回报"仍在跑"），终局返回结论/错误/取消说明 */
    public Map<String, Object> result(String taskId) {
        AgentSessionState state = requireTask(taskId);
        if (!state.isTerminal()) {
            return null;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", taskId);
        res.put("status", state.getStatus());
        res.put("summary", state.getSummary());
        res.put("error", state.getError());
        return res;
    }

    /** 取消任务（幂等：已终局直接返回状态；上游取消失败也不抛，交由泵收口） */
    public Map<String, Object> cancel(String taskId) {
        AgentSessionState state = requireTask(taskId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", taskId);
        if (state.isTerminal()) {
            res.put("status", state.getStatus());
            res.put("note", "任务已终局，无需取消");
            return res;
        }
        TaskRef ref = refs.get(taskId);
        if (ref != null) {
            try {
                String turnId = ref.turnId == null ? "" : ref.turnId;
                if (!turnId.isEmpty()) {
                    ref.provider.cancel(ref.sessionId, turnId);
                }
            } catch (Exception e) {
                log.warn("外部任务取消请求失败（{}）：{}", taskId, e.getMessage());
            }
            try {
                ref.link.close();
            } catch (Exception ignore) {
                // 只停消费，不影响收口
            }
        }
        res.put("status", AgentSessionState.RUNNING);
        res.put("note", "取消请求已发出，终局以事件流为准");
        return res;
    }

    // ==================== 泵：规范事件 → AgentEvent ====================

    private void pump(String taskId, RuntimeTurnLink link) {
        TaskRef ref = refs.get(taskId);
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        long lastFlush = 0L;
        boolean settled = false;
        try {
            if (ref != null) {
                ref.link = link;
            }
            String frameJson;
            while ((frameJson = link.nextFrame()) != null) {
                JsonNode frame = parse(frameJson);
                if (frame == null) {
                    continue;
                }
                String type = frame.path("type").asText("");
                JsonNode data = frame.path("data");
                if (ref != null && ref.turnId == null) {
                    String mid = frame.path("message_id").asText(null);
                    if (mid != null && !mid.isEmpty() && !RuntimeEvents.ACCEPTED.equals(type)) {
                        ref.turnId = mid;
                    }
                }
                switch (type) {
                    case RuntimeEvents.ACCEPTED:
                    case RuntimeEvents.STARTED:
                        store.appendEvent(taskId, AgentEvent.LOG, "上游已受理，开始执行");
                        break;
                    case RuntimeEvents.THINKING_DELTA:
                        thinking.append(data.path("delta").asText(""));
                        lastFlush = flushLive(taskId, text, thinking, lastFlush);
                        break;
                    case RuntimeEvents.TEXT_DELTA:
                        text.append(data.path("delta").asText(""));
                        lastFlush = flushLive(taskId, text, thinking, lastFlush);
                        break;
                    case RuntimeEvents.TOOL_CALL:
                        store.appendEvent(taskId, AgentEvent.TOOL_CALL, toolCallPayload(data));
                        break;
                    case RuntimeEvents.TOOL_RESULT:
                        store.appendEvent(taskId, AgentEvent.TOOL_RESULT, toolResultPayload(data));
                        break;
                    case RuntimeEvents.SNAPSHOT:
                        // 快照=重置累积态后逐帧归约（与前端同语义），避免挂流重复
                        text.setLength(0);
                        thinking.setLength(0);
                        for (JsonNode replay : data.path("frames")) {
                            replayOne(taskId, replay, text, thinking);
                        }
                        break;
                    case RuntimeEvents.DONE:
                        String finalText = data.path("text").asText("");
                        settle(taskId, AgentSessionState.FINISHED, finalText.isEmpty() ? text.toString() : finalText, null);
                        settled = true;
                        break;
                    case RuntimeEvents.ERROR:
                        settle(taskId, AgentSessionState.FAILED, text.toString(),
                                data.path("message").asText(data.path("code").asText("外部任务失败")));
                        settled = true;
                        break;
                    case RuntimeEvents.CANCELLED:
                        settle(taskId, AgentSessionState.STOPPED, text.toString(), null);
                        settled = true;
                        break;
                    default:
                        // 未知事件忽略（协议只增不改）
                        break;
                }
                if (settled) {
                    break;
                }
            }
            if (!settled) {
                // 上游收流但没给终局：按断流处理，标失败让父循环能感知，不悬挂
                settle(taskId, AgentSessionState.FAILED, text.toString(), "上游流已结束但未见终局事件（按断流处理）");
            }
        } catch (Exception e) {
            log.warn("外部任务泵异常（{}）：{}", taskId, e.getMessage());
            if (!settled) {
                settle(taskId, AgentSessionState.FAILED, text.toString(), "外部任务链路异常：" + e.getMessage());
            }
        } finally {
            try {
                link.close();
            } catch (Exception ignore) {
                // 收流即释放
            }
            running.decrementAndGet();
            refs.remove(taskId);
        }
    }

    /** 快照内单帧归约（与直播同一套翻译，保证两路一致） */
    private void replayOne(String taskId, JsonNode frame, StringBuilder text, StringBuilder thinking) {
        String type = frame.path("type").asText("");
        JsonNode data = frame.path("data");
        switch (type) {
            case RuntimeEvents.THINKING_DELTA:
                thinking.append(data.path("delta").asText(""));
                break;
            case RuntimeEvents.TEXT_DELTA:
                text.append(data.path("delta").asText(""));
                break;
            case RuntimeEvents.TOOL_CALL:
                store.appendEvent(taskId, AgentEvent.TOOL_CALL, toolCallPayload(data));
                break;
            case RuntimeEvents.TOOL_RESULT:
                store.appendEvent(taskId, AgentEvent.TOOL_RESULT, toolResultPayload(data));
                break;
            default:
                break;
        }
    }

    private long flushLive(String taskId, StringBuilder text, StringBuilder thinking, long lastFlush) {
        long now = System.currentTimeMillis();
        if (now - lastFlush < LIVE_FLUSH_MILLIS) {
            return lastFlush;
        }
        AgentSessionState state = store.getState(taskId);
        if (state != null && !state.isTerminal()) {
            state.setLive(liveLine(thinking, text));
            state.setHeartbeat(now);
            store.saveState(state);
        }
        return now;
    }

    private String liveLine(StringBuilder thinking, StringBuilder text) {
        StringBuilder line = new StringBuilder();
        if (thinking.length() > 0) {
            line.append("思考中 · 已思 ").append(thinking.length()).append(" 字");
        }
        if (text.length() > 0) {
            if (line.length() > 0) {
                line.append('\n');
            }
            line.append(text);
        }
        return line.toString();
    }

    private void settle(String taskId, String status, String summary, String error) {
        AgentSessionState state = store.getState(taskId);
        if (state == null || state.isTerminal()) {
            return;
        }
        state.setStatus(status);
        state.setEndedAt(System.currentTimeMillis());
        state.setLive(null);
        if (error == null) {
            state.setSummary(summary);
        } else {
            state.setError(error);
            if (summary != null && !summary.isEmpty()) {
                state.setSummary(summary);
            }
        }
        store.saveState(state);
        if (AgentSessionState.FAILED.equals(status)) {
            store.appendEvent(taskId, AgentEvent.ERROR, error == null ? "外部任务失败" : error);
        } else if (AgentSessionState.STOPPED.equals(status)) {
            store.appendEvent(taskId, AgentEvent.STOPPED, "外部任务已取消");
        } else {
            store.appendEvent(taskId, AgentEvent.LLM_OUTPUT, summary == null ? "" : summary);
            store.appendEvent(taskId, AgentEvent.FINISH, "外部任务完成");
        }
    }

    // ==================== 辅助 ====================

    private AgentSessionState requireTask(String taskId) {
        AgentSessionState state = store.getState(taskId);
        if (state == null || state.getAgentKey() == null || !state.getAgentKey().startsWith("ext:")) {
            throw new IllegalArgumentException("外部任务不存在或已过期：" + taskId);
        }
        return state;
    }

    private String operatorOf(String parentSessionId) {
        if (parentSessionId == null || parentSessionId.isEmpty()) {
            return null;
        }
        AgentSessionState parent = store.getState(parentSessionId);
        return parent == null ? null : parent.getOperator();
    }

    private static JsonNode parse(String json) {
        try {
            return OM.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** 与 core/llm 工具事件同键位（T2a 已对齐规范事件），前端两路适配器共用一套视图模型 */
    private static Map<String, Object> toolCallPayload(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_call_id", data.path("tool_call_id").asText(""));
        payload.put("tool_name", data.path("tool_name").asText("tool"));
        JsonNode arguments = data.path("arguments");
        payload.put("arguments", arguments.isMissingNode() || arguments.isNull() ? null : arguments.toString());
        return payload;
    }

    private static Map<String, Object> toolResultPayload(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_call_id", data.path("tool_call_id").asText(""));
        payload.put("tool_name", data.path("tool_name").asText("tool"));
        payload.put("output", data.path("output").asText(""));
        return payload;
    }

    private static String summarize(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) payload;
            Object nameObj = map.get("tool_name");
            String name = nameObj == null ? "" : String.valueOf(nameObj);
            Object out = map.get("output");
            String text = out == null ? name : name + " → " + out;
            return cut(text, 200);
        }
        return cut(String.valueOf(payload), 200);
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** 上游三元组 + 在途链（turnId 由受理后的首帧补齐） */
    private static final class TaskRef {
        private final AgentRuntimeProvider provider;
        private final String sessionId;
        private final String agentCode;
        private volatile String turnId;
        private volatile RuntimeTurnLink link;

        private TaskRef(AgentRuntimeProvider provider, String sessionId, String agentCode) {
            this.provider = provider;
            this.sessionId = sessionId;
            this.agentCode = agentCode;
        }
    }

    /** 供工具面回查上游轮次（诊断用） */
    public TurnItem upstreamTurn(String taskId) {
        TaskRef ref = refs.get(taskId);
        if (ref == null || ref.turnId == null) {
            return null;
        }
        return ref.provider.turn(ref.sessionId, ref.turnId);
    }
}
