package com.bidr.forge.engine.driver.base;

import java.util.List;

/**
 * Driver删除接口
 * 定义所有删除相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverDeleteInf<VO> extends DriverBaseInf {

    /**
     * 删除数据
     *
     * @param id         主键ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int delete(Object id, String portalName, Long roleId);

    /**
     * 批量删除数据
     *
     * @param ids        主键ID列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchDelete(List<Object> ids, String portalName, Long roleId);
}
