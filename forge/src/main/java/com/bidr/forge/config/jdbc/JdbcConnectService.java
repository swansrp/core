package com.bidr.forge.config.jdbc;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Title: JdbcConnectService
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 11:12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcConnectService {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * 切换数据源
     *
     * @param dataSourceName 数据源名称
     */
    public void switchDataSource(String dataSourceName) {
        DynamicDataSourceContextHolder.push(dataSourceName);
    }

    /**
     * 获取当前数据源名称
     *
     * @return 当前数据源名称
     */
    public String getCurrentDataSourceName() {
        return DynamicDataSourceContextHolder.peek();
    }

    /**
     * 重置为默认数据源
     */
    public void resetToDefaultDataSource() {
        DynamicDataSourceContextHolder.poll();
    }

    /**
     * 执行查询SQL语句，返回单个结果
     * 推荐使用命名参数版本 executeNamedQueryForObject
     *
     * @param sql        SQL语句
     * @param parameters 命名参数映射
     * @param column     列名
     * @param clazz      结果类型
     * @return 查询结果
     */
    public <T> T queryObject(String sql, Map<String, Object> parameters, String column, Class<T> clazz) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            @SuppressWarnings("unchecked")
            T result = (T) row.get(column);
            log.trace("<== 查询结果: {}", result);
            return result;
        } catch (Exception e) {
            log.trace("<== 查询结果: null ({})", e.getMessage());
            return null;
        }
    }

    /**
     * 执行查询SQL语句，返回单个结果
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @return 查询结果
     */
    public Map<String, Object> executeQueryOne(String sql, Map<String, Object> parameters) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            log.trace("<== 查询单行结果: {}", row);
            return row;
        } catch (Exception e) {
            log.trace("<== 查询单行结果: null ({})", e.getMessage());
            return null;
        }
    }

    /**
     * 执行查询SQL语句，返回结果列表
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @return 查询结果列表
     */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> parameters) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        List<Map<String, Object>> result = namedParameterJdbcTemplate.queryForList(sql, parameters);
        log.trace("<== 查询结果行数: {}", result.size());
        return result;
    }

    /**
     * 执行更新SQL语句（INSERT, UPDATE, DELETE）
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @return 影响的行数
     */
    public int executeUpdate(String sql, Map<String, Object> parameters) {
        log.trace("==> 更新SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        int affectedRows = namedParameterJdbcTemplate.update(sql, parameters);
        log.trace("<== 影响行数: {}", affectedRows);
        return affectedRows;
    }

    /**
     * 执行SQL语句，返回结果集
     * 注意：不推荐使用，建议使用 executeQuery 或 executeNamedQuery
     *
     * @param sql SQL语句（不支持命名参数）
     * @return ResultSet结果集
     * @deprecated 建议使用 executeQuery 或 executeNamedQuery
     */
    @Deprecated
    public ResultSet executeQueryResultSet(String sql) {
        log.trace("==> 查询SQL: {}", sql);
        try {
            Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            return ps.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException("执行查询失败: " + sql, e);
        }
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 回滚事务
     *
     * @param conn 数据库连接
     * @throws SQLException SQL异常
     */
    public void rollbackTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.rollback();
            conn.setAutoCommit(true);
        }
    }

    /**
     * 关闭连接
     *
     * @param conn 数据库连接
     */
    public void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * 执行批量SQL语句（不带参数）
     * 注意：建议使用 executeNamedBatchUpdate 代替
     *
     * @param sqlList SQL语句列表
     * @return 每条SQL语句影响的行数
     * @deprecated 建议使用 executeNamedBatchUpdate
     */
    @Deprecated
    public int[] executeBatch(List<String> sqlList) {
        log.trace("==> 批量SQL, 总数: {}", sqlList.size());
        sqlList.forEach(sql -> log.trace("  批量SQL: {}", sql));
        try (Connection conn = beginTransaction();
             Statement stmt = conn.createStatement()) {
            for (String sql : sqlList) {
                stmt.addBatch(sql);
            }
            int[] result = stmt.executeBatch();
            commitTransaction(conn);
            int totalAffected = 0;
            for (int affected : result) {
                totalAffected += affected;
            }
            log.trace("<== 批量执行完成, 总影响行数: {}", totalAffected);
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("执行批量SQL失败", e);
        }
    }

    /**
     * 开启事务
     *
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    public Connection beginTransaction() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        return conn;
    }

    /**
     * 提交事务
     *
     * @param conn 数据库连接
     * @throws SQLException SQL异常
     */
    public void commitTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    // ==================== 命名参数化 SQL 方法（推荐使用）====================

    /**
     * 执行命名参数化查询SQL，返回结果列表
     *
     * @param sql        命名参数化SQL语句（如: SELECT * FROM table WHERE id = :id）
     * @param parameters 命名参数映射
     * @return 查询结果列表
     */
    public List<Map<String, Object>> query(String sql, Map<String, Object> parameters) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        List<Map<String, Object>> result = namedParameterJdbcTemplate.queryForList(sql, parameters);
        log.trace("<== 查询结果行数: {}", result.size());
        return result;
    }

    /**
     * 执行命名参数化查询SQL，返回单行结果
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @return 查询结果（单行）
     */
    public Map<String, Object> queryOne(String sql, Map<String, Object> parameters) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            log.trace("<== 查询单行结果: {}", row);
            return row;
        } catch (Exception e) {
            log.trace("<== 查询单行结果: null ({})", e.getMessage());
            return null;
        }
    }

    /**
     * 执行命名参数化查询SQL，返回单个对象
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @param clazz      结果类型
     * @return 查询结果
     */
    public <T> T queryForObject(String sql, Map<String, Object> parameters, Class<T> clazz) {
        log.trace("==> 查询SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        try {
            T result = namedParameterJdbcTemplate.queryForObject(sql, parameters, clazz);
            log.trace("<== 查询结果: {}", result);
            return result;
        } catch (Exception e) {
            log.trace("<== 查询结果: null ({})", e.getMessage());
            return null;
        }
    }

    /**
     * 执行命名参数化更新SQL（INSERT, UPDATE, DELETE）
     *
     * @param sql        命名参数化SQL语句
     * @param parameters 命名参数映射
     * @return 影响的行数
     */
    public int update(String sql, Map<String, Object> parameters) {
        log.trace("==> 更新SQL: {}", sql);
        log.trace("==> 参数: {}", parameters);
        int affectedRows = namedParameterJdbcTemplate.update(sql, parameters);
        log.trace("<== 影响行数: {}", affectedRows);
        return affectedRows;
    }

    /**
     * 执行批量命名参数化更新SQL
     *
     * @param sql            命名参数化SQL语句
     * @param parametersList 命名参数映射列表
     * @return 每条SQL影响的行数
     */
    public int[] batchUpdate(String sql, List<Map<String, Object>> parametersList) {
        log.trace("==> 批量更新SQL: {}", sql);
        log.trace("==> 批量数量: {}", parametersList.size());
        @SuppressWarnings("unchecked")
        Map<String, Object>[] batchValues = parametersList.toArray(new Map[0]);
        int[] result = namedParameterJdbcTemplate.batchUpdate(sql, batchValues);
        int totalAffected = 0;
        for (int affected : result) {
            totalAffected += affected;
        }
        log.trace("<== 批量执行完成, 总影响行数: {}", totalAffected);
        return result;
    }
}