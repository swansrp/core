package com.bidr.forge.engine.driver.statistic;

import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.statistic.GeneralStatisticReq;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;

import java.util.List;

/**
 * Driver统计指标接口
 * 定义指标统计相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverStatisticMetricInf extends DriverStatisticBaseInf {
    /**
     * 指标统计（高级查询）
     *
     * @param req        高级查询条件
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 指标统计数据
     */
    List<StatisticRes> statistic(AdvancedStatisticReq req, String portalName, Long roleId);
}
