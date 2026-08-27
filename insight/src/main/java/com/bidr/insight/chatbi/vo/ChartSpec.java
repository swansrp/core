package com.bidr.insight.chatbi.vo;

import com.bidr.insight.smartquery.vo.ChartBlueprint;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChartSpec
 * Description: 单个图表生成物，两种形态二选一：
 * 基于已有指标卡片做补丁式调整（indicatorId+patch），或按语义插槽自造完整图表（config）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChartSpec {

    @ApiModelProperty(value = "指标卡片id（复用已配置卡片时必填；语义目录 indicators[].id，即 sys_portal_dashboard_statistic.id，与 config 二选一）")
    private Long indicatorId;

    @ApiModelProperty(value = "选择/生成该图表的原因（中文一句话）")
    private String reason;

    @ApiModelProperty(value = "对指标卡片默认配置的补丁（indicatorId 形态适用）")
    private ChartPatch patch;

    @ApiModelProperty(value = "图表自造参数（目录卡片无法表达时使用，与 indicatorId 二选一）")
    private ChartBlueprint config;
}
