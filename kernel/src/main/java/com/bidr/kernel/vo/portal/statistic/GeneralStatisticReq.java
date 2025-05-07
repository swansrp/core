package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: GeneralStatisticReq
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/21 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GeneralStatisticReq extends QueryConditionReq implements StatisticReqInf {
    private List<Metric> metricColumn;
    private List<MetricCondition> metricCondition;
    private String majorCondition;
    private List<KeyValueResVO> statisticColumn;
    private Integer sort;
}
