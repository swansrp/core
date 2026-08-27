package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * Title: ScopeFilter
 * Description: semantic_query 跨事实表范围过滤协议（scope_filter，SKILL.md §6.7）：
 * 主查询指标与范围指标来自不同事实表时，把范围条件压成 IN 半连接子查询，
 * 避免两张不同粒度的事实表直接 JOIN 造成双向扇出
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ScopeFilter {

    /** 范围指标（atomic，且与主查询指标不同事实表） */
    private String metric;

    /** 关联维度（主查询与子查询的半连接键） */
    private String dimension;

    /** in / not_in，缺省 in */
    private String op;

    /** 子查询内的前置过滤（维度须被范围指标支持） */
    private FilterNode filters;

    /** 子查询内的后聚合条件（指标须与范围指标同源表） */
    private FilterNode having;
}
