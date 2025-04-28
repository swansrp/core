package com.bidr.kernel.vo.portal;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Title: GeneralStatisticReq
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/21 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GeneralStatisticReq extends QueryConditionReq {
    private List<String> metricColumn;
    private Map<String, AdvancedQuery> metricCondition;
    private String statisticColumn;
    private Integer sort;
}
