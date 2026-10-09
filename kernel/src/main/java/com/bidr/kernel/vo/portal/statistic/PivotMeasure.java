package com.bidr.kernel.vo.portal.statistic;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: PivotMeasure
 * Description: 透视报表度量列定义
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/8/10
 */
@Data
public class PivotMeasure {
    @ApiModelProperty("度量字段名（ratio 时仅作输出别名，真实取数走分子/分母字段）")
    private String field;
    @ApiModelProperty("度量显示名")
    private String label;
    @ApiModelProperty("聚合方式 sum/count/countDistinct/avg/min/max/ratio")
    private String agg;
    @ApiModelProperty("比率聚合分子字段（agg=ratio 时必填）")
    private String numerator;
    @ApiModelProperty("比率聚合分母字段（agg=ratio 时必填）")
    private String denominator;
}
