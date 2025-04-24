package com.bidr.kernel.vo.portal;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Title: StatisticRes
 * Description: Copyright: Copyright (c) 2025
 *
 * @author Sharp
 * @since 2025/4/23 09:06
 */
@Data
public class StatisticRes {
    private String metric;
    private BigDecimal statistic;
}
