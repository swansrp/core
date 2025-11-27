package com.bidr.forge.engine.driver.base;

import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.engine.DriverCapability;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.engine.builder.SqlBuilder;

import java.util.Map;

/**
 * Driver基础接口
 * 定义驱动的基本能力和元数据获取
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverBaseInf {

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
    Map<String, String> buildAliasMap(String portalName, Long roleId);

    /**
     * 获取SQL构建器
     * 由各个Driver实现类提供具体的SqlBuilder实例
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return SQL构建器实例
     */
    SqlBuilder getSqlBuilder(String portalName, Long roleId);

    /**
     * 获取JDBC连接服务
     * 由各个Driver实现类提供JdbcConnectService实例
     *
     * @return JDBC连接服务实例
     */
    JdbcConnectService getJdbcConnectService();

    /**
     * 获取数据源名称
     * 由各个Driver实现类提供数据源配置
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 数据源名称（为空则使用默认数据源）
     */
    String getDataSource(String portalName, Long roleId);
}
