package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.vo.portal.AdvancedQuery;
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
    @ApiModelProperty("列输出键：同字段多条件各出一列时用于区分（缺省=field；只作列别名后半段，不参与取数）")
    private String key;
    @ApiModelProperty("度量自身条件：与透视列条件按 AND 叠加，命中条件才计入该单元格")
    private AdvancedQuery condition;
    @ApiModelProperty("度量显示名")
    private String label;
    @ApiModelProperty("聚合方式 sum/count/countDistinct/avg/min/max/ratio")
    private String agg;
    @ApiModelProperty("比率聚合分子字段（agg=ratio 时必填）")
    private String numerator;
    @ApiModelProperty("比率聚合分母字段（agg=ratio 时必填）")
    private String denominator;
    @ApiModelProperty("比率分子腿聚合方式（agg=ratio 时可选，不配=sum）")
    private String numeratorAgg;
    @ApiModelProperty("比率分母腿聚合方式（agg=ratio 时可选，不配=sum；组内恒定的目标值配 max，防分母随行数放大）")
    private String denominatorAgg;
}
