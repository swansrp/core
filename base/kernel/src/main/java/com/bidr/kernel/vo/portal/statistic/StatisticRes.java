package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: StatisticRes
 * Description: Copyright: Copyright (c) 2025
 *
 * @author Sharp
 * @since 2025/4/23 09:06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticRes {

    public static final String NULL = "NULL";
    public static final String UNKNOWN = "未知";

    private String metricColumn;
    private String metric;
    private String metricLabel;
    private BigDecimal statistic;
    private List<StatisticRes> children;

    public StatisticRes(String metricColumn, String metric, String metricLabel, Object statistic) {
        this.metricColumn = metricColumn;
        this.metric = metric;
        if (FuncUtil.isEmpty(metricLabel) || NULL.equals(metricLabel)) {
            this.metricLabel = UNKNOWN;
        } else {
            this.metricLabel = metricLabel;
        }
        this.statistic = FuncUtil.isNotEmpty(statistic) ? new BigDecimal(statistic.toString()) : BigDecimal.ZERO;
        this.children = new ArrayList<>();
    }

    public StatisticRes(String metricColumn, String metric, String metricLabel) {
        this(metricColumn, metric, metricLabel, null);
    }

    public StatisticRes(String metricColumn, String metric, Object statistic) {
        this(metricColumn, metric, StringUtil.NULL, statistic);
    }
}
