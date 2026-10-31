package com.bidr.llm.flow;

import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.flow.approval.ApprovalDecisionReq;
import com.bidr.llm.flow.approval.ApprovalRecord;
import com.bidr.llm.flow.approval.ApprovalService;
import com.bidr.llm.flow.trace.FlowTrace;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: FlowOrchestratorController
 * Description: 工作流编排台端点（T4.1 工作流门面）——流程定义版本化与审批持久化的管理入口：
 * <ul>
 *     <li>GET  flows：已注册流程清单（编排台流程列表数据源）；</li>
 *     <li>GET  versions/{flowKey}：版本清单（升序；未启用版本化返回空清单）；</li>
 *     <li>GET  version/{flowKey}/{version}：指定版本 graph 回读（编辑/审计）；</li>
 *     <li>POST save-version：graph JSON 校验后落一个版本（缺省新版本号，传已有版本=覆盖），返回版本号；</li>
 *     <li>POST run/{flowKey}/{version}：按指定版本同步执行（与当前生效编排无关），返回轨迹 id 与 output；</li>
 *     <li>GET  approvals/{flowKey}：审批队列（待审 + 已决定历史）；</li>
 *     <li>POST approve/{recordId} / reject/{recordId}：审批决定，通过即经 FlowEngine.resume 驱动续跑；</li>
 *     <li>GET  trace/{flowKey}：执行轨迹列表（复用 {@link FlowTraceRecorder}，新→旧）。</li>
 * </ul>
 * 装配与鉴权口径同 agent 会话端点（/web/api 拦截链）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@RestController
@RequestMapping("/web/api/flow/orchestrator")
@RequiredArgsConstructor
public class FlowOrchestratorController {

    /**
     * 历史类清单（审批历史/轨迹列表）缺省条数
     */
    private static final int DEFAULT_LIMIT = 20;

    private final FlowManagerService flowManagerService;

    private final ApprovalService approvalService;

    private final FlowTraceRecorder traceRecorder;

    private final ObjectProvider<OperatorResolver> operatorResolver;

    /**
     * 已注册流程（封闭集来源：编排台流程列表与其余端点的 flowKey 校验共用）
     */
    private final List<FlowDefinitionProvider> flowProviders;

    /**
     * 版本清单（升序；未启用版本化=空清单，页面据此降级为无版本管理）
     */
    @GetMapping("/versions/{flowKey}")
    public List<Integer> versions(@PathVariable("flowKey") String flowKey) {
        return flowManagerService.flowVersions(flowKey);
    }

    /**
     * 已注册流程清单（flowKey 封闭集来源，编排台流程列表数据源）
     */
    @GetMapping("/flows")
    public List<Map<String, Object>> flows() {
        List<Map<String, Object>> flows = new ArrayList<>();
        for (FlowDefinitionProvider provider : flowProviders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flowKey", provider.flowKey());
            item.put("skillCode", provider.skillCode());
            item.put("displayName", provider.displayName());
            flows.add(item);
        }
        return flows;
    }

    /**
     * 指定版本详情（graph 回读给页面编辑/审计；未启用版本化或版本不存在报错）
     */
    @GetMapping("/version/{flowKey}/{version}")
    public FlowDetailRes versionDetail(@PathVariable("flowKey") String flowKey,
                                       @PathVariable("version") int version) {
        return flowManagerService.getFlowVersion(flowKey, version);
    }

    /**
     * 保存流程版本（graph JSON 结构校验后原样落库），返回 {"version": 落库版本号}
     */
    @PostMapping("/save-version")
    public Map<String, Object> saveVersion(@RequestBody FlowVersionSaveReq req) {
        int version = flowManagerService.saveFlowVersion(req);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("version", version);
        return res;
    }

    /**
     * 按指定版本同步执行流程：请求体为可选的入参变量表（question 等按名注入变量池）；
     * 返回 {"ok":true,"traceId":...,"output":{...}}，执行失败返回 {"ok":false,"error":...}
     */
    @PostMapping("/run/{flowKey}/{version}")
    public Map<String, Object> run(@PathVariable("flowKey") String flowKey,
                                   @PathVariable("version") int version,
                                   @RequestBody(required = false) Map<String, Object> inputs) {
        FlowContext context = new FlowContext(null);
        context.setOperator(operator());
        if (inputs != null) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            String traceId = flowManagerService.runFlowVersion(flowKey, version, context);
            res.put("ok", Boolean.TRUE);
            res.put("traceId", traceId);
            res.put("output", context.getOutput());
        } catch (RuntimeException e) {
            log.warn("编排台按版本运行流程失败, flowKey={}, version={}", flowKey, version, e);
            res.put("ok", Boolean.FALSE);
            res.put("error", e.getMessage() == null ? "流程执行失败" : e.getMessage());
        }
        return res;
    }

    /**
     * 审批队列：{"pending":[待审旧→新],"history":[已决定新→旧]}
     */
    @GetMapping("/approvals/{flowKey}")
    public Map<String, Object> approvals(@PathVariable("flowKey") String flowKey,
                                         @RequestParam(name = "limit", defaultValue = "20") int limit) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pending", approvalService.listPending(flowKey));
        res.put("history", approvalService.history(flowKey, limit > 0 ? limit : DEFAULT_LIMIT));
        return res;
    }

    /**
     * 审批通过：决定落库并驱动流程从挂起结点续跑，返回 {"record":...,"resumed":true|false}
     */
    @PostMapping("/approve/{recordId}")
    public Map<String, Object> approve(@PathVariable("recordId") String recordId,
                                       @RequestBody(required = false) ApprovalDecisionReq req) {
        return decide(recordId, ApprovalService.DECISION_APPROVE, req);
    }

    /**
     * 审批驳回：仅落决定不续跑
     */
    @PostMapping("/reject/{recordId}")
    public Map<String, Object> reject(@PathVariable("recordId") String recordId,
                                      @RequestBody(required = false) ApprovalDecisionReq req) {
        return decide(recordId, ApprovalService.DECISION_REJECT, req);
    }

    /**
     * 执行轨迹列表（新→旧；按当前访问人隔离——与 agent 会话页同口径，轨迹正文走既有
     * /web/api/agent/flow/trace/{traceId} 详情端点）
     */
    @GetMapping("/trace/{flowKey}")
    public List<FlowTrace.TraceRecord> trace(@PathVariable("flowKey") String flowKey,
                                             @RequestParam(name = "limit", defaultValue = "20") int limit) {
        List<FlowTrace.TraceRecord> traces = traceRecorder.listTraces(flowKey, operator());
        int cap = limit > 0 ? limit : DEFAULT_LIMIT;
        return traces.size() <= cap ? traces : traces.subList(0, cap);
    }

    private Map<String, Object> decide(String recordId, String decision, ApprovalDecisionReq req) {
        String operator = req != null ? req.getOperator() : null;
        if (!StringUtils.hasText(operator)) {
            operator = operator();
        }
        ApprovalRecord record = approvalService.approveRecord(recordId, decision, operator,
                req != null ? req.getComment() : null);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("record", record);
        res.put("resumed", ApprovalService.DECISION_APPROVE.equals(record.getDecision()));
        return res;
    }

    /**
     * 当前访问人（SPI 缺席/失败回落 null，由各存储与轨迹按 anonymous 兜底）
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
}
