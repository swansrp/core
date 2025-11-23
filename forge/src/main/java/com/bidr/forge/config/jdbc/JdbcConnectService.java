package com.bidr.forge.config.jdbc;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
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
     *
     * @param sql SQL语句
     * @return 查询结果
     */
    public <T> T queryObject(String sql, String column, Class<T> clazz) {
        log.trace("==> 查询SQL: {}", sql);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                T result = rs.getObject(column, clazz);
                log.trace("<== 查询结果: {}", result);
                return result;
            }
            log.trace("<== 查询结果: null");
        } catch (SQLException e) {
            throw new RuntimeException("执行查询失败: " + sql, e);
        }
        return null;
    }

    /**
     * 执行查询SQL语句，返回单个结果
     *
     * @param sql SQL语句
     * @return 查询结果
     */
    public Map<String, Object> executeQueryOne(String sql) {
        List<Map<String, Object>> result = executeQuery(sql);
        Map<String, Object> row = result.isEmpty() ? null : result.get(0);
        log.trace("<== 查询单行结果: {}", row);
        return row;
    }

    /**
     * 执行查询SQL语句，返回结果列表
     *
     * @param sql SQL语句
     * @return 查询结果列表
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        log.trace("==> 查询SQL: {}", sql);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("执行查询失败: " + sql, e);
        }
        log.trace("<== 查询结果行数: {}", result.size());
        return result;
    }

    /**
     * 执行更新SQL语句（INSERT, UPDATE, DELETE）
     *
     * @param sql SQL语句
     * @return 影响的行数
     */
    public int executeUpdate(String sql) {
        log.trace("==> 更新SQL: {}", sql);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int affectedRows = ps.executeUpdate();
            log.trace("<== 影响行数: {}", affectedRows);
            return affectedRows;
        } catch (SQLException e) {
            throw new RuntimeException("执行更新失败: " + sql, e);
        }
    }

    /**
     * 执行SQL语句，返回结果集
     *
     * @param sql SQL语句
     * @return ResultSet结果集
     */
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
     * 执行批量SQL语句
     *
     * @param sqlList SQL语句列表
     * @return 每条SQL语句影响的行数
     */
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
}