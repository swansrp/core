package com.bidr.kernel.jdbc;

import javax.sql.DataSource;

/**
 * Title: DynamicDataSourceResolver
 * Description: 动态数据源解析器。JdbcConnectService 切换数据源时，
 * 若名称未在 yml（spring.datasource.dynamic.datasource）静态定义，
 * 按注册顺序询问各解析器动态提供（如数据源管理库表配置）；
 * 静态定义的名称永远优先，解析器不会覆盖
 *
 * @author Sharp
 * @since 2026/8/19
 */
public interface DynamicDataSourceResolver {

    /**
     * 尝试为名称提供数据源
     *
     * @param name 数据源名称
     * @return 可提供时返回 DataSource（调用方负责注册进路由），无法提供返回 null
     */
    DataSource resolve(String name);

    /**
     * 判断数据源是否仍可用（如连接池已被销毁则返回 false）。
     * 约定：仅对实现方能确定已失效的数据源返回 false，不认识的数据源一律返回 true；
     * 调用方据此注销路由中残留的死池并重新解析（refresh 销毁旧池与懒注册竞态的自愈）
     *
     * @param dataSource 已注册进路由的数据源
     * @return false 表示已失效，需注销重建
     */
    default boolean isAlive(DataSource dataSource) {
        return true;
    }
}
