package com.bidr.insight.smartquery.controller;

import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.flow.MaintainQueryAgentDefinition;
import com.bidr.insight.smartquery.service.SmartQueryMaintainService;
import com.bidr.insight.smartquery.vo.SmartQueryAskRes;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.session.AgentSessionService;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.platform.config.anno.ApiTrace;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminSmartQueryMaintainController
 * Description: 维护问数与资产建议审批：问数遇到资产缺失时 LLM 建议资产并以临时语义层
 * 一次性作答（/ask），建议落待审提案；管理员列表查看后合并进草稿（仍需发布+刷新生效）
 * 或驳回。与运行期三端点隔离：提案与建议资产不进运行期缓存
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Slf4j
@Api(tags = "智能问数 - 维护与变更建议")
@RestController
@RequiredArgsConstructor
@RequestMapping("/web/insight/agent/admin/maintain")
public class AdminSmartQueryMaintainController {

    private final SmartQueryMaintainService smartQueryMaintainService;
    private final AgentSessionService agentSessionService;

    @ApiOperation("维护问数：自然语言 → LLM 解析 → 校验；资产缺失时 LLM 建议资产 + 临时层一次性作答，建议落待审提案")
    @PostMapping("/ask")
    public SmartQueryAskRes ask(@RequestBody AskReq req) {
        if (req == null || FuncUtil.isEmpty(req.getAgentCode()) || FuncUtil.isEmpty(req.getQuestion())) {
            throw new NoticeException("agentCode 与 question 不能为空");
        }
        return smartQueryMaintainService.ask(req.getAgentCode(), req.getQuestion(), req.getChartMode());
    }

    /**
     * 维护问数流式版（SSE）：多轮 LLM 编排可达 1-2 分钟，同步接口前端只能长 loading；
     * 本端点逐阶段推 step 进度事件，收尾 done 事件携带与同步版同构的完整应答 JSON。
     * SseEmitter 不走 JSON 消息转换器，全局响应包装不影响本端点（同 chatbi /ask）。
     * 【已废弃·遗留保留】前端已切换提交+轮询链（/ask/submit + /ask/poll），本端点无前端调用方；
     * 与旧一次性 /ask 一样保留并存（验证期不删），歧义确认仅轮询链支持
     */
    @ApiOperation(value = "维护问数（SSE 流式：step 进度 + done 完整应答）")
    @RequestMapping(value = "/ask/stream", method = RequestMethod.POST, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AskReq req) {
        if (req == null || FuncUtil.isEmpty(req.getAgentCode()) || FuncUtil.isEmpty(req.getQuestion())) {
            throw new NoticeException("agentCode 与 question 不能为空");
        }
        // 超时 0 = 不限时，由编排完成/失败关闭连接
        SseEmitter emitter = new SseEmitter(0L);
        smartQueryMaintainService.askStream(req.getAgentCode(), req.getQuestion(), req.getChartMode(), emitter);
        return emitter;
    }

