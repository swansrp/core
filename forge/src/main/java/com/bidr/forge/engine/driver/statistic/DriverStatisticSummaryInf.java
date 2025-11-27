package com.bidr.forge.engine.driver.statistic;

import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import com.bidr.kernel.vo.portal.statistic.GeneralSummaryReq;

import java.util.Map;

/**
 * Driver统计汇总接口
 * 定义汇总统计相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverStatisticSummaryInf extends DriverStatisticBaseInf {

    /**
     * 汇总（高级查询）
     *
     * @param req        高级查询条件
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 汇总数据
     */
    Map<String, Object> summary(AdvancedSummaryReq req, String portalName, Long roleId);
}
