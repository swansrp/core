package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AgentLoopListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: AgentSessionContext
 * Description: 会话上下文——业务执行体（AutonomousAgentDefinition#start）与 llm 会话层的桥：
 * 事件上报（思考过程/工具轨迹/结论）、控制原语（停止/暂停阻塞）、结论摘要。
 * {@link #loopListener()} 产出的组合钩子直连 {@link com.bidr.llm.agent.ToolAgentRunner}：
 * log 转发事件流、shouldStop 接停止键与线程中断、awaitResumeIfPaused 阻塞等待恢复并
 * 返回恢复指导语（引擎注入下一轮上下文）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class AgentSessionContext {

    /** 暂停检查点轮询间隔（毫秒）：阻塞等待恢复时的醒查周期 */
    private static final long PAUSE_POLL_MILLIS = 1000;

    /** awaitAnswer 哨兵返回：等待期间收到停止请求（工具侧报错文回传，引擎下轮头 shouldStop 收口） */
    public static final String AWAIT_STOPPED = "\u0000STOPPED";

    /** awaitAnswer 哨兵返回：超时未作答（工具侧引导 LLM 按合理默认继续） */
    public static final String AWAIT_EXPIRED = "\u0000EXPIRED";

    /** 作答控制键 JSON 解析（与 AgentSessionService#answer 组装口径一致） */
    private static final ObjectMapper ANSWER_OM = new ObjectMapper();

    private final AgentSessionState state;
    private final AgentSessionStore store;
    private final Map<String, Object> payload;
    private final AtomicReference<String> summary = new AtomicReference<>();
    /** 计划待办板（状态机下沉框架层）：真源是 state.plan 同一 list 引用，
     *  变更回调挂快照持久化；公开四方法保留为约定名桥接（见接入指南 §2.4） */
    private final PlanBoard planBoard;

    public AgentSessionContext(AgentSessionState state, AgentSessionStore store, Map<String, Object> payload) {
        this.state = state;
        this.store = store;
        this.payload = payload;
        this.planBoard = new PlanBoard(state.getPlan(), this::persistStages);
    }

    /** 计划待办板（工具注册用：new AgentPlanTools(ctx.planBoard(), …)） */
    public PlanBoard planBoard() {
        return planBoard;
    }

    public String getSessionId() {
        return state.getSessionId();
    }

    public AgentSessionState getState() {
        return state;
    }

    /** 启动参数（Controller 透传的业务结构，只读视图） */
    public Map<String, Object> getPayload() {
        return payload;
    }

    /** 结论摘要（FINISHED 时前端展示；多次调用以最后一次为准） */
    public void setSummary(String text) {
        summary.set(text);
    }

    public String takeSummary() {
        return summary.get();
    }

    // ---- 事件上报 ----

    /** 追加过程事件（见 AgentEvent 类型常量），返回事件 seq */
    public long emit(String type, Object eventPayload) {
        return store.appendEvent(state.getSessionId(), type, eventPayload);
    }

    /** 过程日志便捷上报（LOG 事件） */
    public void log(String line) {
        emit(AgentEvent.LOG, line);
    }

    /** LLM 流式实时内容上报（替换式：覆盖 state.live 并落快照，与问数链 live 同口径）；
     *  适合高频推送（每秒级）：不进追加式事件流不膨胀存储，轮询拿到的永远是最新一帧；
     *  传 null 即清空（终态收口用） */
    public void pushLive(String text) {
        state.setLive(text);
        persistStages();
    }

    // ---- 阶段上报（AgentStages 数据源：状态快照全量渲染 + stage 事件实时跳动） ----

    /**
     * 声明阶段清单（start 内尽早调用）：全部置 pending 落状态快照并逐段发 stage 事件；
     * 重复调用覆盖旧清单（以最后一次为准）
     */
    public void defineStages(AgentStage... stages) {
        state.setStages(new ArrayList<>(Arrays.asList(stages)));
        persistStages();
        for (AgentStage stage : state.getStages()) {
            emit(AgentEvent.STAGE, stage);
        }
    }

    /** 阶段开始（running 打戳）；未声明的 key 忽略并记日志 */
    public void stageStart(String key, String detail) {
        updateStage(key, AgentStage.RUNNING, detail, true);
    }

    /** 阶段完成（detail 可携成果摘要，如「实体 12 / 维度 8」） */
    public void stageDone(String key, String detail) {
        updateStage(key, AgentStage.OK, detail, false);
    }

    /** 阶段失败（detail 携原因） */
    public void stageFail(String key, String detail) {
        updateStage(key, AgentStage.ERROR, detail, false);
    }

    /** 阶段跳过（如条件不满足直通） */
    public void stageSkip(String key) {
        updateStage(key, AgentStage.SKIPPED, null, false);
    }

    private void updateStage(String key, String status, String detail, boolean touchStart) {
        AgentStage target = null;
        for (AgentStage stage : state.getStages()) {
            if (stage.getKey() != null && stage.getKey().equals(key)) {
                target = stage;
                break;
            }
        }
        if (target == null) {
            log.warn("阶段上报未命中声明清单, sessionId={}, key={}", state.getSessionId(), key);
            return;
        }
        target.setStatus(status);
        target.setDetail(detail);
        if (touchStart) {
            target.setStartedAt(System.currentTimeMillis());
        }
        target.setEndedAt(AgentStage.RUNNING.equals(status) ? null : System.currentTimeMillis());
        persistStages();
        emit(AgentEvent.STAGE, target);
    }

    /** 阶段快照落 store（写失败仅记日志，不阻断业务执行）；
     *  落快照同时刷新心跳：本方法皆由 run 线程调用（pushLive/阶段/计划等高频写入），
     *  活动即存活信号——否则内存快照会把 Redis 心跳回滚为启动时刻，
     *  会话满 180s 后被查询侧误判执行实例失联 */
    private void persistStages() {
        try {
            state.setHeartbeat(System.currentTimeMillis());
            store.saveState(state);
        } catch (Exception e) {
            log.warn("阶段快照写入失败（忽略）, sessionId={}, error={}", state.getSessionId(), e.getMessage());
        }
    }

    // ---- 计划待办上报（前端清单勾选数据源：状态快照全量渲染，submit_plan/done_plan_item 工具桥） ----

    /**
     * 提交计划待办清单（会话开局阶段调用）：全部置 pending 落状态快照；
     * 重复调用覆盖旧清单（以最后一次为准），id 自 1 重排（PlanBoard 委托）
     */
    public void submitPlan(List<String> items) {
        planBoard.submit(items);
    }
    
    /** 待办挑勾（id 自 1；未命中返回 false 不变更） */
    public boolean donePlanItem(int id, String note) {
        return planBoard.done(id, note);
    }
    
    /** 待办标记执行中（开始做该条目前调用；同一时刻至多一条执行中，
     *  新开自动回退旧执行中为 pending；未命中返回 false 不变更） */
    public boolean startPlanItem(int id) {
        return planBoard.start(id);
    }
    
    /** 计划快照文本（工具回显供 LLM 掌握编号与进度）：每行「id. [x]/[>]/[ ] 文本」 */
    public String planText() {
        return planBoard.planText();
    }

    /** 计划进度单行摘要（start/done 挑勾回显用，避免每轮回显全量清单刷上下文）：
     *  如「计划进度：3/9 完成，执行中 #4」；待办编号 LLM 自己刚提交过，无需回灌全文 */
    public String planBrief() {
        return planBoard.planBrief();
    }

    // ---- 用户决策提问（ask_user 工具桥：问题落状态快照前端渲染卡片，作答经控制键消费） ----

    /**
     * 提交一个等待用户决策的问题：追加 state.questions 落快照并发 QUESTION 事件，
     * id 会话内自 1 递增（作答控制键按 id 匹配防串答）；前端随 status 轮询渲染问题卡片
     */
    public AgentQuestion askQuestion(String question, List<String> options) {
        List<AgentQuestion> list = state.getQuestions();
        if (list == null) {
            list = new ArrayList<>();
            state.setQuestions(list);
        }
        int id = list.isEmpty() ? 1 : list.get(list.size() - 1).getId() + 1;
        AgentQuestion item = new AgentQuestion(id, question, options);
        list.add(item);
        persistStages();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("question", item.getQuestion());
        payload.put("options", item.getOptions());
        emit(AgentEvent.QUESTION, payload);
        return item;
    }

    /**
     * 阻塞等待问题作答（工具线程内调用，1s 醒查）：每次醒查先看停止请求，再消费作答控制键
     * （questionId 匹配才生效，前序超时残留丢弃）；命中后置位 answered/skipped 落快照并
     * 发 ANSWERED 事件。返回用户答案文本；跳过返回 null（状态见 q.getStatus()）；
     * 停止返回 {@link #AWAIT_STOPPED}，超时置 expired 后返回 {@link #AWAIT_EXPIRED}
     */
    public String awaitAnswer(AgentQuestion q, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            if (isStopRequested()) {
                return AWAIT_STOPPED;
            }
            String raw = store.consumeAnswer(state.getSessionId());
            if (raw != null) {
                String matched = applyAnswerIfMatched(q, raw);
                if (matched != MATCH_MISS) {
                    return matched;
                }
                // 不匹配的作答（前序超时问题残留）已消费丢弃，继续等待当前问题
            }
            if (System.currentTimeMillis() >= deadline) {
                q.setStatus(AgentQuestion.EXPIRED);
                q.setAnsweredAt(System.currentTimeMillis());
                persistStages();
                emit(AgentEvent.LOG, "提问 #" + q.getId() + " 等待超时未作答，按合理默认继续");
                return AWAIT_EXPIRED;
            }
            try {
                Thread.sleep(PAUSE_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AWAIT_STOPPED;
            }
        }
    }

    /** 作答不匹配哨兵（与合法返回 null=跳过 区分） */
    private static final String MATCH_MISS = "\u0000MISS";

    /** 解析作答 JSON 并按 questionId 匹配落位：不匹配返回 MATCH_MISS；
     *  命中返回答案文本（跳过返回 null），状态置位并落快照发事件 */
    private String applyAnswerIfMatched(AgentQuestion q, String raw) {
        int questionId;
        String answer;
        boolean skipped;
        try {
            JsonNode node = ANSWER_OM.readTree(raw);
            questionId = node.path("questionId").asInt(-1);
            answer = node.path("answer").asText("").trim();
            skipped = node.path("skipped").asBoolean(false);
        } catch (Exception e) {
            log.warn("作答 JSON 解析失败丢弃, sessionId={}, error={}", state.getSessionId(), e.getMessage());
            return MATCH_MISS;
        }
        if (questionId != q.getId()) {
            return MATCH_MISS;
        }
        if (skipped || answer.isEmpty()) {
            q.setStatus(AgentQuestion.SKIPPED);
            q.setAnsweredAt(System.currentTimeMillis());
            persistStages();
            emit(AgentEvent.ANSWERED, "用户跳过了该问题，交由 AI 自行决策");
            return null;
        }
        q.setStatus(AgentQuestion.ANSWERED);
        q.setAnswer(answer);
        q.setAnsweredAt(System.currentTimeMillis());
        persistStages();
        emit(AgentEvent.ANSWERED, answer);
        return answer;
    }

    // ---- 待确认口径登记（自决口径收口闭环：登记落状态快照，终态后确认页逐条裁决） ----

    /**
     * 登记一条未经用户确认的自决口径：追加 state.confirmations 落快照并发 CONFIRMATION 事件，
     * id 会话内自 1 递增；同疑点（question 同文）重复登记去重不重发，防 LLM 重复提交刷条目
     */
    public AgentConfirmation addConfirmation(String question, String adopted, String evidence, String impact) {
        List<AgentConfirmation> list = state.getConfirmations();
        if (list == null) {
            list = new ArrayList<>();
            state.setConfirmations(list);
        }
        if (question != null) {
            for (AgentConfirmation c : list) {
                if (question.trim().equals(c.getQuestion())) {
                    return c;
                }
            }
        }
        int id = list.isEmpty() ? 1 : list.get(list.size() - 1).getId() + 1;
        AgentConfirmation item = new AgentConfirmation(id, question, adopted, evidence, impact);
        list.add(item);
        persistStages();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("question", item.getQuestion());
        payload.put("adopted", item.getAdopted());
        emit(AgentEvent.CONFIRMATION, payload);
        return item;
    }

    // ---- 控制原语 ----

    /** 停止请求（store 停止键或线程中断任一命中即视为停止） */
    public boolean isStopRequested() {
        return store.isStopRequested(state.getSessionId()) || Thread.currentThread().isInterrupted();
    }

    /** 暂停态检查（不阻塞） */
    public boolean isPaused() {
        return store.isPaused(state.getSessionId());
    }

    /**
     * 暂停阻塞（引擎每轮循环头调用）：暂停期间每秒醒查，恢复或停止时返回。
     * 暂停/恢复的时刻各补一条事件（前端状态条）；返回恢复指导语（无则 null）
     */
    public String awaitResumeIfPaused() {
        if (!isPaused() || isStopRequested()) {
            return null;
        }
        emit(AgentEvent.PAUSED, "会话已暂停，等待恢复指令");
        while (isPaused() && !isStopRequested()) {
            try {
                Thread.sleep(PAUSE_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        if (isStopRequested()) {
            return null;
        }
        String guidance = store.consumeResumeGuidance(state.getSessionId());
        emit(AgentEvent.RESUMED, guidance == null ? "会话已恢复" : "会话已恢复（附带用户补充指导）");
        if (guidance != null && !guidance.trim().isEmpty()) {
            emit(AgentEvent.GUIDANCE, guidance);
        }
        return guidance;
    }

    /**
     * 工具循环组合钩子：log→事件流、shouldStop→停止键+线程中断、
     * awaitResumeIfPaused→暂停阻塞（指导语由引擎注入下一轮上下文）
     */
    public AgentLoopListener loopListener() {
        return new AgentLoopListener() {
            @Override
            public void log(String line) {
                emit(AgentEvent.LOG, line);
            }

            @Override
            public boolean shouldStop() {
                return isStopRequested();
            }

            @Override
            public String awaitResumeIfPaused() {
                return AgentSessionContext.this.awaitResumeIfPaused();
            }
        };
    }
}