    /**
     * 维护问数（提交+轮询版，SSE 替代主通道）：多轮 LLM 编排可达 1-2 分钟，SSE 易被
     * 中间层（反代/杀软/浏览器插件）缓冲掐断致前端无进度长转圈；改为即刻返回票据 +
     * 后台编排 + /ask/poll 每 2s 增量轮询 step（阶段进度 + 工具循环日志，即 LLM 思考过程），
     * 同资产生成 progress 模式送达可靠；收尾 done 态 result 与同步版同构
     */
    @ApiOperation("维护问数提交：即刻返回 ticketId，编排后台执行，进度经 /ask/poll 轮询")
    @PostMapping("/ask/submit")
    public Map<String, Object> askSubmit(@RequestBody AskReq req) {
        if (req == null || FuncUtil.isEmpty(req.getAgentCode()) || FuncUtil.isEmpty(req.getQuestion())) {
            throw new NoticeException("agentCode 与 question 不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", smartQueryMaintainService.askSubmit(
                req.getAgentCode(), req.getQuestion(), req.getChartMode(), AccountContext.getDisplayName()));
        return result;
    }

    /** 高频轮询（前端 2s 一次）：ApiTrace(log=false) 静默访问日志降噪（同 generate/progress 口径） */
    @ApiTrace(log = false)
    @ApiOperation("维护问数票据增量轮询：steps + 终态（done 携带 result，error 携带 errorMessage）")
    @PostMapping("/ask/poll")
    public Map<String, Object> askPoll(@RequestBody TicketReq req) {
        if (req == null || FuncUtil.isEmpty(req.getTicketId())) {
            throw new NoticeException("ticketId 不能为空");
        }
        return smartQueryMaintainService.askPoll(req.getTicketId(), req.getOffset() == null ? 0 : req.getOffset());
    }

    @ApiOperation("停止维护问数：打取消标记，编排在下个进度点抛出终止")
    @PostMapping("/ask/cancel")
    public boolean askCancel(@RequestBody TicketReq req) {
        if (req == null || FuncUtil.isEmpty(req.getTicketId())) {
            throw new NoticeException("ticketId 不能为空");
        }
        return smartQueryMaintainService.askCancel(req.getTicketId());
    }

    /**
     * 歧义确认作答：编排线程阻塞在 askUser 工具等待时，前端轮询拿到 question 渲染选项卡，
     * 用户选择/输入后经本端点唤醒编排继续（answer 空=交由 AI 自选口径）
     */
    @ApiOperation("歧义确认作答：唤醒等待中的问数编排（answer 空=交由 AI 决定）")
    @PostMapping("/ask/answer")
    public boolean askAnswer(@RequestBody AnswerReq req) {
        if (req == null || FuncUtil.isEmpty(req.getTicketId())) {
            throw new NoticeException("ticketId 不能为空");
        }
        return smartQueryMaintainService.askAnswer(req.getTicketId(), req.getAnswer());
    }

    /**
     * 自主维护问数（agent 会话，计划 B3）：返回 sessionId，过程事件流/暂停/补语/停止
     * 经通用会话端点 /web/api/agent/session/*；阶段随工具调用推进（AgentStages 边执行边跳动）。
     * 断开策略由发起页面定义（测试页传 KEEP_RUNNING 供刷新重连；用户对话入口不传走默认断开即停）。
     * 旧一次性 /ask 与流式 /ask/stream 保留并存（验证期不删）
     */
    @ApiOperation("自主维护问数（agent 会话）：返回 sessionId，过程与控制经 /web/api/agent/session/*")
    @PostMapping("/agent/ask")
    public Map<String, Object> agentAsk(@RequestBody AskReq req) {
        if (req == null || FuncUtil.isEmpty(req.getAgentCode()) || FuncUtil.isEmpty(req.getQuestion())) {
            throw new NoticeException("agentCode 与 question 不能为空");
        }
        DetachPolicy detachPolicy = null;
        if (FuncUtil.isNotEmpty(req.getDetachPolicy())) {
            try {
                detachPolicy = DetachPolicy.valueOf(req.getDetachPolicy().trim());
            } catch (IllegalArgumentException e) {
                throw new NoticeException("detachPolicy 取值非法：" + req.getDetachPolicy());
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(MaintainQueryAgentDefinition.PAYLOAD_AGENT_CODE, req.getAgentCode());
        payload.put(MaintainQueryAgentDefinition.PAYLOAD_QUESTION, req.getQuestion());
        AgentSessionState state = agentSessionService.start(MaintainQueryAgentDefinition.AGENT_KEY,
                payload, AccountContext.getDisplayName(), detachPolicy);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", state.getSessionId());
        return result;
    }

    @ApiOperation("变更建议列表（按 Agent + 状态筛选）")
    @PostMapping("/proposals/list")
    public List<InsightAgentProposal> proposalsList(@RequestBody ProposalListReq req) {
        return smartQueryMaintainService.listProposals(req.getAgentCode(), req.getStatus());
    }

    @ApiOperation("各 Agent 待审提案数（管理页行内徽标 + 未处理提示）")
    @PostMapping("/proposals/pending-counts")
    public Map<String, Long> pendingCounts() {
        return smartQueryMaintainService.pendingCounts();
    }

    @ApiOperation("合并所选建议进草稿资产（合并后仍为草稿，需发布+刷新缓存生效）")
    @PostMapping("/proposals/merge")
    public int proposalsMerge(@RequestBody ProposalActionReq req) {
        return smartQueryMaintainService.merge(req.getIds());
    }

    @ApiOperation("驳回所选建议")
    @PostMapping("/proposals/reject")
    public int proposalsReject(@RequestBody ProposalActionReq req) {
        return smartQueryMaintainService.reject(req.getIds());
    }

    /** 维护问数请求体 */
    @Data
    public static class AskReq {
        private String agentCode;
        /** 自然语言问题 */
        private String question;
        /** 可选：图表模式覆盖（缺省由查询形态推断） */
        private String chartMode;
        /** 可选：断开策略覆盖（STOP_ON_DETACH/KEEP_RUNNING）；不传回落定义层默认，策略随页面场景定 */
        private String detachPolicy;
    }

    /** 票据操作请求体（轮询/取消） */
    @Data
    public static class TicketReq {
        private String ticketId;
        /** 已拉取的 step 偏移（从此起返），缺省 0 */
        private Integer offset;
    }

    /** 歧义确认作答请求体 */
    @Data
    public static class AnswerReq {
        private String ticketId;
        /** 作答内容（候选项文本或自由输入）；空=交由 AI 决定 */
        private String answer;
    }

    /** 建议列表请求体 */
    @Data
    public static class ProposalListReq {
        private String agentCode;
        /** 可选：0=待审 1=已合并 2=已驳回 */
        private String status;
    }

    /** 合并/驳回请求体 */
    @Data
    public static class ProposalActionReq {
        private List<Integer> ids;
    }
}
