package com.bidr.admin.controller.inf;

/**
 * Title: PortalSqlInf
 * Description: Portal SQL获取接口（按Portal名称维度）
 * <p>传统Portal每个配置对应独立Controller Bean，可直接通过
 * {@link com.bidr.kernel.controller.inf.AdminControllerInf#getPortalSql()}获取SQL；
 * 而动态Portal（Matrix/Dataset）所有配置共享同一个Bean，无法从Bean本身区分，
 * 需要通过Portal名称反查对应驱动来生成SQL，故由此类单独定义按名称获取的能力。</p>
 *
 * @author Sharp
 * @since 2026/08/08
 */
public interface PortalSqlInf {

    /**
     * 获取指定Portal的基础查询SQL
     *
     * @param portalName Portal名称
     * @return sql语句
     */
    String getPortalSql(String portalName);
}
