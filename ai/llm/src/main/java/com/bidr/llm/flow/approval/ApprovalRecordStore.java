package com.bidr.llm.flow.approval;

import java.util.List;

/**
 * Title: ApprovalRecordStore
 * Description: 审批记录持久化 SPI——业务模块提供存储实现（如落关系表，多实例共享审批队列）；
 * 框架自带 {@link InMemoryApprovalRecordStore}（类路径无 core/redis 时的默认，单实例适用）。
 * save 以 recordId 为幂等键（无则插入有则更新），decision 为空即待审。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public interface ApprovalRecordStore {

    /**
     * 保存审批记录（recordId 为空时生成；同 id 再存 = 更新，审批决定回填走同一条路）
     */
    void save(ApprovalRecord record);

    /**
     * 按 recordId 读单条；不存在返回 null
     */
    ApprovalRecord find(String recordId);

    /**
     * 待审清单（decision 为空，按提交时间旧→新，审批先进先出）；flowKey 为空返回全部流程的待审
     */
    List<ApprovalRecord> listPending(String flowKey);

    /**
     * 已决定历史（decision 非空，按提交时间新→旧，截断 limit 条；limit<=0 由实现方定兜底）
     */
    List<ApprovalRecord> history(String flowKey, int limit);
}
