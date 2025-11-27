package com.bidr.forge.engine.driver.inf;

import com.bidr.forge.engine.driver.base.DriverQueryInf;
import com.bidr.forge.engine.driver.statistic.DriverStatisticInf;

/**
 * Driver查询接口（第1层 - 只读层）
 * <p>提供只读查询能力，适用于Dataset等只读数据源</p>
 * 
 * <h3>接口层次：</h3>
 * <pre>
 * DriverQueryOnlyInf（第1层 - 只读）⬅ 当前接口
 *   └── DriverCrudInf（第2层 - 增删改）
 *         └── DriverTreeInf（第3层 - 树形结构）
 * </pre>
 * 
 * <h3>能力说明：</h3>
 * <ul>
 *   <li>查询：queryPage、queryList、queryOne、count、selectById、getAllData</li>
 *   <li>统计：generalCount、advancedCount、generalSummary、advancedSummary、generalStatistic、advancedStatistic</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>Dataset数据源（只读、多表关联）</li>
 *   <li>只读报表</li>
 *   <li>数据查询展示</li>
 * </ul>
 *
 * @param <VO> 返回的VO类型
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverQueryOnlyInf<VO> extends 
        DriverQueryInf<VO>,
        DriverStatisticInf {
    // 第1层：只提供查询和统计能力
    // Dataset驱动实现此接口
}
