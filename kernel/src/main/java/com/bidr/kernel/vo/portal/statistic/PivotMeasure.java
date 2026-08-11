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
    @ApiModelProperty("度量字段名")
    private String field;
    @ApiModelProperty("度量显示名")
    private String label;
    @ApiModelProperty("聚合方式 sum/count/avg")
    private String agg;
}
