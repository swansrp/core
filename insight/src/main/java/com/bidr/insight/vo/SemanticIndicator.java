package com.bidr.insight.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: SemanticIndicator
 * Description: 语义目录中的指标卡片条目（来自 sys_portal_dashboard_statistic）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SemanticIndicator {

    @ApiModelProperty(value = "指标卡片id（图表生成物 ChartSpec.indicatorId 引用值）")
    private Long id;

    @ApiModelProperty(value = "卡片标题")
    private String title;

    @ApiModelProperty(value = "副标题")
    private String subTitle;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "可用维度项名称（indicator.firstDimension.indicatorItems[].itemName）")
    private List<String> dimensions;

    @ApiModelProperty(value = "可用统计指标名称（indicator.dataMetrics[].dataName）")
    private List<String> metrics;

    @ApiModelProperty(value = "默认图表类型（indicator.dataMetrics[].chartType 去重）")
    private List<String> chartTypes;
}
