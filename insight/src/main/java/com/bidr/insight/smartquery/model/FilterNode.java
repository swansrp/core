package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: FilterNode
 * Description: semantic_query 过滤条件树节点（filters/having/scope_filter.filters 共用结构）。
 * 组节点携带 operator+conditions；叶子节点携带 dimension+operator+value（filters 树）
 * 或 metric+operator+value（having 树）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterNode {

    /** 组节点逻辑符：AND/OR，缺省 AND */
    private String operator;

    /** 组节点子条件列表 */
    private List<FilterNode> conditions;

    /** 叶子条件引用的维度英文名（filters 树） */
    private String dimension;

    /** 叶子条件引用的指标英文名（having 树） */
    private String metric;

    /** 过滤值：单值（String/Number）或列表（in/not_in/between） */
    private Object value;

    public boolean isGroup() {
        return conditions != null;
    }

    public String operatorOrDefault() {
        return operator == null || operator.isEmpty() ? "AND" : operator;
    }
}
