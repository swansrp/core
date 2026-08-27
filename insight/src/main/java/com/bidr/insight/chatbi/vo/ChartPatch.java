package com.bidr.insight.chatbi.vo;

import com.bidr.insight.smartquery.vo.TimeFilter;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ChartPatch
 * Description: 图表补丁——仅允许调整图表类型、维度/指标可见性与时间范围，
 * 未给出的字段沿用指标卡片默认配置
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChartPatch {

    @ApiModelProperty(value = "图表类型：bar-柱状图/line-折线图/ptLine-平滑折线/pie-饼图/metricsPie-指标饼图/treeStackedBar-树形堆叠/rankingBar-排行榜/comparisonBar-同比环比")
    private String chartType;

    @ApiModelProperty(value = "可见一级维度项名称（indicator 配置 firstDimension.indicatorItems[].itemName 子集）")
    private List<String> visibleFirstDimensions;

    @ApiModelProperty(value = "可见二级维度项名称（indicator 配置 secondDimension.indicatorItems[].itemName 子集）")
    private List<String> visibleSecondDimensions;

    @ApiModelProperty(value = "可见统计指标名称（indicator 配置 dataMetrics[].dataName 子集，空则全部）")
    private List<String> visibleMetrics;

    @ApiModelProperty(value = "时间范围过滤（property 必须是字段目录中的日期字段）")
    private TimeFilter timeFilter;
}
