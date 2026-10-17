package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: AdvancedPivotReq
 * Description: 透视报表聚合查询请求
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/8/10
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvancedPivotReq extends AdvancedQueryReq {
    @ApiModelProperty("行维度列 value=字段名 label=显示名")
    private List<KeyValueResVO> groupColumns;
    @ApiModelProperty("父表头列 value=列标识 label=表头文字 condition=列条件")
    private List<MetricCondition> pivotColumns;
    @ApiModelProperty("度量列")
    private List<PivotMeasure> measures;
}
