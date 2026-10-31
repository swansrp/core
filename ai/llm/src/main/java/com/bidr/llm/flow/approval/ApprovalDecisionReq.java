package com.bidr.llm.flow.approval;

import lombok.Data;

/**
 * Title: ApprovalDecisionReq
 * Description: 审批决定请求体——审批动作（通过/驳回）的入参；operator 缺省回落"审批台"，
 * comment 为审批意见（与提交说明分开存放）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class ApprovalDecisionReq {

    /**
     * 审批人（空则记"审批台"）
     */
    private String operator;

    /**
     * 审批意见
     */
    private String comment;
}
