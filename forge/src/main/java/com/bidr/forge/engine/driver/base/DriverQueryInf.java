package com.bidr.forge.engine.driver.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.engine.builder.SqlBuilder;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver查询接口
 * 定义所有查询相关方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverQueryInf<VO> extends DriverBaseInf {

    /**
     * 分页查询
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 分页结果
     */
    Page<VO> queryPage(AdvancedQueryReq req, String portalName, Long roleId);

    /**
     * 列表查询（不分页）
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 查询结果列表
     */
    List<VO> queryList(AdvancedQueryReq req, String portalName, Long roleId);

    /**
     * 单条查询
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 单条结果
     */
    VO queryOne(AdvancedQueryReq req, String portalName, Long roleId);

    /**
     * 根据主键ID查询单条记录
     *
     * @param id         主键ID（单主键传具体值，联合主键传Map<String, Object>）
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 单条记录（未找到返回null）
     */
    Map<String, Object> selectById(Object id, String portalName, Long roleId);

    /**
     * 获取所有数据（用于树结构或导出）
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 所有数据列表
     */
    List<VO> getAllData(String portalName, Long roleId);

    // ========== 以下为默认查询实现（供子类复用） ==========

    /**
     * 执行分页查询（供 Matrix/Dataset 驱动复用）
     * <p>执行流程：</p>
     * <ol>
     *   <li>切换到指定数据源（如需要）</li>
     *   <li>构建 SELECT 和 COUNT SQL（一次性解析优化）</li>
     *   <li>查询总数</li>
     *   <li>当总数大于0时，查询分页数据</li>
     *   <li>恢复默认数据源</li>
     * </ol>
     *
     * @param req        高级查询请求，包含查询条件、分页参数等
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 分页结果，包含总数和当前页数据
     */
    default Page<Map<String, Object>> doQueryPage(AdvancedQueryReq req, String portalName, Long roleId) {
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        SqlBuilder builder = getSqlBuilder(portalName, roleId);
        JdbcConnectService jdbcConnectService = getJdbcConnectService();
        String dataSource = getDataSource(portalName, roleId);
        if (FuncUtil.isNotEmpty(dataSource)) {
            jdbcConnectService.switchDataSource(dataSource);
        }
        try {
            Map<String, Object> parameters = new HashMap<>();
            // 优化：一次解析生成 SELECT 和 COUNT
            SqlBuilder.SqlParts sqlParts = builder.buildSqlParts(req, aliasMap, parameters);

            Long total = jdbcConnectService.queryForObject(sqlParts.getCountSql(), parameters, Long.class);

            Page<Map<String, Object>> page = new Page<>(req.getCurrentPage(), req.getPageSize());
            page.setTotal(total == null ? 0 : total);
            if (page.getTotal() > 0) {
                List<Map<String, Object>> records = jdbcConnectService.query(sqlParts.getSelectSql(), parameters);
                page.setRecords(records);
            }
            return page;
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    /**
     * 执行列表查询（供 Matrix/Dataset 驱动复用）
     * <p>执行流程：</p>
     * <ol>
     *   <li>切换到指定数据源（如需要）</li>
     *   <li>复制查询请求并移除分页参数（实现全量查询）</li>
     *   <li>构建 SELECT SQL</li>
     *   <li>执行查询并返回结果列表</li>
     *   <li>恢复默认数据源</li>
     * </ol>
     *
     * @param req        高级查询请求，包含查询条件、排序等（分页参数将被忽略）
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 查询结果列表（不分页）
     */
    default List<Map<String, Object>> doQueryList(AdvancedQueryReq req, String portalName, Long roleId) {
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        SqlBuilder builder = getSqlBuilder(portalName, roleId);
        JdbcConnectService jdbcConnectService = getJdbcConnectService();
        String dataSource = getDataSource(portalName, roleId);
        if (FuncUtil.isNotEmpty(dataSource)) {
            jdbcConnectService.switchDataSource(dataSource);
        }
        try {
            Map<String, Object> parameters = new HashMap<>();

            // 不分页查询：复制请求并移除分页参数
            AdvancedQueryReq noPagingReq = new AdvancedQueryReq();
            ReflectionUtil.copyProperties(req, noPagingReq);
            noPagingReq.setCurrentPage(null);
            noPagingReq.setPageSize(null);

            String selectSql = builder.buildSelect(noPagingReq, aliasMap, parameters);
            return jdbcConnectService.query(selectSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    /**
     * 执行单条查询（供 Matrix/Dataset 驱动复用）
     * <p>执行流程：</p>
     * <ol>
     *   <li>强制设置分页参数为第1页、每页1条</li>
     *   <li>调用 doQueryList 查询</li>
     *   <li>返回第一条数据（如无数据则返回null）</li>
     * </ol>
     *
     * @param req        高级查询请求，包含查询条件等
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 单条数据（未找到返回null）
     */
    default Map<String, Object> doQueryOne(AdvancedQueryReq req, String portalName, Long roleId) {
        // 限制查询 1 条
        req.setCurrentPage(1L);
        req.setPageSize(1L);
        List<Map<String, Object>> list = doQueryList(req, portalName, roleId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 执行计数查询（供 Matrix/Dataset 驱动复用）
     * <p>执行流程：</p>
     * <ol>
     *   <li>切换到指定数据源（如需要）</li>
     *   <li>构建 COUNT SQL</li>
     *   <li>执行查询获取总数</li>
     *   <li>恢复默认数据源</li>
     * </ol>
     *
     * @param req        高级查询请求，包含查询条件等
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 符合条件的记录总数（查询失败返回0）
     */
    default Long doCount(AdvancedQueryReq req, String portalName, Long roleId) {
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        SqlBuilder builder = getSqlBuilder(portalName, roleId);
        JdbcConnectService jdbcConnectService = getJdbcConnectService();
        String dataSource = getDataSource(portalName, roleId);
        if (FuncUtil.isNotEmpty(dataSource)) {
            jdbcConnectService.switchDataSource(dataSource);
        }
        try {
            Map<String, Object> parameters = new HashMap<>();
            String countSql = builder.buildCount(req, aliasMap, parameters);
            Long result = jdbcConnectService.queryForObject(countSql, parameters, Long.class);
            return result == null ? 0L : result;
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }
}
