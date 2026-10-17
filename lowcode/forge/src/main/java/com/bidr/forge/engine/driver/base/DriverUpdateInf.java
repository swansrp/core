package com.bidr.forge.engine.driver.base;

import java.util.List;

/**
 * Driver更新接口
 * 定义所有更新相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverUpdateInf<VO> extends DriverBaseInf {

    /**
     * 更新数据
     *
     * @param data       数据对象
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int update(VO data, String portalName, Long roleId);

    /**
     * 批量更新数据
     *
     * @param dataList   数据列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchUpdate(List<VO> dataList, String portalName, Long roleId);
}
