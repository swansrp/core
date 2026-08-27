package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.agent.conversation.AgentConversationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Title: AgentSessionService
 * Description: 自主 agent 会话生命周期——注册表（AutonomousAgentDefinition 封闭集）+ start/pause/resume/stop/status/events：
 * <ul>
 *     <li>start：独立 run 线程执行业务定义（会话线程即停止中断的属主句柄），daemon 心跳任务
 *         每 20s 刷新（RUNNING/PAUSED 期间）；</li>
 *     <li>stop：停止键（跨实例轮询生效）+ 本实例线程 interrupt（加速收口）双通道；</li>
 *     <li>status：查询侧失联判定——非终态且心跳超 {@link #ORPHAN_HEARTBEAT_MILLIS} 改写 STOPPED
 *         （执行实例宕机后的兜底收口）；同时刷新前端活跃时间（轮询即存活信号）；</li>
 *     <li>断开即停（省 token）：STOP_ON_DETACH 策略的会话，前端活跃信号消失超
 *         {@link #DETACH_STOP_MILLIS} 由心跳任务自动停止；KEEP_RUNNING 后台继续可重连；</li>
 *     <li>pause/resume：改 store 控制键并同步状态与事件（前端状态条即时可见）；
 *         run 线程在下一个检查点（工具循环轮头）感知阻塞/继续。</li>
 * </ul>
 * 会话并发量小（管理端操作），run 线程与心跳任务均轻量自管
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
@Service
public class AgentSessionService {

    /** 心跳周期（秒） */
    private static final long HEARTBEAT_SECONDS = 20;

    /** 失联判定阈值（毫秒）：非终态会话心跳超时即视为执行实例宕机 */
    private static final long ORPHAN_HEARTBEAT_MILLIS = 180_000;

    /** 前端断开判定阈值（毫秒）：活跃信号（status 轮询）消失超此时长，STOP_ON_DETACH 会话自动停止；
     *  需大于轮询周期与重连耗时留足余量，避免短暂网络抖动误停 */
    private static final long DETACH_STOP_MILLIS = 60_000;

    private final AgentSessionStore store;
    private final Map<String, AutonomousAgentDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Thread> runThreads = new ConcurrentHashMap<>();

    /** 自动停止原因（断开即停等）：心跳任务写入，run 线程收口时消费替换默认中断文案 */
    private final Map<String, String> autoStopReasons = new ConcurrentHashMap<>();

    /** 通用历史对话存储（会话收口自动落单轮对话；可为空=不落盘） */
    private final ObjectProvider<AgentConversationService> conversationProvider;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-session-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public AgentSessionService(AgentSessionStore store, List<AutonomousAgentDefinition> definitionList,
                               ObjectProvider<AgentConversationService> conversationProvider) {
        this.store = store;
        this.conversationProvider = conversationProvider;
        for (AutonomousAgentDefinition definition : definitionList) {
            AutonomousAgentDefinition existed = definitions.put(definition.agentKey(), definition);
            if (existed != null) {
                throw new IllegalStateException("agentKey 重复注册: " + definition.agentKey()
                        + "（" + existed.getClass().getName() + " 与 " + definition.getClass().getName() + "）");
            }
        }
    }

    /** 已注册 agent 清单（注册表端点数据源；保持注册序） */
    public List<AutonomousAgentDefinition> registeredAgents() {
        return new ArrayList<>(definitions.values());
    }

    /**
     * 发起会话：立即返回状态快照（sessionId 供控制与轮询端点），业务执行体在 run 线程内进行。
     * 同一 agent 重复发起不拦截（由业务自定义互斥，如资产生成经 Redis 锁）
     */
    public AgentSessionState start(String agentKey, Map<String, Object> payload, String operator) {
        return start(agentKey, payload, operator, null);
    }

    /**
     * 发起会话（带断开策略覆盖）：同一 agent 在不同页面场景需求不同（如测试页需重连、
     * 用户对话断开即停），故策略由发起方定义、定义层只声明默认：
     * requested 非空用之，否则回落 {@code definition.detachPolicy()}，解析结果落快照供心跳执行
     */
    public AgentSessionState start(String agentKey, Map<String, Object> payload, String operator,
                                   DetachPolicy requested) {
        AutonomousAgentDefinition definition = definitions.get(agentKey);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的 agent: " + agentKey);
        }
        Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        state.setAgentKey(agentKey);
        state.setSkillCode(definition.skillCode());
        state.setDisplayName(definition.displayName());
        state.setOperator(operator == null || operator.trim().isEmpty() ? "anonymous" : operator.trim());
        state.setStatus(AgentSessionState.RUNNING);
        state.setStartedAt(System.currentTimeMillis());
        state.setHeartbeat(System.currentTimeMillis());
        state.setDetachPolicy((requested != null ? requested : definition.detachPolicy()).name());
        store.touchView(state.getSessionId());
        try {
            state.setSubject(definition.sessionSubject(safePayload));
        } catch (Exception e) {
            log.warn("会话作用对象解析失败（不阻断启动）, agent={}, error={}", agentKey, e.getMessage());
        }
        store.saveState(state);

        Thread thread = new Thread(() -> runSession(definition, state, safePayload),
                "agent-session-" + state.getSessionId());
        runThreads.put(state.getSessionId(), thread);
        thread.start();
        return state;
    }

    /** 暂停（幂等）：run 线程在下一检查点阻塞；状态与事件即时更新供前端展示 */
    public void pause(String sessionId, String note) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new IllegalStateException("会话已结束（" + state.getStatus() + "），无法暂停");
        }
        store.pause(sessionId, note);
        store.appendEvent(sessionId, AgentEvent.PAUSED,
                note == null || note.trim().isEmpty() ? "用户暂停了会话" : note);
        transition(sessionId, AgentSessionState.PAUSED, null);
    }

    /** 恢复（幂等）：清除暂停键；guidance 空时注入默认指导语（让 LLM 知晓恢复事实而非凭空续跑），
     * 非空时作为用户补充指导注入下一轮上下文 */
    public void resume(String sessionId, String guidance) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new IllegalStateException("会话已结束（" + state.getStatus() + "），无法恢复");
        }
        if (guidance == null || guidance.trim().isEmpty()) {
            guidance = "用户未补充新指导，按暂停前的既有计划与进度继续";
        }
        store.resume(sessionId, guidance);
        store.appendEvent(sessionId, AgentEvent.RESUMED,
                guidance == null || guidance.trim().isEmpty() ? "会话已恢复" : "会话已恢复（附带用户补充指导）");
        transition(sessionId, AgentSessionState.RUNNING, null);
    }

    /** 作答 LLM 提问：校验问题存在且 waiting，作答写入控制键（工具线程阻塞醒查中消费）；
     *  问题状态由 run 线程消费后落快照（单写者不变），请求线程不碰 questions */
    public void answer(String sessionId, int questionId, String answer, boolean skipped) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new IllegalStateException("会话已结束（" + state.getStatus() + "），无法作答");
        }
        AgentQuestion question = null;
        if (state.getQuestions() != null) {
            for (AgentQuestion q : state.getQuestions()) {
                if (q.getId() == questionId) {
                    question = q;
                    break;
                }
            }
        }
        if (question == null) {
            throw new IllegalArgumentException("问题不存在: #" + questionId);
        }
        if (!AgentQuestion.WAITING.equals(question.getStatus())) {
            throw new IllegalStateException("问题 #" + questionId + " 已处理（" + question.getStatus() + "），无需作答");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", questionId);
        payload.put("answer", answer == null ? "" : answer.trim());
        payload.put("skipped", skipped);
        try {
            store.submitAnswer(sessionId, new ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("作答序列化失败: " + e.getMessage(), e);
        }
    }

    /** 收口待确认口径（终态后亦可操作：确认页裁决发生在会话结束后，run 线程已收口无并发写）：
     *  confirmed=用户认可自决口径，revised=改口径（note 必携新口径说明，业务侧据此重算产出）；
     *  重复收口幂等覆盖（用户可反悔改判），条目不存在报错 */
    public AgentConfirmation resolveConfirmation(String sessionId, int confirmationId, boolean revised, String note) {
        AgentSessionState state = requireState(sessionId);
        AgentConfirmation item = null;
        if (state.getConfirmations() != null) {
            for (AgentConfirmation c : state.getConfirmations()) {
                if (c.getId() == confirmationId) {
                    item = c;
                    break;
                }
            }
        }
        if (item == null) {
            throw new IllegalArgumentException("待确认条目不存在: #" + confirmationId);
        }
        String trimmed = note == null ? "" : note.trim();
        if (revised && trimmed.isEmpty()) {
            throw new IllegalArgumentException("改口径须给出新口径说明");
        }
        item.setStatus(revised ? AgentConfirmation.REVISED : AgentConfirmation.CONFIRMED);
        item.setResolvedAt(System.currentTimeMillis());
        item.setResolveNote(trimmed.isEmpty() ? null : trimmed);
        store.saveState(state);
        store.appendEvent(sessionId, AgentEvent.CONFIRMATION,
                "待确认口径 #" + confirmationId + (revised ? " 已改口径：" + trimmed : " 已经用户确认"));
        return item;
    }

    /** 停止（幂等）：停止键跨实例轮询生效 + 本实例线程中断加速收口 */
    public void stop(String sessionId) {
        AgentSessionState state = requireState(sessionId);
        store.requestStop(sessionId);
        Thread thread = runThreads.get(sessionId);
        if (thread != null) {
            thread.interrupt();
        }
        // 终态由 run 线程收口写入（含已完成情况说明）；此处仅提前清理，避免 stop 后短暂显示 RUNNING
        if (state.isTerminal()) {
            store.clearControls(sessionId);
        }
    }

    /** 会话状态（查询侧失联判定：非终态且心跳超时改写 STOPPED；轮询即前端存活信号，刷新活跃时间） */
    public AgentSessionState status(String sessionId) {
        AgentSessionState state = requireState(sessionId);
        if (!state.isTerminal()) {
            // 存活信号走独立轻键（不读改写整条快照，避免与 run 线程落快照丢更新互踩）
            store.touchView(sessionId);
        }
        if (!state.isTerminal() && System.currentTimeMillis() - state.getHeartbeat() > ORPHAN_HEARTBEAT_MILLIS) {
            log.warn("agent 会话心跳超时判定失联, sessionId={}, agentKey={}", sessionId, state.getAgentKey());
            state.setStatus(AgentSessionState.STOPPED);
            state.setEndedAt(System.currentTimeMillis());
            state.setError("执行实例失联，可重新发起继续");
            // 失联同属终态：阶段/计划 running 段一并收口，避免查询侧看到永久转圈
            finalizeStages(state, AgentSessionState.STOPPED, state.getError());
            finalizePlan(state, AgentSessionState.STOPPED);
            store.saveState(state);
            store.appendEvent(sessionId, AgentEvent.STOPPED, state.getError());
            store.clearControls(sessionId);
        }
        return state;
    }

    /** 事件流增量读取（seq 大于 sinceSeq） */
    public List<AgentEvent> events(String sessionId, long sinceSeq) {
        requireState(sessionId);
        return store.events(sessionId, sinceSeq);
    }

    /**
     * 活跃会话列表（非终态，新→旧）：刷新/重连场景的数据源——本人发起、
     * agentKey 可选过滤；业务维度（如资产生成的业务 agentCode）经快照 subject 前端自行筛。
     * 枚举全量会话键再过滤（会话量小，管理端操作）
     */
    public List<AgentSessionState> activeSessions(String operator, String agentKey) {
        String op = operator == null || operator.trim().isEmpty() ? "anonymous" : operator.trim();
        List<AgentSessionState> result = new ArrayList<>();
        for (String sessionId : store.listSessionIds()) {
            AgentSessionState state = store.getState(sessionId);
            if (state == null || state.isTerminal()) {
                continue;
            }
            if (!op.equals(state.getOperator())) {
                continue;
            }
            if (StringUtils.hasText(agentKey) && !agentKey.equals(state.getAgentKey())) {
                continue;
            }
            result.add(state);
        }
        result.sort(Comparator.comparingLong(AgentSessionState::getStartedAt).reversed());
        return result;
    }

    // ---- run 线程体 ----

    private void runSession(AutonomousAgentDefinition definition, AgentSessionState state,
                            Map<String, Object> payload) {
        String sessionId = state.getSessionId();
        AgentSessionContext ctx = new AgentSessionContext(state, store, payload);
        Map<String, Object> startPayload = new HashMap<>();
        startPayload.put("agentKey", state.getAgentKey());
        startPayload.put("payload", payload);
        store.appendEvent(sessionId, AgentEvent.RUN_START, startPayload);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> refreshHeartbeat(state, definition), HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        String finalStatus;
        String error = null;
        try {
            definition.start(ctx, payload);
            finalStatus = AgentSessionState.FINISHED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finalStatus = AgentSessionState.STOPPED;
            error = "会话被中断停止";
        } catch (Exception e) {
            if (ToolAgentRunner.isInterrupted(e)) {
                // 停止中断经 Redis/HTTP 等包装外溢（如 Redisson 同步等待抛 InterruptedException
                // 被 Spring 转成 DataAccessException）：与裸 InterruptedException 同口径收口 STOPPED
                finalStatus = AgentSessionState.STOPPED;
                error = "会话被中断停止";
            } else {
                log.warn("agent 会话执行异常, sessionId={}, agentKey={}", sessionId, state.getAgentKey(), e);
                finalStatus = AgentSessionState.FAILED;
                error = brief(e);
            }
        } finally {
            heartbeat.cancel(false);
            runThreads.remove(sessionId);
            // 清理须干净线程：停止中断留下的标志位会让 Redisson 同步等待立即抛 InterruptedException，
            // finally 外溢将导致终态状态/事件永不写入（前端只能等 180s 心跳超时兜底）；
            // 先清标志位（中断事实已由 catch 归类），清理本身再兜底防外溢
            Thread.interrupted();
            try {
                store.clearControls(sessionId);
            } catch (Exception e) {
                log.warn("agent 会话控制键清理失败（TTL 到期自动失效）, sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
        state.setStatus(finalStatus);
        state.setEndedAt(System.currentTimeMillis());
        // 自动停止原因（断开即停等）替换默认中断文案，前端与留痕都能看到真实原因；
        // FINISHED 竞态（停请下达但任务先跑完）不覆盖，照常完成
        String autoReason = autoStopReasons.remove(state.getSessionId());
        if (autoReason != null && !AgentSessionState.FINISHED.equals(finalStatus)) {
            error = autoReason;
        }
        state.setError(error);
        // 终态清空替换式 live（最后一帧流式文本不残留；思考全文留痕由业务侧轮末归档事件承载）
        state.setLive(null);
        state.setSummary(AgentSessionState.FINISHED.equals(finalStatus) ? ctx.takeSummary() : null);
        finalizeStages(state, finalStatus, error);
        finalizePlan(state, finalStatus);
        store.saveState(state);
        if (AgentSessionState.FINISHED.equals(finalStatus)) {
            store.appendEvent(sessionId, AgentEvent.FINISH,
                    state.getSummary() == null ? "会话完成" : state.getSummary());
        } else if (AgentSessionState.FAILED.equals(finalStatus)) {
            store.appendEvent(sessionId, AgentEvent.ERROR, error);
        } else {
            store.appendEvent(sessionId, AgentEvent.STOPPED,
                    "会话已停止" + (error == null ? "" : "：" + error));
            // 停止事实传达：补一条 GUIDANCE 事件，后续重开/回看时 LLM 与用户都能获知上一轮系用户主动停止，
            // 避免新一轮会话误以为任务仍在延续（协议无停止命令，中断即停，此为事实层补齐）
            store.appendEvent(sessionId, AgentEvent.GUIDANCE,
                    "上一轮会话已被用户主动停止；若重新发起，不要假定上一轮任务仍在进行，按新的完整指令执行");
        }
        try {
            definition.onFinish(ctx, finalStatus, error);
        } catch (Exception e) {
            log.warn("agent 会话收口回调失败, agent={}, error={}", definition.agentKey(), e.getMessage());
        }
        appendSessionConversation(definition, state, payload);
    }

    /**
     * 通用历史对话落盘（自主会话=单轮对话）：定义实现 {@link AutonomousAgentDefinition#conversationQuestion}
     * 返回非空才写；user=问题展示文本，assistant=结论摘要/失败原因/停止说明（含 sessionId 供回放定位），
     * agentCode=agentKey（与统一注册中心同构）。对话是辅助链路，异常只记日志不影响会话状态
     */
    private void appendSessionConversation(AutonomousAgentDefinition definition, AgentSessionState state,
                                           Map<String, Object> payload) {
        AgentConversationService conversationService = conversationProvider.getIfAvailable();
        if (conversationService == null) {
            return;
        }
        String question;
        try {
            question = definition.conversationQuestion(payload);
        } catch (Exception e) {
            log.warn("会话对话问题组装失败, agent={}, error={}", definition.agentKey(), e.getMessage());
            return;
        }
        if (question == null || question.trim().isEmpty()) {
            return;
        }
        try {
            String conversationId = conversationService.appendUser(
                    definition.conversationAgentCode(payload), null, state.getOperator(), question.trim(), null);
            String content;
            String status;
            if (AgentSessionState.FINISHED.equals(state.getStatus())) {
                content = state.getSummary() == null ? "会话已完成" : state.getSummary();
                status = "done";
            } else if (AgentSessionState.FAILED.equals(state.getStatus())) {
                content = state.getError() == null ? "会话执行失败" : state.getError();
                status = "error";
            } else {
                content = "会话已被用户停止";
                status = "stopped";
            }
            Map<String, Object> ext = new HashMap<>();
            ext.put("sessionId", state.getSessionId());
            conversationService.appendAssistant(conversationId, content, status, ext);
        } catch (Exception e) {
            log.warn("agent 会话对话落盘失败, agent={}, error={}", definition.agentKey(), e.getMessage());
        }
    }

    /** 阶段终态收口：running 段按会话终态置位（FINISHED→ok 补齐 / FAILED→error / STOPPED→stopped），
     *  后续 pending 段一律 skipped（未执行到）——阶段条不再永久卡在转圈 */
    private void finalizeStages(AgentSessionState state, String finalStatus, String error) {
        if (state.getStages().isEmpty()) {
            return;
        }
        String runningTo = AgentSessionState.FINISHED.equals(finalStatus) ? AgentStage.OK
                : (AgentSessionState.FAILED.equals(finalStatus) ? AgentStage.ERROR : AgentStage.STOPPED);
        for (AgentStage stage : state.getStages()) {
            if (AgentStage.RUNNING.equals(stage.getStatus())) {
                stage.setStatus(runningTo);
                stage.setDetail(AgentStage.ERROR.equals(runningTo) && error != null ? error : stage.getDetail());
                stage.setEndedAt(System.currentTimeMillis());
            } else if (AgentStage.PENDING.equals(stage.getStatus())) {
                stage.setStatus(AgentStage.SKIPPED);
                stage.setEndedAt(System.currentTimeMillis());
            }
        }
    }

    /** 计划待办终态收口：running 条目按会话终态置位（FINISHED→done 补挑勾 / 其余→stopped），
     *  pending 条目保持原样（如实反映未执行）——清单不再永久卡在转圈（与阶段收口同口径） */
    private void finalizePlan(AgentSessionState state, String finalStatus) {
        if (state.getPlan().isEmpty()) {
            return;
        }
        boolean toDone = AgentSessionState.FINISHED.equals(finalStatus);
        for (AgentPlanItem item : state.getPlan()) {
            if (AgentPlanItem.RUNNING.equals(item.getStatus())) {
                item.setStatus(toDone ? AgentPlanItem.DONE : AgentPlanItem.STOPPED);
                if (toDone && (item.getNote() == null || item.getNote().trim().isEmpty())) {
                    item.setNote("收口自动挑勾");
                }
            }
        }
    }

    private void refreshHeartbeat(AgentSessionState state, AutonomousAgentDefinition definition) {
        try {
            // 同步刷新内存对象心跳：run 线程后续落快照（persistStages）携带新值，
            // 防高频快照把 Redis 心跳回滚为启动时刻致查询侧误判失联
            state.setHeartbeat(System.currentTimeMillis());
            AgentSessionState latest = store.getState(state.getSessionId());
            if (latest == null || latest.isTerminal()) {
                return;
            }
            // 断开即停（省 token）：STOP_ON_DETACH 会话前端存活信号消失超阈值自动停止；
            // KEEP_RUNNING（功能型任务）不处置，后台继续供重连。策略读快照（发起时解析，
            // 发起方可覆盖定义默认）；停止键跨实例生效，属主实例中断加速收口；
            // 原因经 autoStopReasons 传回 run 线程收口文案
            if (policyOf(state, definition) == DetachPolicy.STOP_ON_DETACH) {
                long viewAt = store.viewTime(state.getSessionId());
                if (viewAt > 0 && System.currentTimeMillis() - viewAt > DETACH_STOP_MILLIS) {
                    log.info("agent 会话前端断开超阈值自动停止（省 token）, sessionId={}, agentKey={}",
                            state.getSessionId(), state.getAgentKey());
                    autoStopReasons.put(state.getSessionId(), "前端已断开，会话自动停止（节省资源，可重新发起）");
                    store.requestStop(state.getSessionId());
                    store.appendEvent(state.getSessionId(), AgentEvent.STOPPED, "前端断开超阈值，会话自动停止");
                    Thread thread = runThreads.get(state.getSessionId());
                    if (thread != null) {
                        thread.interrupt();
                    }
                    return;
                }
            }
            latest.setHeartbeat(System.currentTimeMillis());
            store.saveState(latest);
        } catch (Exception e) {
            log.warn("agent 会话心跳刷新失败, sessionId={}, error={}", state.getSessionId(), e.getMessage());
        }
    }

    /** 会话生效的断开策略：快照字段优先（发起时解析落定）；缺失/非法回落定义默认（兼容旧快照） */
    private DetachPolicy policyOf(AgentSessionState state, AutonomousAgentDefinition definition) {
        String name = state.getDetachPolicy();
        if (name != null) {
            try {
                return DetachPolicy.valueOf(name.trim());
            } catch (IllegalArgumentException ignored) {
                // 非法取值按定义默认处置，不因脏数据中断心跳
            }
        }
        return definition.detachPolicy();
    }

    private void transition(String sessionId, String status, String error) {
        AgentSessionState state = requireState(sessionId);
        state.setStatus(status);
        state.setError(error);
        state.setHeartbeat(System.currentTimeMillis());
        store.saveState(state);
    }

    private AgentSessionState requireState(String sessionId) {
        AgentSessionState state = store.getState(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("会话不存在或已过期: " + sessionId);
        }
        return state;
    }

    private static String brief(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }
}
