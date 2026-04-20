package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: MetricCondition
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 22:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricCondition {
    private String distinct = "0";
    private String count = "0";
    private String value;
    private String label;
    private AdvancedQuery condition;

    public MetricCondition(String value, String label, AdvancedQuery condition) {
        this.distinct = CommonConst.NO;
        this.count = CommonConst.NO;
        this.value = value;
        this.label = label;
        this.condition = condition;
    }
}