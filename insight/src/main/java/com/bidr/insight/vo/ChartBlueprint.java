package com.bidr.insight.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ChartBlueprint
 * Description: 图表自造参数（ChartSpec.config）：LLM 按用户提问填充的语义插槽，
 * 前端编译成完整指标卡片配置（DashboardItem.config.indicator）后交给 ChartCard 渲染。
 * 与 indicatorId 二选一：复用已配置卡片走 indicatorId+patch，目录卡片无法表达时走本参数。
 *
 * 各图表类型的插槽要求：
 * - bar/line/ptLine/pie：dimensionField（有 values 的字段）+ metrics（field 空=计数），交叉分析加 secondDimensionField；
 * - metricsPie：无维度，metrics ≥2 且都要 field（扇区=指标字段）；
 * - rankingBar：groupByField（实体/文本字段）+ metrics 1 个 + 可选 topN/sortOrder；
 * - treeStackedBar：treeField（fieldType=tree/tree-multi）；
 * - comparisonBar：dateField（日期字段）+ metrics 1 个。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class ChartBlueprint {

    @ApiModelProperty(value = "图表标题（中文，面向业务用户）")
    private String title;

    @ApiModelProperty(value = "图表类型：bar/line/ptLine/pie/metricsPie/treeStackedBar/rankingBar/comparisonBar")
    private String chartType;

    @ApiModelProperty(value = "一级维度字段（bar/line/ptLine/pie 必填；字段目录中有 values 的字段）")
    private String dimensionField;

    @ApiModelProperty(value = "一级维度项（可选子集与顺序，label/value 取自字段 values；缺省=全量展开）")
    private List<SemanticField.SemanticValue> dimensionItems;

    @ApiModelProperty(value = "二级维度字段（可选，交叉分析）")
    private String secondDimensionField;

    @ApiModelProperty(value = "二级维度项（可选）")
    private List<SemanticField.SemanticValue> secondDimensionItems;

    @ApiModelProperty(value = "树维度字段（treeStackedBar 必填；fieldType=tree/tree-multi）")
    private String treeField;

    @ApiModelProperty(value = "统计指标（1~4 个）")
    private List<BlueprintMetric> metrics;

    @ApiModelProperty(value = "分组字段（rankingBar 必填；作为 X 轴取 Top-N 的实体/文本字段）")
    private String groupByField;

    @ApiModelProperty(value = "排行数量（rankingBar，默认 10）")
    private Integer topN;

    @ApiModelProperty(value = "排行排序（rankingBar）：asc-从小到大 / desc-从大到小（默认）")
    private String sortOrder;

    @ApiModelProperty(value = "周期日期字段（comparisonBar 必填；字段目录中的日期字段）")
    private String dateField;

    @ApiModelProperty(value = "卡片级筛选条件（与表格条件同构，编译进图表全局筛选）")
    private List<ConditionListNode> filters;

    @ApiModelProperty(value = "时间范围过滤（编译进图表全局筛选的介于条件）")
    private ChatBiTimeFilter timeFilter;

    /**
     * 统计指标插槽：field 为空=计数 count(1)，否则为字段目录中的数值类字段（求和）
     */
    @Data
    public static class BlueprintMetric {

        @ApiModelProperty(value = "指标显示名（缺省取字段显示名，计数类用“数量”）")
        private String name;

        @ApiModelProperty(value = "聚合字段（空=计数；metricsPie 必填）")
        private String field;
    }
}
