package com.bidr.kernel.vo.portal;

import com.bidr.kernel.utils.FuncUtil;
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
    private String metric;
    private BigDecimal statistic;
    private List<StatisticRes> children;

    public StatisticRes(String metric, BigDecimal statistic) {
        this.metric = metric;
        this.statistic = FuncUtil.isNotEmpty(statistic) ? statistic : BigDecimal.ZERO;
        this.children = new ArrayList<>();
    }

    public StatisticRes(String metric) {
        this(metric, null);
    }
}
