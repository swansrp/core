package com.bidr.llm.agent.session;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.agent.conversation.AgentConversationService;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;

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

    /** 建后未起跑的兜底超时（毫秒）：startPaused 会话等素材上传，超时仍未 resume 即自动收口
     *  （防页面关闭后留下永不执行的暂停会话；正常上传秒级完成，10 分钟足够大文件慢网） */
    private static final long DEFERRED_START_TIMEOUT_MILLIS = 10 * 60_000L;

    private final AgentSessionStore store;
    private final Map<String, AutonomousAgentDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Thread> runThreads = new ConcurrentHashMap<>();

    /** 自动停止原因（断开即停等）：心跳任务写入，run 线程收口时消费替换默认中断文案 */
    private final Map<String, String> autoStopReasons = new ConcurrentHashMap<>();

    /** 建后未起跑会话（{@code startPaused=true}）：run 线程延迟到 resume 才创建，
     *  期间附件上传事件先落事件流 ⇒ 首轮提示词天然含附件（无"上传晚于首轮"竞态） */
    private final Map<String, DeferredStart> deferredStarts = new ConcurrentHashMap<>();

    /** 建后未起跑会话的心跳守卫：refreshHeartbeat（断开即停＋存活轻键）＋超时收口，resume/stop 时取消 */
    private final Map<String, ScheduledFuture<?>> deferredGuards = new ConcurrentHashMap<>();

    /** 延迟起跑快照（definition/state/payload 在 resume 时补起 run 线程所需） */
    private record DeferredStart(AutonomousAgentDefinition definition, AgentSessionState state,
                                 Map<String, Object> payload) {
    }

    /** 通用历史对话存储（会话收口自动落单轮对话；可为空=不落盘） */
    private final ObjectProvider<AgentConversationService> conversationProvider;

    /** SSE 推送通道（M18 棒2；可为空=未装配，行为与改前一致） */
    private final AgentSessionStreamHub streamHub;

    /** Agent 会话沙箱端口（Cube 清扫对齐棒3；可为空=沙箱能力未启用，终态钩子静默跳过） */
    private final AgentSandbox sandbox;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-session-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public AgentSessionService(AgentSessionStore store, List<AutonomousAgentDefinition> definitionList,
                               ObjectProvider<AgentConversationService> conversationProvider) {
        this(store, definitionList, conversationProvider, null, null);
    }

    @Autowired
    public AgentSessionService(AgentSessionStore store, List<AutonomousAgentDefinition> definitionList,
                               ObjectProvider<AgentConversationService> conversationProvider,
                               ObjectProvider<AgentSessionStreamHub> streamHubProvider,
                               ObjectProvider<AgentSandbox> sandboxProvider) {
        this.store = store;
        this.conversationProvider = conversationProvider;
        this.streamHub = streamHubProvider == null ? null : streamHubProvider.getIfAvailable();
        this.sandbox = sandboxProvider == null ? null : sandboxProvider.getIfAvailable();
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
        return start(agentKey, payload, operator, requested, false);
    }

    /**
     * 发起会话（可「建后未起跑」）：{@code startPaused=true} 时只建会话快照、<b>不启动 run 线程</b>，
     * 返回 {@link AgentSessionState#PAUSED} 快照——发起方先把素材附件上传到该会话
     * （{@code file_attached} 事件先落事件流），再调 {@link #resume} 起跑：
     * 首轮提示词由 run 线程在 resume 时才组装，附件天然进入首轮上下文（业务提示词与
     * 运行期增量两条通道都不需要赌"上传抢在首轮前"）。
     * <p>
     * 等待期由心跳守卫兜底：每 {@link #HEARTBEAT_SECONDS} 秒刷存活轻键（防失联误判）
     * 并按断开策略/超时自动收口；stop 直接终态收口，不会留下永不执行的暂停会话。
     * <p>
     * 🔴 首轮业务参数仍走 {@code payload}（如用户指令），resume 的 guidance 不参与首轮组装
     * （deferred 分支忽略并记 info）——避免指令被当作「用户补充指导」二次注入。
     */
    public AgentSessionState start(String agentKey, Map<String, Object> payload, String operator,
                                   DetachPolicy requested, boolean startPaused) {
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
        state.setStatus(startPaused ? AgentSessionState.PAUSED : AgentSessionState.RUNNING);
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

        if (startPaused) {
            deferredStarts.put(state.getSessionId(), new DeferredStart(definition, state, safePayload));
            deferredGuards.put(state.getSessionId(), heartbeatExecutor.scheduleAtFixedRate(
                    () -> guardDeferredStart(state.getSessionId()),
                    HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS));
            // 「建后未起跑」是素材暂存的内部形态，不是用户暂停：不发 PAUSED 事件
            //（流里出现「已暂停：会话已创建…」对用户是噪音；状态快照仍为 PAUSED，
            //  前端轮询与上传/起跑链照常）；仅广播状态快照供订阅端同步
            if (streamHub != null) {
                streamHub.publishStatus(state);
            }
            return state;
        }
        launch(definition, state, safePayload);
        return state;
    }

    /** 起 run 线程（直起与「建后未起跑」resume 补起共用一处，避免两套线程装配漂移） */
    private void launch(AutonomousAgentDefinition definition, AgentSessionState state,
                        Map<String, Object> payload) {
        Thread thread = new Thread(() -> runSession(definition, state, payload),
                "agent-session-" + state.getSessionId());
        runThreads.put(state.getSessionId(), thread);
        thread.start();
    }

    /** 暂停（幂等）：run 线程在下一检查点阻塞；状态与事件即时更新供前端展示 */
    public void pause(String sessionId, String note) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new ServiceException("会话已结束（" + state.getStatus() + "），无法暂停");
        }
        store.pause(sessionId, note);
        emitAndPublish(sessionId, AgentEvent.PAUSED,
                note == null || note.trim().isEmpty() ? "用户暂停了会话" : note);
        transition(sessionId, AgentSessionState.PAUSED, null);
    }

    /** 恢复（幂等）：清除暂停键；guidance 空时注入默认指导语（让 LLM 知晓恢复事实而非凭空续跑），
     *  非空时作为用户补充指导注入下一轮上下文。 */
    public void resume(String sessionId, String guidance) {
        resume(sessionId, guidance, null);
    }

    /** 恢复（可带首轮 payload 补丁）：清除暂停键；guidance 空时注入默认指导语（让 LLM 知晓恢复事实
     *  而非凭空续跑），非空时作为用户补充指导注入下一轮上下文。
     *  <p>「建后未起跑」会话（startPaused）：本调用＝真正起跑——不落 guidance（首轮指令随 start
     *  payload 组装，guidance 注入会二次叠加），起 run 线程后正常执行。
     *  <p>{@code payloadPatch}（2026-11-17）：仅对「建后未起跑」会话生效——素材会话在用户挑素材时
     *  就已创建（先上传＋预解析），当时指令/底稿还没落定，起跑前用补丁把最终业务参数合并进
     *  start payload（键覆盖、null 值跳过），首轮提示词据此组装；普通暂停恢复忽略该参数（记 info）。
     *  合并后重算 subject，保证会话列表/历史里的业务维度与首轮参数一致。 */
    public void resume(String sessionId, String guidance, Map<String, Object> payloadPatch) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new ServiceException("会话已结束（" + state.getStatus() + "），无法恢复");
        }
        DeferredStart deferred = deferredStarts.remove(sessionId);
        if (deferred != null) {
            cancelDeferredGuard(sessionId);
            if (guidance != null && !guidance.trim().isEmpty()) {
                log.info("建后未起跑会话的首轮指令走 start payload，resume guidance 不入首轮（忽略）: sessionId={}",
                        sessionId);
            }
            Map<String, Object> firstPayload = mergePayload(deferred.payload(), payloadPatch);
            if (payloadPatch != null && !payloadPatch.isEmpty()) {
                try {
                    deferred.state().setSubject(deferred.definition().sessionSubject(firstPayload));
                } catch (Exception e) {
                    log.warn("会话作用对象重算失败（不阻断起跑）, sessionId={}, error={}",
                            sessionId, e.getMessage());
                }
            }
            // run 线程尚未创建：此快照是唯一写者，直接落 RUNNING 后起跑
            deferred.state().setStatus(AgentSessionState.RUNNING);
            deferred.state().setHeartbeat(System.currentTimeMillis());
            store.saveState(deferred.state());
            if (streamHub != null) {
                streamHub.publishStatus(deferred.state());
            }
            emitAndPublish(sessionId, AgentEvent.RESUMED, "素材已就绪，会话开始执行");
            launch(deferred.definition(), deferred.state(), firstPayload);
            return;
        }
        if (payloadPatch != null && !payloadPatch.isEmpty()) {
            log.info("首轮 payload 补丁只对建后未起跑会话生效，普通恢复忽略: sessionId={}", sessionId);
        }
        if (guidance == null || guidance.trim().isEmpty()) {
            guidance = "用户未补充新指导，按暂停前的既有计划与进度继续";
        }
        store.resume(sessionId, guidance);
        emitAndPublish(sessionId, AgentEvent.RESUMED,
                guidance == null || guidance.trim().isEmpty() ? "会话已恢复" : "会话已恢复（附带用户补充指导）");
        transition(sessionId, AgentSessionState.RUNNING, null);
    }

    /**
     * 首轮 payload 合并：补丁键覆盖、null 值跳过（前端未填的键不覆盖已有值）。
     * 补丁为空/无有效键时原样返回 base（不复制，避免无意义对象）。
     */
    static Map<String, Object> mergePayload(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> safeBase = base == null ? Collections.emptyMap() : base;
        if (patch == null || patch.isEmpty()) {
            return safeBase;
        }
        Map<String, Object> merged = new LinkedHashMap<>(safeBase);
        boolean changed = false;
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            merged.put(entry.getKey(), entry.getValue());
            changed = true;
        }
        return changed ? merged : safeBase;
    }

    /** 作答 LLM 提问：校验问题存在且 waiting，作答写入控制键（工具线程阻塞醒查中消费）；
     *  问题状态由 run 线程消费后落快照（单写者不变），请求线程不碰 questions */
    public void answer(String sessionId, int questionId, String answer, boolean skipped) {
        AgentSessionState state = requireState(sessionId);
        if (state.isTerminal()) {
            throw new ServiceException("会话已结束（" + state.getStatus() + "），无法作答");
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
            throw new ServiceException("问题不存在: #" + questionId);
        }
        if (!AgentQuestion.WAITING.equals(question.getStatus())) {
            // B-04（2026-10-22）：迟到作答/跳过＝无效操作不是服务异常——ask_user 5 分钟超时后
            // 问题已 EXPIRED，用户此刻点「跳过」很正常；抛错会让前端全局红条。降为幂等 WARN
            // 直接返回（问题状态不变、不重复提交），前端问题卡按事件置灰兜住入口
            log.warn("问题 #{} 已处理（{}），迟到作答按幂等无效忽略: session={}",
                    questionId, question.getStatus(), sessionId);
            return;
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
            throw new ServiceException("待确认条目不存在: #" + confirmationId);
        }
        String trimmed = note == null ? "" : note.trim();
        if (revised && trimmed.isEmpty()) {
            throw new ServiceException("改口径须给出新口径说明");
        }
        item.setStatus(revised ? AgentConfirmation.REVISED : AgentConfirmation.CONFIRMED);
        item.setResolvedAt(System.currentTimeMillis());
        item.setResolveNote(trimmed.isEmpty() ? null : trimmed);
        store.saveState(state);
        emitAndPublish(sessionId, AgentEvent.CONFIRMATION,
                "待确认口径 #" + confirmationId + (revised ? " 已改口径：" + trimmed : " 已经用户确认"));
        if (streamHub != null) {
            streamHub.publishStatus(state);
        }
        return item;
    }

    /** 停止（幂等）：停止键跨实例轮询生效 + 本实例线程中断加速收口；
     *  「建后未起跑」会话无 run 线程，由本侧直接终态收口（守卫任务一并取消） */
    public void stop(String sessionId) {
        AgentSessionState state = requireState(sessionId);
        DeferredStart deferred = deferredStarts.remove(sessionId);
        if (deferred != null) {
            cancelDeferredGuard(sessionId);
            try {
                store.clearControls(sessionId);
            } catch (Exception e) {
                log.warn("未起跑会话控制键清理失败（TTL 到期自动失效）, sessionId={}, error={}",
                        sessionId, e.getMessage());
            }
            finishDeferredStart(deferred.state(), "用户停止了会话");
            return;
        }
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
        // 失联判定取"快照 heartbeat 与独立存活键"较大者：心跳线程只写轻键，不再回写状态快照
        long aliveAt = Math.max(state.getHeartbeat(), store.runTime(sessionId));
        if (!state.isTerminal() && System.currentTimeMillis() - aliveAt > ORPHAN_HEARTBEAT_MILLIS) {
            log.warn("agent 会话心跳超时判定失联, sessionId={}, agentKey={}", sessionId, state.getAgentKey());
            state.setStatus(AgentSessionState.STOPPED);
            state.setEndedAt(System.currentTimeMillis());
            state.setError("执行实例失联，可重新发起继续");
            // 失联同属终态：阶段/计划 running 段一并收口，避免查询侧看到永久转圈
            finalizeStages(state, AgentSessionState.STOPPED, state.getError());
            finalizePlan(state, AgentSessionState.STOPPED);
            store.saveState(state);
            emitAndPublish(sessionId, AgentEvent.STOPPED, state.getError());
            store.clearControls(sessionId);
            // HTTP 线程改写的终态同样收口 SSE 连接（done 帧后关流，前端回落轮询不会挂死）
            if (streamHub != null) {
                streamHub.publishDone(state);
            }
        }
        return state;
    }

    /** 事件流增量读取（seq 大于 sinceSeq） */
    public List<AgentEvent> events(String sessionId, long sinceSeq) {
        requireState(sessionId);
        return store.events(sessionId, sinceSeq);
    }

    /**
     * 登记会话附件事件（HTTP 上传链路）：附件字节已由 {@code AgentFileStore} 落存储，
     * 本方法只把关「会话存在」并追加 {@link AgentEvent#FILE_ATTACHED}（payload 键口径
     * 与运行线程 {@code AgentSessionContext#attachFile} 共用 {@link AgentEvent#fileAttachedPayload}）。
     * <p>
     * 写者说明：事件流惯例是 run 线程单写者；上传是用户低频动作、且"上传事实必须即时出现在
     * 事件流"是协议要求（前端 chips 与模型上下文都靠它），故此处由请求线程直接追加。
     * 归属校验（操作人＝发起人）在端点侧完成后再调本方法。
     *
     * @return 事件 seq；&lt;=0 表示落库失败（未扩容事件流）
     */
    public long attachFile(String sessionId, FileRef file) {
        return attachFile(sessionId, file, null);
    }

    /**
     * 同上，附带预解析正文长度（{@code parse_chars}）：发起前素材链路在登记时把「已解析」
     * 事实写进事件流，前端文件卡据此点亮（见 {@link AgentEvent#fileAttachedPayload(FileRef, Integer)}）。
     */
    public long attachFile(String sessionId, FileRef file, Integer parseChars) {
        requireState(sessionId);
        if (file == null) {
            throw new ServiceException("附件引用不能为空");
        }
        return emitAndPublish(sessionId, AgentEvent.FILE_ATTACHED, AgentEvent.fileAttachedPayload(file, parseChars));
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
        AgentSessionContext ctx = new AgentSessionContext(state, store, payload, streamHub);
        Map<String, Object> startPayload = new HashMap<>();
        startPayload.put("agentKey", state.getAgentKey());
        startPayload.put("payload", payload);
        emitAndPublish(sessionId, AgentEvent.RUN_START, startPayload);
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
            emitAndPublish(sessionId, AgentEvent.FINISH,
                    state.getSummary() == null ? "会话完成" : state.getSummary());
        } else if (AgentSessionState.FAILED.equals(finalStatus)) {
            emitAndPublish(sessionId, AgentEvent.ERROR, error);
        } else {
            emitAndPublish(sessionId, AgentEvent.STOPPED,
                    "会话已停止" + (error == null ? "" : "：" + error));
            // 停止事实传达：补一条 GUIDANCE 事件，后续重开/回看时 LLM 与用户都能获知上一轮系用户主动停止，
            // 避免新一轮会话误以为任务仍在延续（协议无停止命令，中断即停，此为事实层补齐）
            emitAndPublish(sessionId, AgentEvent.GUIDANCE,
                    "上一轮会话已被用户主动停止；若重新发起，不要假定上一轮任务仍在进行，按新的完整指令执行");
        }
        // 终态收口帧：done 之后关该会话全部 SSE 连接（前端回落轮询/重连）
        if (streamHub != null) {
            streamHub.publishDone(state);
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
            // 会话↔对话定位广播（M14 棒2 增量）：装配单这类永久面据此记住"这场会话有条对话"
            conversationService.linkSession(state.getSessionId(), conversationId);
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
     *  后续 pending 段一律 skipped（未执行到）——阶段条不再永久卡在转圈。
     *  <p>🔴 三路终态汇聚单点挂钩：run 主收口（{@link #runSession}）、查询侧失联（{@link #status}）、
     *  「建后未起跑」收口（{@link #finishDeferredStart}）全部经本方法落终态，沙箱销毁挂此处一处即全覆盖。
     *  钩子置于方法开头（早于 stages 空表早退，无阶段会话同样销毁）；销毁异常全吞不阻断收口
     *  （终态收口路径任何异常都可能污染会话落库），失败交由沙箱实现侧清扫循环兜底回收 */
    private void finalizeStages(AgentSessionState state, String finalStatus, String error) {
        destroySandboxQuietly(state.getSessionId());
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

    /** 沙箱终态销毁（静默）：端口缺席（provider 为 null 或 bean 缺席 getIfAvailable() 为 null）
     *  静默跳过；destroy 抛错只记 WARN 不外溢——本方法经 finalizeStages 在 run 线程/HTTP 线程/
     *  守卫线程三种上下文被调，本类全程无持锁区，destroy 内部 HTTP 调用无锁竞争；
     *  销毁失败不重试，由沙箱实现侧清扫循环（Janitor）兜底回收 */
    private void destroySandboxQuietly(String sessionId) {
        if (sandbox == null) {
            return;
        }
        try {
            sandbox.destroy(sessionId);
        } catch (Exception e) {
            log.warn("agent 会话沙箱销毁失败（清扫循环兜底回收）, sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }

    private void refreshHeartbeat(AgentSessionState state, AutonomousAgentDefinition definition) {
        try {
            // 同步刷新内存对象心跳：run 线程后续落快照（persistStages）携带新值，
            // 使快照 heartbeat 尽量跟上（查询侧失联判定还会并取独立存活键 runTime 兜底）
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
                    // 🔴 心跳线程不得 appendEvent：事件流是"整表读-改-写"，store 的约定是**单写者=run 线程**，
                    // 第二个线程追加会与之互相覆盖。停止事件由 run 线程收口统一补发
                    // （settle 的 STOPPED 分支，文案取上面的 autoStopReasons）。
                    // 注：实测发生过的"64 条→2 条、序号从 1 重启"并非此处引起，而是 store 把
                    // "读失败"当成"空表"回写截断历史——已在 RedisAgentSessionStore.appendEvent 修复。
                    Thread thread = runThreads.get(state.getSessionId());
                    if (thread != null) {
                        thread.interrupt();
                    }
                    return;
                }
            }
            // 🔴 存活信号只写独立轻键：状态键是整份快照的读-改-写，心跳线程若回写快照，
            // 会与 run 线程落快照互相丢更新（阶段/live 被回滚）——与 touchView 同一口径
            store.touchRun(state.getSessionId());
        } catch (Exception e) {
            log.warn("agent 会话心跳刷新失败, sessionId={}, error={}", state.getSessionId(), e.getMessage());
        }
    }

    // ---- 建后未起跑（startPaused）守卫 ----

    /**
     * 守卫心跳（每 20s）：刷新存活轻键并执行断开策略；超时未被 resume 则自动收口。
     * run 线程在场时期望每轮都跑它，见 {@link #refreshHeartbeat}——这里复用同一实现，
     * 保证「暂停等素材」与会话运行期的存活/断开语义一致。
     */
    private void guardDeferredStart(String sessionId) {
        DeferredStart deferred = deferredStarts.get(sessionId);
        if (deferred == null) {
            cancelDeferredGuard(sessionId);
            return;
        }
        refreshHeartbeat(deferred.state(), deferred.definition());
        boolean stopRequested;
        try {
            stopRequested = store.isStopRequested(sessionId);
        } catch (Exception e) {
            log.warn("未起跑会话停止键读取失败（按未停止处理）, sessionId={}, error={}",
                    sessionId, e.getMessage());
            stopRequested = false;
        }
        boolean timedOut = System.currentTimeMillis() - deferred.state().getStartedAt()
                > DEFERRED_START_TIMEOUT_MILLIS;
        if (!stopRequested && !timedOut) {
            return;
        }
        if (deferredStarts.remove(sessionId, deferred)) {
            cancelDeferredGuard(sessionId);
            try {
                store.clearControls(sessionId);
            } catch (Exception e) {
                log.warn("未起跑会话控制键清理失败（TTL 到期自动失效）, sessionId={}, error={}",
                        sessionId, e.getMessage());
            }
            finishDeferredStart(deferred.state(), timedOut
                    ? "创建后长时间未开始（素材上传未完成），已自动停止"
                    : "等待素材期间会话被停止");
        }
    }

    /** 取消守卫任务（resume/stop/超时收口任一路径都要取消，避免定时任务泄漏） */
    private void cancelDeferredGuard(String sessionId) {
        ScheduledFuture<?> guard = deferredGuards.remove(sessionId);
        if (guard != null) {
            guard.cancel(false);
        }
    }

    /** 「建后未起跑」会话的终态收口：无 run 线程，快照/阶段/计划/事件由本侧一次写齐 */
    private void finishDeferredStart(AgentSessionState state, String reason) {
        state.setStatus(AgentSessionState.STOPPED);
        state.setEndedAt(System.currentTimeMillis());
        state.setError(reason);
        state.setLive(null);
        finalizeStages(state, AgentSessionState.STOPPED, reason);
        finalizePlan(state, AgentSessionState.STOPPED);
        store.saveState(state);
        emitAndPublish(state.getSessionId(), AgentEvent.STOPPED, "会话已停止：" + reason);
        if (streamHub != null) {
            streamHub.publishDone(state);
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
        // 状态迁移即状态帧（pause/resume 后 SSE 客户端免轮询即时可见；hub 缺席为 no-op）
        if (streamHub != null) {
            streamHub.publishStatus(state);
        }
    }

    /** 直写 appendEvent 的点位统一补发 SSE 事件帧（seq≤0 落库失败不发；
     *  与 AgentSessionContext#emit 同口径，双写点共用一个语义） */
    private long emitAndPublish(String sessionId, String type, Object eventPayload) {
        long seq = store.appendEvent(sessionId, type, eventPayload);
        if (seq > 0 && streamHub != null) {
            streamHub.publishEvent(sessionId, seq, type, eventPayload, System.currentTimeMillis());
        }
        return seq;
    }

    private AgentSessionState requireState(String sessionId) {
        AgentSessionState state = store.getState(sessionId);
        if (state == null) {
            // ServiceException(SYS_ERR_MSG, WARN 级)＝只出人话错误信封，不触发全局异常邮件告警、
            // 不刷 ERROR 全文栈——会话快照过期后前端的 status/events 轮询与评价动作是正常现象，不是服务端故障。
            // 本类其余"用户动作时序/入参"类拒绝同理（真故障仍留 IllegalState/IllegalArgument 进告警射程）
            throw new ServiceException("会话不存在或已过期: " + sessionId);
        }
        return state;
    }

    private static String brief(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }
}
