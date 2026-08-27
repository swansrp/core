package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionService;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.flow.FlowDefinitionProvider;
import com.bidr.llm.flow.FlowDetailRes;
import com.bidr.llm.flow.FlowManagerService;
import com.bidr.llm.flow.FlowRegistryRes;
import com.bidr.llm.flow.FlowSaveReq;
import com.bidr.llm.flow.trace.FlowTrace;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import com.bidr.llm.skill.AgentRatingListener;
import com.bidr.llm.skill.SkillRatingFilter;
import com.bidr.llm.skill.SkillRatingRecord;
import com.bidr.llm.skill.SkillRatingService;
import com.bidr.llm.skill.SkillRatingStatRes;
import com.bidr.platform.config.anno.ApiTrace;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentSessionController
 * Description: agent 通用控制与调试端点（llm 基础框架，业务零绑定）——两类 agent 统一入口：
 * <ul>
 *     <li><b>自主规划型</b>：经 {@link AgentSessionService} 发起会话 / 暂停 / 补语恢复 / 停止 /
 *     状态与事件流轮询（前端 AgentChat 组件的数据源）；</li>
 *     <li><b>flow 编排型</b>：注册表 / 编排存取 / 重置 / 执行轨迹（自 ChatBiController 上提泛化，
 *     skill 工作台与轨迹查看不再绑定 chatbi）。</li>
 * </ul>
 * 访问人经 {@link OperatorResolver} SPI 注入（llm 不依赖 authorization），无实现回落 anonymous。
 * 装配条件：类路径有 spring-webmvc 且未配置 {@code llm.agent-api.enabled=false}。
 * 鉴权沿用平台 /web/api 拦截链，不加 @IgnoreAuth。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
