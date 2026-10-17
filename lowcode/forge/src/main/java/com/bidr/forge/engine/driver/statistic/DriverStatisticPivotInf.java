package com.bidr.forge.engine.driver.statistic;

import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;

import java.util.List;
import java.util.Map;

/**
 * Driver透视聚合接口
 * 定义透视报表聚合查询能力（Matrix/Dataset 动态Portal）
 * 按行维度列 GROUP BY，每个父表头列 × 每个度量列生成一个条件聚合表达式
 *
 * @author Sharp
 * @since 2026-09-14
 */
public interface DriverStatisticPivotInf extends DriverStatisticBaseInf {

    /**
     * 透视聚合查询
     *
     * @param req        透视请求（groupColumns/pivotColumns/measures）
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 聚合后的平铺行数据，列别名为 ${父表头列标识}__${度量字段}
     */
    List<Map<String, Object>> pivot(AdvancedPivotReq req, String portalName, Long roleId);
}
