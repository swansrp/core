package com.bidr.forge.engine.driver.statistic;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;

/**
 * Driver统计计数接口
 * 定义统计个数相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverStatisticCountInf extends DriverStatisticBaseInf {

    /**
     * 统计个数（高级查询）
     *
     * @param req        高级查询条件
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 统计个数
     */
    Long count(AdvancedQueryReq req, String portalName, Long roleId);
}
