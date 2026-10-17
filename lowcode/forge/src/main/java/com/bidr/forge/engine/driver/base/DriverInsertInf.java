package com.bidr.forge.engine.driver.base;

import java.util.List;

/**
 * Driver插入接口
 * 定义所有插入相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverInsertInf<VO> extends DriverBaseInf {

    /**
     * 插入数据
     *
     * @param data       数据对象
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int insert(VO data, String portalName, Long roleId);

    /**
     * 批量插入数据
     *
     * @param dataList   数据列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchInsert(List<VO> dataList, String portalName, Long roleId);
}
