package com.bidr.forge.config.jdbc;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.bidr.kernel.mybatis.log.MybatisLogFormatter;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 切换数据源（push 进栈）。
     * 注意：DynamicDataSourceContextHolder 是一个栈结构，切换后务必在 finally/close 中恢复，
     * 否则会污染当前线程后续的 MyBatis / JDBC 调用。
     */
    public void switchDataSource(String dataSourceName) {
        DynamicDataSourceContextHolder.push(dataSourceName);
    }

    /**
     * 获取当前数据源名称。(栈顶位置相当于)
     */
    public String getCurrentDataSourceName() {
        return DynamicDataSourceContextHolder.peek();
    }

    /**
     * 重置为上一个数据源（poll 一次）。
     * 更推荐使用 {@link #switchDataSourceScope(String)}，它会精确恢复到切换前的值。
     */
    public void resetToDefaultDataSource() {
        DynamicDataSourceContextHolder.poll();
    }

    /**
     * 在一个作用域内切换数据源，作用域结束后精确恢复到切换前的数据源。
     * 用法：
     * try (var ignored = jdbcConnectService.switchDataSourceScope("DORIS")) {
     *     // do query...
     * }
     */
    public DataSourceScope switchDataSourceScope(String dataSourceName) {
        String prev = getCurrentDataSourceName();
        switchDataSource(dataSourceName);
        return new DataSourceScope(prev);
    }

    /**
     * 恢复到指定的数据源（用于 finally 精确恢复）。
     * 说明：dynamic-datasource 的上下文是栈结构，这里通过清栈+必要时 push 的方式，
     * 确保最终数据源等于 prev（允许 prev 为 null，表示清空回默认）。
     */
    public void restoreDataSource(String prev) {
        // 清空当前线程的 DS 栈
        while (DynamicDataSourceContextHolder.peek() != null) {
            DynamicDataSourceContextHolder.poll();
        }
        // 恢复到切换前的 DS（null 表示回默认数据源）
        if (FuncUtil.isNotEmpty(prev)) {
            DynamicDataSourceContextHolder.push(prev);
        }
    }

    public class DataSourceScope implements AutoCloseable {
        private final String prev;
        private boolean closed;

        private DataSourceScope(String prev) {
            this.prev = prev;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            restoreDataSource(prev);
        }
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            if (row != null && !row.isEmpty()) {
                printQueryResult(completeSql, row);
            }
            @SuppressWarnings("unchecked")
            T result = (T) row.get(column);
            return result;
        } catch (Exception e) {
            printQueryResult(completeSql, null);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            printQueryResult(completeSql, row);
            return row;
        } catch (Exception e) {
            printQueryResult(completeSql, null);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        List<Map<String, Object>> result = namedParameterJdbcTemplate.queryForList(sql, parameters);
        printQueryListResult(completeSql, result);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        int affectedRows = namedParameterJdbcTemplate.update(sql, parameters);
        printUpdateResult(completeSql, affectedRows);
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
            printBatchUpdateResult(sqlList, totalAffected);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        List<Map<String, Object>> result = namedParameterJdbcTemplate.queryForList(sql, parameters);
        printQueryListResult(completeSql, result);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        try {
            Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, parameters);
            printQueryResult(completeSql, row);
            return row;
        } catch (Exception e) {
            printQueryResult(completeSql, null);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        try {
            T result = namedParameterJdbcTemplate.queryForObject(sql, parameters, clazz);
            printSimpleResult(completeSql, result != null ? 1 : 0);
            return result;
        } catch (Exception e) {
            printSimpleResult(completeSql, 0);
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
        String paramStr = formatParameters(parameters);
        String completeSql = buildCompleteSql(sql, paramStr);

        int affectedRows = namedParameterJdbcTemplate.update(sql, parameters);
        printUpdateResult(completeSql, affectedRows);
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
        @SuppressWarnings("unchecked")
        Map<String, Object>[] batchValues = parametersList.toArray(new Map[0]);
        int[] result = namedParameterJdbcTemplate.batchUpdate(sql, batchValues);
        int totalAffected = 0;
        for (int affected : result) {
            totalAffected += affected;
        }
        printBatchNamedUpdateResult(sql, parametersList, totalAffected);
        return result;
    }

    // ==================== 日志输出辅助方法 ====================

    /**
     * 格式化参数为字符串
     */
    private String formatParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        return parameters.values().stream()
            .map(v -> v + "(" + (v != null ? v.getClass().getSimpleName() : "null") + ")")
            .collect(Collectors.joining(", "));
    }

    /**
     * 构建完整的可执行 SQL
     */
    private String buildCompleteSql(String sql, String paramStr) {
        if (paramStr == null || paramStr.isEmpty()) {
            return sql;
        }
        // 使用 MybatisLogFormatter 构建 SQL
        return MybatisLogFormatter.buildSql(sql.replaceAll(":\\w+", "?"), paramStr);
    }

    /**
     * 打印查询结果（单行）
     */
    private void printQueryResult(String completeSql, Map<String, Object> row) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL\n```sql\n");
        output.append(completeSql);
        output.append("\n```");

        if (row != null && !row.isEmpty()) {
            List<String> cols = new ArrayList<>(row.keySet());
            List<List<String>> rows = new ArrayList<>();
            List<String> rowValues = cols.stream()
                .map(col -> String.valueOf(row.get(col)))
                .collect(Collectors.toList());
            rows.add(rowValues);

            String tableOutput = MybatisLogFormatter.formatMarkdown(cols, rows);
            output.append("\n### 📋 Query Result (1 row)\n");
            output.append(tableOutput);
        } else {
            output.append("\n### 📋 Query Result (0 rows)");
        }
        System.out.println(output);
    }

    /**
     * 打印查询结果（多行）
     */
    private void printQueryListResult(String completeSql, List<Map<String, Object>> result) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL\n```sql\n");
        output.append(completeSql);
        output.append("\n```");

        if (!result.isEmpty()) {
            List<String> cols = new ArrayList<>(result.get(0).keySet());
            List<List<String>> rows = new ArrayList<>();
            for (Map<String, Object> row : result) {
                List<String> rowValues = cols.stream()
                    .map(col -> String.valueOf(row.get(col)))
                    .collect(Collectors.toList());
                rows.add(rowValues);
            }

            String tableOutput = MybatisLogFormatter.formatMarkdown(cols, rows);
            output.append("\n### 📋 Query Result (").append(result.size()).append(" row")
                .append(result.size() > 1 ? "s" : "").append(")\n");
            output.append(tableOutput);
        } else {
            output.append("\n### 📋 Query Result (0 rows)");
        }
        System.out.println(output);
    }

    /**
     * 打印更新结果
     */
    private void printUpdateResult(String completeSql, int affectedRows) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL\n```sql\n");
        output.append(completeSql);
        output.append("\n```");
        output.append("\n### ✅ Update Result: ").append(affectedRows).append(" row(s) affected");
        System.out.println(output);
    }

    /**
     * 打印简单结果（只有行数）
     */
    private void printSimpleResult(String completeSql, int count) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL\n```sql\n");
        output.append(completeSql);
        output.append("\n```");
        output.append("\n### 📋 Result: ").append(count).append(" row(s)");
        System.out.println(output);
    }

    /**
     * 打印批量更新结果（不带参数）
     */
    private void printBatchUpdateResult(List<String> sqlList, int totalAffected) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL (Batch: ").append(sqlList.size()).append(" statements)\n```sql");
        for (String sql : sqlList) {
            output.append("\n").append(sql).append(";");
        }
        output.append("\n```");
        output.append("\n### ✅ Batch Update Result: ").append(totalAffected).append(" row(s) affected");
        System.out.println(output);
    }

    /**
     * 打印批量命名参数更新结果
     */
    private void printBatchNamedUpdateResult(String sql, List<Map<String, Object>> parametersList, int totalAffected) {
        StringBuilder output = new StringBuilder();
        output.append("### 🔹 Complete SQL (Batch: ").append(parametersList.size()).append(" statements)\n```sql");
        for (Map<String, Object> params : parametersList) {
            String paramStr = formatParameters(params);
            String completeSql = buildCompleteSql(sql, paramStr);
            output.append("\n").append(completeSql).append(";");
        }
        output.append("\n```");
        output.append("\n### ✅ Batch Update Result: ").append(totalAffected).append(" row(s) affected");
        System.out.println(output);
    }
}
