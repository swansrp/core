package com.bidr.llm.flow.approval;

import lombok.Data;

/**
 * Title: ApprovalRecord
 * Description: 流程审批记录——流程运行到需人工把关的结点时挂起等待的存证：
 * 一条记录 = 一次审批请求，decision 为空即待审，决定后（APPROVE / REJECT）转为审计历史；
 * APPROVE 时由 {@link ApprovalService} 经 {@link com.bidr.llm.flow.FlowEngine#resume}
 * 从 nodeId 驱动流程续跑（runId 关联发起次执行，通常为执行轨迹 traceId）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class ApprovalRecord {

    /**
     * 记录标识（审批端点按它引用；提交时缺省由存储方生成）
     */
    private String recordId;

    /**
     * 流程标识（已注册的 flowKey）
     */
    private String flowKey;

    /**
     * 发起次执行标识（通常为执行轨迹 traceId，审批通过续跑时据此恢复上下文/归并轨迹）
     */
    private String runId;

    /**
     * 审批通过后从哪个结点续跑（{@link com.bidr.llm.flow.FlowEngine#resume} 的挂点）
     */
    private String nodeId;

    /**
     * 审批决定：APPROVE | REJECT；null = 待审
     */
    private String decision;

    /**
     * 提交人（发起审批请求的操作员）
     */
    private String operator;

    /**
     * 提交说明（为什么需要这次审批）
     */
    private String comment;

    /**
     * 审批人（决定时回填）
     */
    private String decidedBy;

    /**
     * 审批意见（决定时回填，与提交说明分开保存互不覆盖）
     */
    private String decisionComment;

    /**
     * 提交时间（epoch millis）
     */
    private Long createdAt;

    /**
     * 决定时间（epoch millis；待审为 null）
     */
    private Long decidedAt;
}
