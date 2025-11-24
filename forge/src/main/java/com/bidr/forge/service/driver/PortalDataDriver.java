package com.bidr.forge.service.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.List;
import java.util.Map;

/**
 * Portal数据驱动接口
 * 提供基于JDBC的数据访问能力，支持Matrix（单表）和Dataset（多表联接）两种模式
 *
 * @author Sharp
 * @since 2025-11-24
 */
public interface PortalDataDriver<VO> {

    /**
     * 获取驱动能力声明
     *
     * @return 驱动能力
     */
    DriverCapability getCapability();

    /**
     * 获取数据模式
     *
     * @return 数据模式
     */
    PortalDataMode getDataMode();

    /**
     * 构建别名映射表
     * 将VO字段名映射到SQL列名或表达式
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 字段别名映射 (VO字段名 -> SQL列名/表达式)
     */
    Map<String, String> buildAliasMap(String portalName, String roleId);

    /**
     * 分页查询
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 分页结果
     */
    Page<VO> queryPage(AdvancedQueryReq req, String portalName, String roleId);

    /**
     * 列表查询（不分页）
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 查询结果列表
     */
    List<VO> queryList(AdvancedQueryReq req, String portalName, String roleId);

    /**
     * 单条查询
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 单条结果
     */
    VO queryOne(AdvancedQueryReq req, String portalName, String roleId);

    /**
     * 查询总数
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 总数
     */
    Long count(AdvancedQueryReq req, String portalName, String roleId);

    /**
     * 获取所有数据（用于树结构或导出）
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 所有数据列表
     */
    List<VO> getAllData(String portalName, String roleId);

    /**
     * 插入数据
     *
     * @param data       数据对象
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int insert(VO data, String portalName, String roleId);

    /**
     * 批量插入数据
     *
     * @param dataList   数据列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchInsert(List<VO> dataList, String portalName, String roleId);

    /**
     * 更新数据
     *
     * @param data       数据对象
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int update(VO data, String portalName, String roleId);

    /**
     * 批量更新数据
     *
     * @param dataList   数据列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchUpdate(List<VO> dataList, String portalName, String roleId);

    /**
     * 删除数据
     *
     * @param id         主键ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int delete(Object id, String portalName, String roleId);

    /**
     * 批量删除数据
     *
     * @param ids        主键ID列表
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 影响行数
     */
    int batchDelete(List<Object> ids, String portalName, String roleId);
}
