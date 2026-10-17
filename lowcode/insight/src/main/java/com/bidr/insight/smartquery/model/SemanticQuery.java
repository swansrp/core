package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: SemanticQuery
 * Description: semantic_query 协议顶层对象（LLM 产物 → 校验 → SQL 生成的唯一入参）。
 * metric 查询：metrics+dimensions+filters/having/scope_filter/window/order_by/limit；
 * list 查询：entity+fields+filters/order_by/limit（SKILL.md §9）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SemanticQuery {

    /** metric / list，缺省 metric */
    private String queryType;

    /** 目标 Agent 标识（多语义层隔离）：缺省/default 走默认语义层与默认数据源 */
    private String agent;

    /** list 查询的实体英文名 */
    private String entity;

    /** 指标英文名列表 */
    private List<String> metrics;

    /** 维度英文名列表（分组/GROUP BY） */
    private List<String> dimensions;

    /** list 查询输出字段（实体字段名） */
    private List<String> fields;

    /** 前置过滤条件树（维度级） */
    private FilterNode filters;

    /** 后聚合条件树（指标级，仅 metric 查询） */
    private FilterNode having;

    /** 跨事实表范围过滤（半连接） */
    private ScopeFilter scopeFilter;

    /** 窗口排名（仅 metric 查询） */
    private WindowSpec window;

    /** 时间窗口 */
    private TimeSpec time;

    /** 排序项 */
    private List<OrderByItem> orderBy;

    /** 行数上限（解析期做正整数类型校验，非法时置 null 并由解析器报错） */
    private Integer limit;

    public String queryTypeOrDefault() {
        return queryType == null || queryType.isEmpty() ? "metric" : queryType;
    }
}
