package com.bidr.kernel.vo.portal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
