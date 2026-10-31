package com.bidr.llm.flow.approval;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import com.bidr.llm.flow.trace.FlowTrace;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Title: ApprovalService
 * Description: 流程审批服务——审批记录的提交/决定/查询入口：
 * <ul>
 *     <li>submit：业务在需人工把关的挂起点创建待审记录（recordId 由存储方生成）；</li>
 *     <li>approveRecord：决定（APPROVE/REJECT）并回填审批人与意见落库；
 *         APPROVE 时经 {@link FlowEngine#resume}(nodeId, context) 驱动流程从挂起结点续跑——
 *         续跑上下文最小构造：graph 取当前生效编排，审批决定/意见注入变量池，
 *         runId 命中执行轨迹时恢复 question 与发起人并沿用同一 traceId 归并轨迹
 *         （Redis 未接入/轨迹过期时静默降级为无轨迹续跑，执行不受影响）；</li>
 *     <li>listPending / history：审批队列（旧→新）与已决定历史（新→旧）。</li>
 * </ul>
 * 决定回填先落库再续跑：续跑失败不影响决定生效（记录已进历史，错误如实上抛给操作员）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Service
public class ApprovalService {

    /**
     * 决定取值：通过（驱动续跑）
     */
    public static final String DECISION_APPROVE = "APPROVE";

    /**
     * 决定取值：驳回（不续跑）
     */
    public static final String DECISION_REJECT = "REJECT";

    /**
     * 审批意见注入变量池的变量名（续跑后结点模板/条件边可引用）
     */
    public static final String VAR_APPROVAL_COMMENT = "approvalComment";

    /**
     * 审批决定注入变量池的变量名
     */
    public static final String VAR_APPROVAL_DECISION = "approvalDecision";

    private final ObjectProvider<ApprovalRecordStore> storeProvider;

    private final FlowEngine flowEngine;

    private final FlowTraceRecorder traceRecorder;

    public ApprovalService(ObjectProvider<ApprovalRecordStore> storeProvider,
                           FlowEngine flowEngine,
                           FlowTraceRecorder traceRecorder) {
        this.storeProvider = storeProvider;
        this.flowEngine = flowEngine;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 提交待审记录（业务在流程挂起点调用；createdAt 由存储方补）
     */
    public ApprovalRecord submit(String flowKey, String runId, String nodeId,
                                 String operator, String comment) {
        if (!StringUtils.hasText(flowKey)) {
            throw new IllegalArgumentException("流程标识不能为空");
        }
        if (!StringUtils.hasText(nodeId)) {
            throw new IllegalArgumentException("续跑结点不能为空");
        }
        ApprovalRecord record = new ApprovalRecord();
        record.setFlowKey(flowKey);
        record.setRunId(runId);
        record.setNodeId(nodeId);
        record.setOperator(operator);
        record.setComment(comment);
        record.setCreatedAt(System.currentTimeMillis());
        requireStore().save(record);
        return record;
    }

    /**
     * 审批决定：APPROVE 驱动 {@link FlowEngine#resume} 续跑，REJECT 仅落决定；
     * 同一记录不可重复决定（已决定的按审计历史查）
     */
    public ApprovalRecord approveRecord(String recordId, String decision,
                                        String decidedBy, String decisionComment) {
        if (!DECISION_APPROVE.equals(decision) && !DECISION_REJECT.equals(decision)) {
            throw new IllegalArgumentException("非法的审批决定: " + decision + "（仅支持 APPROVE/REJECT）");
        }
        ApprovalRecordStore store = requireStore();
        ApprovalRecord record = store.find(recordId);
        if (record == null) {
            throw new ServiceException("审批记录不存在: " + recordId);
        }
        if (record.getDecision() != null) {
            throw new ServiceException("审批记录已决定: " + recordId + " - " + record.getDecision());
        }
        record.setDecision(decision);
        record.setDecidedBy(StringUtils.hasText(decidedBy) ? decidedBy.trim() : "审批台");
        record.setDecisionComment(decisionComment);
        record.setDecidedAt(System.currentTimeMillis());
        store.save(record);
        if (DECISION_APPROVE.equals(decision)) {
            resumeFlow(record);
        }
        return record;
    }

    /**
     * 待审队列（旧→新，先进先出）；flowKey 为空返回全部流程的待审
     */
    public List<ApprovalRecord> listPending(String flowKey) {
        return requireStore().listPending(StringUtils.hasText(flowKey) ? flowKey.trim() : null);
    }

    /**
     * 已决定历史（新→旧，截断 limit 条）
     */
    public List<ApprovalRecord> history(String flowKey, int limit) {
        return requireStore().history(StringUtils.hasText(flowKey) ? flowKey.trim() : null, limit);
    }

    /**
     * 驱动续跑：graph 取当前生效编排（版本化记录不可变，续跑语义跟随现役定义）；
     * 决定与意见注入变量池；runId 命中轨迹时沿用同一 traceId（续跑结点事件归并进原轨迹）。
     * 续跑异常如实上抛（决定已落库，操作员可见失败原因）
     */
    private void resumeFlow(ApprovalRecord record) {
        FlowContext context = new FlowContext(null);
        context.setGraph(flowEngine.loadFlow(record.getFlowKey()).graph);
        context.setOperator(record.getDecidedBy());
        context.setVariable(VAR_APPROVAL_DECISION, record.getDecision());
        context.setVariable(VAR_APPROVAL_COMMENT, record.getDecisionComment());
        restoreFromTrace(record, context);
        flowEngine.resume(record.getNodeId(), context);
    }

    /**
     * runId = 执行轨迹 traceId 时恢复发起次上下文：question 回填变量池（后续模板可引用）、
     * 发起人回填访问人（审批人未被记录时）、traceId 沿用（事件归并同一条轨迹）；
     * 轨迹缺失（未接 Redis/已过期）静默跳过——审批续跑不依赖轨迹
     */
    private void restoreFromTrace(ApprovalRecord record, FlowContext context) {
        if (traceRecorder == null || !StringUtils.hasText(record.getRunId())) {
            return;
        }
        FlowTrace.TraceRecord trace = traceRecorder.getTrace(record.getRunId());
        if (trace == null) {
            log.info("审批续跑未命中执行轨迹（不阻断续跑）, flowKey={}, runId={}", record.getFlowKey(), record.getRunId());
            return;
        }
        context.setTraceId(record.getRunId());
        if (StringUtils.hasText(trace.getQuestion())) {
            context.setVariable("question", trace.getQuestion());
        }
        if (!StringUtils.hasText(context.getOperator()) && StringUtils.hasText(trace.getOperator())) {
            context.setOperator(trace.getOperator());
        }
    }

    private ApprovalRecordStore requireStore() {
        ApprovalRecordStore store = storeProvider.getIfAvailable();
        if (store == null) {
            throw new ServiceException("应用未接入审批记录持久化（ApprovalRecordStore），审批队列不可用");
        }
        return store;
    }
}
