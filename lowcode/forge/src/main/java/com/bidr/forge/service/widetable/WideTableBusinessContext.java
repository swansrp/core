package com.bidr.forge.service.widetable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 宽表业务上下文（由业务层提供）
 *
 * @author sharp
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WideTableBusinessContext {
    /**
     * 填报历史 ID (FormDataHistory.id)
     */
    private String historyId;

    /**
     * 表单 ID (FormDataHistory.formId)
     */
    private String formId;

    /**
     * 企业 ID
     */
    private Long enterpriseId;

    /**
     * 企业名称
     */
    private String enterpriseName;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 批号
     */
    private String versionNo;

    /**
     * 提交时间 (毫秒)
     */
    private Long submittedAt;

    /**
     * 审批状态
     */
    private String status;

    /**
     * 根据 contextKey 获取上下文值
     * <p>
     * contextKey 与 WideTableFixedColumn.contextKey 对应，
     * 用于在数据收集时动态获取固定列的值。
     *
     * @param contextKey 上下文键（如 enterpriseName, submittedAt, status）
     * @return 对应的字段值，未匹配返回 null
     */
    public Object getContextValue(String contextKey) {
        if (contextKey == null) return null;
        switch (contextKey) {
            case "historyId":     return historyId;
            case "formId":        return formId;
            case "enterpriseId":  return enterpriseId;
            case "enterpriseName":return enterpriseName;
            case "productId":     return productId;
            case "productName":   return productName;
            case "versionNo":     return versionNo;
            case "submittedAt":   return submittedAt;
            case "status":        return status;
            default: return null;
        }
    }
}