@RestController
@RequestMapping("/web/api/agent")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
@ConditionalOnProperty(prefix = "llm.agent-api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentSessionController {

    /** 轨迹列表默认条数上限（新→旧截断） */
    private static final int DEFAULT_TRACE_LIMIT = 50;

    private final AgentSessionService agentSessionService;

    private final FlowManagerService flowManagerService;

    private final FlowTraceRecorder flowTraceRecorder;

    /** flow 型注册表（可为空：应用未注册任何链时 flow 端点仅剩报错路径） */
    private final List<FlowDefinitionProvider> flowProviders;

    private final ObjectProvider<OperatorResolver> operatorResolver;

    /** skill 评价底座（会话整体评价入库；Redis 不可用时静默降级） */
    private final SkillRatingService skillRatingService;

    /** 评价动作业务钩子（按 skillCode 分发；无实现时端点按请求体自组装直落底座） */
    private final List<AgentRatingListener> ratingListeners;

    /** extEquals 查询参数 JSON 解析 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 评价快照保留天数（会话评价无业务参数可配，取与对话同量级的缺省） */
    private static final int RATING_RETENTION_DAYS = 30;

    // ==================== agent 注册表与会话控制 ====================

    /**
     * 两类 agent 注册表清单：flow 型来自 {@link FlowDefinitionProvider}，
     * autonomous 型来自 {@link AutonomousAgentDefinition}（key 唯一性由各自注册机制保证）
     */
    @GetMapping("/agents")
    public List<AgentSummary> listAgents() {
        List<AgentSummary> result = new ArrayList<>();
        for (FlowDefinitionProvider provider : flowProviders) {
            AgentSummary summary = new AgentSummary();
            summary.setKey(provider.flowKey());
            summary.setDisplayName(provider.displayName());
            summary.setType("flow");
            summary.setSkillCode(provider.skillCode());
            result.add(summary);
        }
        for (AutonomousAgentDefinition definition : agentSessionService.registeredAgents()) {
            AgentSummary summary = new AgentSummary();
            summary.setKey(definition.agentKey());
            summary.setDisplayName(definition.displayName());
            summary.setType("autonomous");
            summary.setSkillCode(definition.skillCode());
            result.add(summary);
        }
        return result;
    }

    /**
     * 发起自主 agent 会话：立即返回状态快照（sessionId 供控制与轮询），业务执行体在 run 线程内进行。
     * 断开策略由发起方定义（同一 agent 不同页面场景需求不同）：传则覆盖、不传回落定义层默认
     */
    @PostMapping("/session/start")
    public AgentSessionState start(@RequestBody SessionStartReq req) {
        if (req == null || !StringUtils.hasText(req.getAgentKey())) {
            throw new IllegalArgumentException("agentKey 不能为空");
        }
        DetachPolicy detachPolicy = null;
        if (StringUtils.hasText(req.getDetachPolicy())) {
            try {
                detachPolicy = DetachPolicy.valueOf(req.getDetachPolicy().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("detachPolicy 取值非法：" + req.getDetachPolicy());
            }
        }
        return agentSessionService.start(req.getAgentKey().trim(), req.getPayload(), operator(), detachPolicy);
    }

    /**
     * 暂停会话（body 可带说明，事件流即时可见）；run 线程在下一检查点阻塞
     */
    @PostMapping("/session/{sessionId}/pause")
    public void pause(@PathVariable("sessionId") String sessionId,
                      @RequestBody(required = false) ControlReq req) {
        agentSessionService.pause(sessionId, req == null ? null : req.getNote());
    }

    /**
     * 恢复会话（body 可带补充指导语，注入下一轮上下文）
     */
    @PostMapping("/session/{sessionId}/resume")
    public void resume(@PathVariable("sessionId") String sessionId,
                       @RequestBody(required = false) ControlReq req) {
        agentSessionService.resume(sessionId, req == null ? null : req.getGuidance());
    }

    /**
     * 作答 LLM 提问（ask_user 阻塞等待中）：answer 与 skipped 至少其一；
     * 作答写入控制键由工具线程消费，问题状态随 status 轮询刷新
     */
    @PostMapping("/session/{sessionId}/answer")
    public void answer(@PathVariable("sessionId") String sessionId, @RequestBody AnswerReq req) {
        if (req == null || req.getQuestionId() == null) {
            throw new IllegalArgumentException("questionId 不能为空");
        }
        boolean skipped = Boolean.TRUE.equals(req.getSkipped());
        String answer = req.getAnswer() == null ? "" : req.getAnswer().trim();
        if (!skipped && answer.isEmpty()) {
            throw new IllegalArgumentException("作答内容与跳过至少其一");
        }
        agentSessionService.answer(sessionId, req.getQuestionId(), answer, skipped);
    }

    /**
     * 收口待确认口径（确认页裁决）：revised=false 一键确认认可自决口径；
     * revised=true 改口径（note 为新口径说明，业务侧据条目 impact 重算产出）。
     * 终态后仍可操作；业务写回（如草稿盖章/重生成）由前端另调业务端点完成，本端点只管状态收口
     */
    @PostMapping("/session/{sessionId}/confirmation/{confirmationId}/resolve")
    public com.bidr.llm.agent.session.AgentConfirmation resolveConfirmation(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("confirmationId") int confirmationId,
            @RequestBody(required = false) ConfirmationResolveReq req) {
        boolean revised = req != null && Boolean.TRUE.equals(req.getRevised());
        return agentSessionService.resolveConfirmation(sessionId, confirmationId, revised,
                req == null ? null : req.getNote());
    }

    /**
     * 停止会话（幂等）：停止键跨实例生效 + 本实例线程中断加速收口
     */
    @PostMapping("/session/{sessionId}/stop")
    public void stop(@PathVariable("sessionId") String sessionId) {
        agentSessionService.stop(sessionId);
    }

    /**
     * 活跃会话列表（本人发起的非终态会话，新→旧；agentKey 可选过滤）：
     * 刷新/重连场景数据源——功能型任务（KEEP_RUNNING 策略）断开后台继续，
     * 页面重载后经本端点找回 sessionId 重连 AgentChat 继续跟进；
     * 业务维度（如资产生成的业务 agentCode）经快照 subject 前端自行筛
     */
    @GetMapping("/sessions/active")
    public List<AgentSessionState> activeSessions(@RequestParam(name = "agentKey", required = false) String agentKey) {
        return agentSessionService.activeSessions(operator(),
                StringUtils.hasText(agentKey) ? agentKey.trim() : null);
    }

    /**
     * 会话状态快照（含失联判定：非终态心跳超时改写 STOPPED）
     */
    @ApiTrace(log = false)
    @GetMapping("/session/{sessionId}/status")
    public AgentSessionState status(@PathVariable("sessionId") String sessionId) {
        return agentSessionService.status(sessionId);
    }

    /**
     * 事件流增量读取（seq 大于 sinceSeq，前端 2s 轮询数据源）
     */
    @ApiTrace(log = false)
    @GetMapping("/session/{sessionId}/events")
    public List<AgentEvent> events(@PathVariable("sessionId") String sessionId,
                                   @RequestParam(name = "sinceSeq", defaultValue = "0") long sinceSeq) {
        return agentSessionService.events(sessionId, sinceSeq);
    }

    /**
     * 会话整体评价（AgentChat 结论区点赞/点踩；空=取消）：经 skill 评价底座入库，
     * ratingId=sessionId 同会话重复评价覆盖，ext 携 agentKey 供运营统计筛选
     */
    @PostMapping("/session/{sessionId}/rate")
    public void rate(@PathVariable("sessionId") String sessionId, @RequestBody SessionRateReq req) {
        AgentSessionState state = agentSessionService.status(sessionId);
        String rating = req == null || req.getRating() == null ? "" : req.getRating().trim();
        String ratingId = state.getSessionId();
        if (rating.isEmpty()) {
            skillRatingService.remove(state.getSkillCode(), ratingId);
            return;
        }
        SkillRatingRecord record = new SkillRatingRecord();
        record.setRatingId(ratingId);
        record.setConversationId(sessionId);
        record.setMessageId(sessionId);
        record.setOperator(operator());
        record.setQuestion(state.getDisplayName());
        record.setAnswer(state.getSummary());
        record.setRating(rating);
        record.setMessageTime(state.getEndedAt() == null ? state.getStartedAt() : state.getEndedAt());
        record.setRatingTime(System.currentTimeMillis());
        if (StringUtils.hasText(req.getFeedback())) {
            record.getExt().put("feedback", req.getFeedback());
        }
        record.getExt().put("agentKey", state.getAgentKey());
        skillRatingService.save(state.getSkillCode(), record, RATING_RETENTION_DAYS);
    }

    // ==================== 通用评价（skill 底座，flow/autonomous 两型统一） ====================

    /**
     * 回答评价（like/dislike，空=取消）：经 {@link AgentRatingListener} 钩子后落 skill 底座。
     * 业务侧（如 chatbi 对话正文双写）实现钩子按 skillCode 接管记录组装；无钩子时按请求体自组装，
     * operator 取 {@link OperatorResolver}。ratingId 业务自定（建议 conversationId:messageId）
     */
    @PostMapping("/rating/save")
    public void saveRating(@RequestBody RatingSaveReq req) {
        if (req == null || !StringUtils.hasText(req.getSkillCode())) {
            throw new IllegalArgumentException("skillCode 不能为空");
        }
        if (!StringUtils.hasText(req.getRatingId())) {
            throw new IllegalArgumentException("ratingId 不能为空");
        }
        String skillCode = req.getSkillCode().trim();
        String ratingId = req.getRatingId().trim();
        String rating = req.getRating() == null ? "" : req.getRating().trim();
        String operator = operator();
        AgentRatingListener listener = findRatingListener(skillCode);
        if (rating.isEmpty()) {
            if (listener != null) {
                listener.beforeRemove(skillCode, ratingId, operator);
            }
            skillRatingService.remove(skillCode, ratingId);
            return;
        }
        if (!"like".equals(rating) && !"dislike".equals(rating)) {
            throw new IllegalArgumentException("评价取值非法：" + rating);
        }
        SkillRatingRecord record = listener == null ? null : listener.beforeRate(skillCode, ratingId, rating, operator);
        if (record == null) {
            record = new SkillRatingRecord();
            record.setRatingId(ratingId);
            record.setConversationId(req.getConversationId());
            record.setMessageId(req.getMessageId());
            record.setOperator(operator);
            record.setQuestion(req.getQuestion());
            record.setAnswer(req.getAnswer());
            record.setRating(rating);
            record.setMessageTime(req.getMessageTime());
            if (req.getExt() != null) {
                record.getExt().putAll(req.getExt());
            }
        }
        if (record.getRatingTime() == null) {
            record.setRatingTime(System.currentTimeMillis());
        }
        int retentionDays = listener == null ? RATING_RETENTION_DAYS : listener.retentionDays(skillCode);
        skillRatingService.save(skillCode, record, retentionDays);
    }

    /**
     * 评价运营统计（跨访问人聚合；汇总随筛选联动）：类型/评价人/时间段/关键词为通用维度，
     * 业务维度走 extEquals（JSON 对象串，如 {@code {"tableId":"xxx"}}，对记录 ext 精确匹配）
     */
    @GetMapping("/rating/stat")
    public SkillRatingStatRes ratingStat(String skillCode, String rating, String operator,
                                         Long startTime, Long endTime, String keyword, String extEquals) {
        requireText(skillCode, "skill 标识");
        SkillRatingFilter filter = new SkillRatingFilter();
        filter.setRating(StringUtils.hasText(rating) ? rating.trim() : null);
        filter.setOperator(StringUtils.hasText(operator) ? operator.trim() : null);
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        filter.setKeyword(StringUtils.hasText(keyword) ? keyword.trim() : null);
        if (StringUtils.hasText(extEquals)) {
            try {
                filter.getExtEquals().putAll(objectMapper.readValue(
                        extEquals.trim(), new TypeReference<Map<String, String>>() {
                        }));
            } catch (Exception e) {
                throw new IllegalArgumentException("extEquals 非法 JSON：" + extEquals);
            }
        }
        return skillRatingService.list(skillCode.trim(), filter);
    }

    /**
     * 按 skillCode 找评价钩子（首个 supports 命中）
     */
    private AgentRatingListener findRatingListener(String skillCode) {
        for (AgentRatingListener listener : ratingListeners) {
            if (listener.supports(skillCode)) {
                return listener;
            }
        }
        return null;
    }

    // ==================== flow 编排管理（自 ChatBiController 上提泛化） ====================

    /**
     * skill 注册表（skill 下链清单 + 画布可用结点类型元数据，工作台启动数据源）
     */
    @GetMapping("/flow/registry")
    public FlowRegistryRes getFlowRegistry(String skillCode) {
        requireText(skillCode, "skill 标识");
        return flowManagerService.registry(skillCode.trim());
    }

    /**
     * 获取流程编排详情（库中无自定义时返回内置默认链）
     */
    @GetMapping("/flow/detail")
    public FlowDetailRes getFlowDetail(String flowKey) {
        requireText(flowKey, "流程标识");
        return flowManagerService.getFlow(flowKey.trim());
    }

    /**
     * 保存流程编排（结构校验后落库，提示词即改即生效）
     */
    @PostMapping("/flow/save")
    public void saveFlow(@Validated @RequestBody FlowSaveReq req) {
        flowManagerService.saveFlow(req);
    }

    /**
     * 重置流程编排为内置默认链
     */
    @PostMapping("/flow/reset")
    public void resetFlow(String flowKey) {
        requireText(flowKey, "流程标识");
        flowManagerService.resetFlow(flowKey.trim());
    }

    /**
     * 流程执行轨迹列表（Redis 按访问人保留，天数见系统参数）：
     * flowKey 过滤单链；skillCode 过滤该 skill 下全部链；都空返回本人全部（新→旧，截断 limit 条）
     */
    @ApiTrace(log = false)
    @GetMapping("/flow/traces")
    public List<FlowTrace.TraceRecord> listFlowTraces(String skillCode, String flowKey,
                                                      @RequestParam(name = "limit", defaultValue = "0") int limit) {
        List<FlowTrace.TraceRecord> traces = flowTraceRecorder.listTraces(
                StringUtils.hasText(flowKey) ? flowKey.trim() : null, operator());
        if (!StringUtils.hasText(skillCode) || traces.isEmpty()) {
            return cap(traces, limit);
        }
        String skill = skillCode.trim();
        Map<String, String> flowKeyToSkill = new HashMap<>();
        for (FlowDefinitionProvider provider : flowProviders) {
            flowKeyToSkill.put(provider.flowKey(), provider.skillCode());
        }
        List<FlowTrace.TraceRecord> filtered = new ArrayList<>();
        for (FlowTrace.TraceRecord record : traces) {
            if (skill.equals(flowKeyToSkill.get(record.getFlowKey()))) {
                filtered.add(record);
            }
        }
        return cap(filtered, limit);
    }

    /**
     * 流程执行轨迹详情（含 llm 提示词/回答、extract 输入/结果全文）
     */
    @GetMapping("/flow/trace/{traceId}")
    public FlowTrace.TraceRecord getFlowTrace(@PathVariable("traceId") String traceId) {
        requireText(traceId, "轨迹标识");
        FlowTrace.TraceRecord record = flowTraceRecorder.getTrace(traceId.trim());
        if (record == null) {
            throw new IllegalArgumentException("轨迹不存在或已过期: " + traceId);
        }
        return record;
    }

    // ==================== 私有工具 ====================

    /**
     * 当前访问人（OperatorResolver SPI，业务注入；无实现回落 null → 存储侧 anonymous）
     */
    private String operator() {
        OperatorResolver resolver = operatorResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.currentOperator();
        } catch (Exception e) {
            log.warn("访问人解析失败，回落 anonymous, error={}", e.getMessage());
            return null;
        }
    }

    private List<FlowTrace.TraceRecord> cap(List<FlowTrace.TraceRecord> traces, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_TRACE_LIMIT;
        }
        return traces.size() <= limit ? traces : new ArrayList<>(traces.subList(0, limit));
    }

    private static void requireText(String text, String name) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }

    // ==================== 请求/响应 VO ====================

    /**
     * 发起会话请求：agentKey + 业务自由参数
     */
    @Data
    public static class SessionStartReq {

        /** agent 注册标识（AutonomousAgentDefinition#agentKey） */
        private String agentKey;

        /** 业务参数（资产生成传选表清单、问数传问题等，由各 agent 定义自行解读） */
        private Map<String, Object> payload;

        /** 可选：断开策略覆盖（STOP_ON_DETACH 断开即停 / KEEP_RUNNING 后台继续可重连）；
         *  不传回落定义层默认——策略随页面场景定，由发起方定义 */
        private String detachPolicy;
    }

    /**
     * 会话控制请求：pause 用 note（暂停说明）、resume 用 guidance（补充指导语）
     */
    @Data
    public static class ControlReq {

        /** 暂停说明（事件流展示） */
        private String note;

        /** 恢复指导语（注入下一轮上下文，「补 2 句继续」） */
        private String guidance;
    }

    /**
     * 作答 LLM 提问请求：answer 与 skipped 至少其一（skipped=true 时 answer 可空）
     */
    @Data
    public static class AnswerReq {

        /** 问题编号（status.questions 条目 id） */
        private Integer questionId;

        /** 作答文本（候选项选择或自由输入） */
        private String answer;

        /** 交由 AI 自行决策（跳过） */
        private Boolean skipped;
    }

    /**
     * 通用评价请求：rating 空=取消；无业务钩子接管时，下方字段供端点自组装记录
     */
    @Data
    public static class RatingSaveReq {

        /** skill 标识（必填，评价按 skill 隔离） */
        private String skillCode;

        /** 评价标识（必填，业务自定，如 conversationId:messageId / sessionId） */
        private String ratingId;

        /** 评价：like-点赞 / dislike-点踩 / 空-取消 */
        private String rating;

        /** 对话标识（自组装时透传） */
        private String conversationId;

        /** 被评价消息标识（自组装时透传） */
        private String messageId;

        /** 本轮用户提问摘要（自组装时透传） */
        private String question;

        /** 被评价的回答正文摘要（自组装时透传） */
        private String answer;

        /** 回答时间（毫秒时间戳，自组装时透传） */
        private Long messageTime;

        /** 业务维度扩展键值（自组装时透传，筛选走精确匹配） */
        private Map<String, String> ext;
    }

    /**
     * 会话评价请求：rating 空=取消；feedback 收集反馈文本（dislike 时弹层填）
     */
    @Data
    public static class SessionRateReq {

        /** 评价：like-点赞 / dislike-点踩 / 空-取消 */
        private String rating;

        /** 反馈文本（dislike 时收集原因，存 ext） */
        private String feedback;
    }

    /**
     * 收口待确认口径请求：revised 空/false=一键确认；true=改口径（note 必填新口径说明）
     */
    @Data
    public static class ConfirmationResolveReq {

        /** 是否改口径（false=认可自决口径） */
        private Boolean revised;

        /** 新口径说明（revised=true 时必填，业务侧据此重算） */
        private String note;
    }

    /**
     * agent 注册表条目：key/名称/类型（flow|autonomous）/skillCode
     */
    @Data
    public static class AgentSummary {

        /** flow 型=flowKey；autonomous 型=agentKey */
        private String key;

        /** 显示名 */
        private String displayName;

        /** 类型：flow（编排型，调试+运行两阶段）| autonomous（自主规划型，仅运行） */
        private String type;

        /** 归属 skill（SkillWorkbench 编组） */
        private String skillCode;
    }
}
