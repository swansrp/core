package com.bidr.insight.controller;

import com.bidr.insight.service.ChatBiConversationService;
import com.bidr.insight.service.ChatBiRatingService;
import com.bidr.insight.service.ChatBiRouterService;
import com.bidr.insight.service.ChatBiSemanticService;
import com.bidr.insight.service.ChatBiSensitiveService;
import com.bidr.insight.service.ChatBiService;
import com.bidr.insight.vo.ChatBiAskReq;
import com.bidr.insight.vo.ChatBiConversation;
import com.bidr.insight.vo.ChatBiRateReq;
import com.bidr.insight.vo.ChatBiRatingStatRes;
import com.bidr.insight.vo.ChatBiRouteItem;
import com.bidr.insight.vo.ChatBiRouteReq;
import com.bidr.insight.vo.ChatBiRouteRes;
import com.bidr.insight.vo.ChatBiSemanticCatalog;
import com.bidr.insight.vo.ChatBiSensitiveColumnRes;
import com.bidr.insight.vo.ChatBiSensitiveSaveReq;
import com.bidr.insight.vo.ChatBiTableDescReq;
import com.bidr.llm.flow.FlowDetailRes;
import com.bidr.llm.flow.FlowManagerService;
import com.bidr.llm.flow.FlowRegistryRes;
import com.bidr.llm.flow.FlowSaveReq;
import com.bidr.llm.flow.trace.FlowTrace;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Title: ChatBiController
 * Description: 智能问数入口——语义目录查询 + SSE 流式问数（走默认 AuthLogin，SSO 登录访问）。
 * <p>
 * 事件协议见 {@link com.bidr.llm.sse.FlowSseSender}：
 * delta（token 增量）/ spec（chart-spec 编排指令）/ done（剔除代码块后的正文）/ error。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Api(tags = "智能问数")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiController {

    private final ChatBiService chatBiService;
    private final ChatBiSemanticService chatBiSemanticService;
    private final ChatBiSensitiveService chatBiSensitiveService;
    private final ChatBiRouterService chatBiRouterService;
    private final FlowManagerService flowManagerService;
    private final FlowTraceRecorder flowTraceRecorder;
    private final ChatBiConversationService chatBiConversationService;
    private final ChatBiRatingService chatBiRatingService;

    /**
     * 流式问数：SseEmitter 不走 JSON 消息转换器，全局响应包装不影响本端点
     */
    @ApiOperation(value = "智能问数（SSE 流式）")
    @RequestMapping(value = "/ask", method = RequestMethod.POST, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Validated @RequestBody ChatBiAskReq req) {
        // 超时设为 0 表示不限时，由模型侧的生成结束/失败来关闭连接
        SseEmitter emitter = new SseEmitter(0L);
        chatBiService.ask(req, emitter);
        return emitter;
    }

    @ApiOperation(value = "获取语义目录")
    @RequestMapping(value = "/semantic", method = RequestMethod.GET)
    public ChatBiSemanticCatalog getSemanticCatalog(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiSemanticService.getSemanticCatalog(tableId);
    }

    @ApiOperation(value = "敏感列配置页列清单（看板全量有效列 + 敏感标记与配对列回显）")
    @RequestMapping(value = "/sensitive/columns", method = RequestMethod.GET)
    public List<ChatBiSensitiveColumnRes> listSensitiveColumns(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiSensitiveService.listColumnsWithFlag(tableId);
    }

    @ApiOperation(value = "保存敏感列配置（整板覆盖：勾选列+配对替换列，空清单即清空恢复）")
    @RequestMapping(value = "/sensitive/save", method = RequestMethod.POST)
    public void saveSensitiveColumns(@Validated @RequestBody ChatBiSensitiveSaveReq req) {
        chatBiSensitiveService.saveSensitiveColumns(req);
    }

    @ApiOperation(value = "获取路由候选目录（候选注册制：仅写了业务描述的看板）")
    @RequestMapping(value = "/route/catalog", method = RequestMethod.GET)
    public List<ChatBiRouteItem> getRouteCatalog() {
        return chatBiRouterService.getRouteCatalog();
    }

    @ApiOperation(value = "看板路由（按问题选出最相关看板）")
    @RequestMapping(value = "/route", method = RequestMethod.POST)
    public ChatBiRouteRes route(@Validated @RequestBody ChatBiRouteReq req) {
        return chatBiRouterService.route(req);
    }

    @ApiOperation(value = "保存看板业务描述（写描述=注册进候选，空白=注销）")
    @RequestMapping(value = "/route/desc", method = RequestMethod.POST)
    public void saveTableDesc(@Validated @RequestBody ChatBiTableDescReq req) {
        chatBiRouterService.saveTableDesc(req);
    }

    @ApiOperation(value = "全量看板与业务描述（候选注册管理页数据源）")
    @RequestMapping(value = "/route/desc/all", method = RequestMethod.GET)
    public List<ChatBiRouteItem> listPortalDesc() {
        return chatBiRouterService.listPortalDesc();
    }

    @ApiOperation(value = "AI 生成看板描述草稿（按语义目录汇总喂模型，不落库）")
    @RequestMapping(value = "/route/desc/generate", method = RequestMethod.GET)
    public String generateTableDesc(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiRouterService.generateDesc(tableId);
    }

    @ApiOperation(value = "skill 注册表（skill 下链清单 + 画布可用结点类型元数据，工作台启动数据源）")
    @RequestMapping(value = "/flow/registry", method = RequestMethod.GET)
    public FlowRegistryRes getFlowRegistry(String skillCode) {
        Validator.assertNotNull(skillCode, ErrCodeSys.PA_PARAM_NULL, "skill 标识");
        return flowManagerService.registry(skillCode);
    }

    @ApiOperation(value = "获取流程编排详情（库中无自定义时返回内置默认链）")
    @RequestMapping(value = "/flow/detail", method = RequestMethod.GET)
    public FlowDetailRes getFlowDetail(String flowKey) {
        Validator.assertNotNull(flowKey, ErrCodeSys.PA_PARAM_NULL, "流程标识");
        return flowManagerService.getFlow(flowKey);
    }

    @ApiOperation(value = "保存流程编排（结构校验后落库，提示词即改即生效）")
    @RequestMapping(value = "/flow/save", method = RequestMethod.POST)
    public void saveFlow(@Validated @RequestBody FlowSaveReq req) {
        flowManagerService.saveFlow(req);
    }

    @ApiOperation(value = "重置流程编排为内置默认链")
    @RequestMapping(value = "/flow/reset", method = RequestMethod.POST)
    public void resetFlow(String flowKey) {
        Validator.assertNotNull(flowKey, ErrCodeSys.PA_PARAM_NULL, "流程标识");
        flowManagerService.resetFlow(flowKey);
    }

    @ApiOperation(value = "流程执行轨迹列表（Redis 按访问人保留，天数见系统参数；flowKey 空则返回全部链路）")
    @RequestMapping(value = "/flow/traces", method = RequestMethod.GET)
    public List<FlowTrace.TraceRecord> listFlowTraces(String flowKey) {
        return flowTraceRecorder.listTraces(flowKey, AccountContext.getDisplayName());
    }

    @ApiOperation(value = "流程执行轨迹详情（含 llm 提示词/回答、extract 输入/结果全文）")
    @RequestMapping(value = "/flow/trace/detail", method = RequestMethod.GET)
    public FlowTrace.TraceRecord getFlowTraceDetail(String traceId) {
        Validator.assertNotNull(traceId, ErrCodeSys.PA_PARAM_NULL, "轨迹标识");
        return flowTraceRecorder.getTrace(traceId);
    }

    @ApiOperation(value = "历史对话列表（Redis 按访问人保留，天数见系统参数；新→旧，列表不带消息明细）")
    @RequestMapping(value = "/conversation/list", method = RequestMethod.GET)
    public List<ChatBiConversation> listConversations() {
        return chatBiConversationService.listConversations(AccountContext.getDisplayName());
    }

    @ApiOperation(value = "对话详情（含全部消息与 chart-spec，前端恢复渲染用）")
    @RequestMapping(value = "/conversation/detail", method = RequestMethod.GET)
    public ChatBiConversation getConversationDetail(String conversationId) {
        Validator.assertNotNull(conversationId, ErrCodeSys.PA_PARAM_NULL, "对话标识");
        return chatBiConversationService.getConversation(conversationId);
    }

    @ApiOperation(value = "删除历史对话（仅能删自己的）")
    @RequestMapping(value = "/conversation/delete", method = RequestMethod.POST)
    public void deleteConversation(String conversationId) {
        Validator.assertNotNull(conversationId, ErrCodeSys.PA_PARAM_NULL, "对话标识");
        chatBiConversationService.deleteConversation(conversationId, AccountContext.getDisplayName());
    }

    @ApiOperation(value = "评价助手回复（like/dislike，空=取消；双写对话正文与全局评价索引）")
    @RequestMapping(value = "/conversation/rate", method = RequestMethod.POST)
    public void rateConversation(@Validated @RequestBody ChatBiRateReq req) {
        chatBiRatingService.rate(req, AccountContext.getDisplayName());
    }

    @ApiOperation(value = "评价运营统计（跨访问人聚合；筛选：类型/看板/评价人/时间段/关键词，汇总随筛选联动）")
    @RequestMapping(value = "/rating/stat", method = RequestMethod.GET)
    public ChatBiRatingStatRes getRatingStat(String rating, String tableId, String operator,
                                             Long startTime, Long endTime, String keyword) {
        return chatBiRatingService.listRatings(rating, tableId, operator, startTime, endTime, keyword);
    }
}
