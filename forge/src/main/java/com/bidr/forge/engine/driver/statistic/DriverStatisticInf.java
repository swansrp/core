package com.bidr.forge.engine.driver.statistic;

/**
 * Driver统计统合接口
 * 继承所有统计子接口，提供完整的统计能力
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverStatisticInf extends 
        DriverStatisticCountInf, 
        DriverStatisticSummaryInf, 
        DriverStatisticMetricInf {
    // 统合所有统计能力
    // 具体实现由 MatrixDriver 和 DatasetDriver 提供
}
