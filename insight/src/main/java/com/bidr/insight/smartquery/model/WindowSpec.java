package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: WindowSpec
 * Description: semantic_query 窗口排名协议（window），按 partition_by 分组、
 * 组内按 order_by 排序取前 top_n 名（SKILL.md §9.5）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class WindowSpec {

    /** 分组维度列表，必须是 dimensions 的子集 */
    private List<String> partitionBy;

    /** 组内排序项，field 来自 metrics/dimensions */
    private List<OrderByItem> orderBy;

    /** 每组取前 N 名，正整数 */
    private Integer topN;
}
