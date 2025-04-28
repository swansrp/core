package com.bidr.kernel.vo.portal;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Title: AdvancedQuery
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 09:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvancedStatisticReq extends AdvancedQueryReq {
    private List<String> metricColumn;
    private Map<String, AdvancedQuery> metricCondition;
    private String statisticColumn;
    private Integer sort;
}

