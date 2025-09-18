package com.bidr.kernel.mybatis.inf;

import com.bidr.kernel.utils.FuncUtil;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * MybatisPlusTableInitializerInf 接口用于统一管理数据库表的初始化和升级。
 * 通过实现该接口，子类可以定义表名、建表 SQL 和升级 SQL。
 * 接口提供了自动创建表、执行升级 SQL 以及智能判断是否需要执行 DDL 的功能。
 * </p>
 *
 * <p>
 * 功能特点：
 * <ul>
 *     <li>自动创建表（如果不存在）</li>
 *     <li>自动执行升级 SQL</li>
 *     <li>智能跳过已存在的列、索引、约束</li>
 *     <li>自动判断列类型、长度及 NOT NULL 属性</li>
 *     <li>每个表独立维护版本号，避免重复执行升级</li>
 *     <li>子类只需提供建表 SQL 和升级 SQL，升级逻辑由接口统一处理</li>
 * </ul>
 * </p>
 *
 * @author Sharp
 * @since 2025/9/8
 */
public interface MybatisPlusTableInitializerInf {

    /**
     * 初始化表并执行升级逻辑。
     * <p>
     * 步骤：
     * <ol>
     *     <li>判断表是否存在，如果不存在执行建表 SQL</li>
     *     <li>读取表在 sys_table_version 的版本号，如果不存在则插入 0</li>
     *     <li>按版本顺序执行升级 SQL，并更新版本号</li>
     * </ol>
     * </p>
     *
     * @param dataSource 数据源，用于获取数据库连接
     */
    default void initTable(DataSource dataSource) {
        String tableName = getTableName();
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            DatabaseMetaData metaData = connection.getMetaData();
            handleCreateDDL(tableName, stmt, metaData);
            handleUpgradeDDL(tableName, stmt, metaData);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("表 {} 检查失败", tableName, e);
        }
    }

    /**
     * 获取表名。
     *
     * @return 表名字符串
     */
    String getTableName();

    /**
     * 处理建表语句
     *
     * @param tableName 表名
     * @param stmt      数据库连接
     * @param metaData  数据库元数据
     * @throws SQLException 异常
     */
    default void handleCreateDDL(String tableName, Statement stmt, DatabaseMetaData metaData) throws SQLException {
        String createSql = getCreateSql();
        if (FuncUtil.isNotEmpty(createSql)) {
            if (!tableExists(metaData, tableName)) {
                stmt.executeUpdate(createSql);
                stmt.executeUpdate(
                        "INSERT INTO sys_table_version(table_name, version) VALUES ('" + tableName + "', 0) " +
                                "ON DUPLICATE KEY UPDATE version=0");
                LoggerFactory.getLogger(getClass()).info("表 {} 创建成功", tableName);
            }
        }
    }

    /**
     * 处理更新表结构语句
     *
     * @param tableName 表名
     * @param stmt      数据库连接
     * @param metaData  数据库元数据
     * @throws SQLException 异常
     */
    default void handleUpgradeDDL(String tableName, Statement stmt, DatabaseMetaData metaData) throws SQLException {
        // 2. 获取当前版本
        int currentVersion = 0;
        try (ResultSet vrs = stmt.executeQuery(
                "SELECT version FROM sys_table_version WHERE table_name='" + tableName + "'")) {
            if (vrs.next()) {
                currentVersion = vrs.getInt(1);
            } else {
                stmt.executeUpdate(
                        "INSERT INTO sys_table_version(table_name, version) VALUES ('" + tableName + "', 0)");
                currentVersion = 0;
            }
        }

        LinkedHashMap<Integer, String> scripts = getUpgradeScripts();
        if (!scripts.isEmpty()) {
            // 找到目标版本（LinkedHashMap 有序，最后一个就是最高版本）
            int latestVersion = scripts.keySet().stream().max(Integer::compareTo).orElse(currentVersion);

            if (latestVersion > currentVersion) {
                for (Map.Entry<Integer, String> entry : scripts.entrySet()) {
                    int targetVersion = entry.getKey();
                    String sql = entry.getValue();

                    if (targetVersion > currentVersion) {
                        LoggerFactory.getLogger(getClass())
                                .info("执行表 {} 升级 v{} -> v{}", tableName, currentVersion, targetVersion);
                        if (!shouldSkip(metaData, sql)) {
                            stmt.executeUpdate(sql);
                        }
                        stmt.executeUpdate(
                                "INSERT INTO sys_table_version(table_name, version) VALUES ('" + tableName + "', " +
                                        targetVersion + ") ON DUPLICATE KEY UPDATE version=" + targetVersion);
                        LoggerFactory.getLogger(getClass())
                                .info("执行表 {} 升级 v{} -> v{} 完成", tableName, currentVersion, targetVersion);

                        currentVersion = targetVersion;
                    }
                }

                LoggerFactory.getLogger(getClass()).info("表 {} 已同步到最新版本 v{}", tableName, currentVersion);
            }
        }
    }

    /**
     * 获取建表 SQL。
     *
     * @return 创建表的 SQL 语句
     */
    String getCreateSql();

    /**
     * 判断表是否存在
     *
     * @param metaData  数据库元数据
     * @param tableName 表名
     * @return 如果表存在返回 true，否则返回 false
     * @throws SQLException SQL 异常
     */
    default boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * 获取升级脚本集合。
     * <p>
     * key = 版本号（递增），value = 升级 SQL
     * </p>
     *
     * @return 升级 SQL 的有序集合
     */
    LinkedHashMap<Integer, String> getUpgradeScripts();

    /** ----------------- 列/索引/表判断 ----------------- */

    /**
     * 判断 SQL 是否可以跳过执行。
     * <p>
     * 用于智能判断：
     * <ul>
     *     <li>表是否存在</li>
     *     <li>列是否存在</li>
     *     <li>列类型是否一致</li>
     *     <li>NOT NULL 属性是否一致</li>
     *     <li>索引或约束是否存在</li>
     * </ul>
     * </p>
     *
     * @param metaData 数据库元数据，用于检查表结构
     * @param sql      待执行的 DDL SQL
     * @return 如果无需执行 SQL，则返回 true；否则返回 false
     * @throws SQLException SQL 异常
     */
    default boolean shouldSkip(DatabaseMetaData metaData, String sql) throws SQLException {
        sql = sql.trim().toUpperCase();

        if (sql.startsWith("ALTER TABLE")) {
            String[] parts = sql.split("\\s+");
            String table = parts[2];

            // ADD COLUMN
            if (sql.contains("ADD COLUMN")) {
                String column = parts[5];
                return columnExists(metaData, table, column);
            }

            // DROP COLUMN
            if (sql.contains("DROP COLUMN")) {
                String column = parts[5];
                return !columnExists(metaData, table, column);
            }

            // MODIFY / CHANGE COLUMN
            if (sql.contains("MODIFY COLUMN") || sql.contains("CHANGE COLUMN")) {
                String column = parts[4];
                return shouldSkipColumn(metaData, table, column, sql);
            }

            // ADD CONSTRAINT / ADD FOREIGN KEY
            if (sql.contains("ADD CONSTRAINT") || sql.contains("ADD FOREIGN KEY")) {
                String constraint = parts[6];
                return indexExists(metaData, table, constraint);
            }

            // DROP CONSTRAINT / DROP FOREIGN KEY
            if (sql.contains("DROP CONSTRAINT") || sql.contains("DROP FOREIGN KEY")) {
                String constraint = parts[5];
                return !indexExists(metaData, table, constraint);
            }

            // ADD INDEX
            if (sql.contains("ADD INDEX")) {
                String index = parts[5];
                return indexExists(metaData, table, index);
            }

            // DROP INDEX
            if (sql.contains("DROP INDEX")) {
                String index = parts[2];
                return !indexExists(metaData, table, index);
            }
        }

        // CREATE TABLE
        if (sql.startsWith("CREATE TABLE")) {
            if (sql.contains("IF NOT EXISTS")) {
                // 交给数据库自己处理，不跳过
                return false;
            }
            String table = sql.split("\\s+")[2];
            return tableExists(metaData, table);
        }

        // DROP TABLE
        if (sql.startsWith("DROP TABLE")) {
            if (sql.contains("IF NOT EXISTS")) {
                // 交给数据库自己处理，不跳过
                return false;
            }
            String table = sql.split("\\s+")[2];
            return !tableExists(metaData, table);
        }

        // CREATE INDEX
        if (sql.startsWith("CREATE INDEX")) {
            String[] parts = sql.split("\\s+");
            String index = parts[2];
            String table = parts[4];
            return indexExists(metaData, table, index);
        }

        return false;
    }

    /**
     * 判断列是否存在
     *
     * @param metaData   数据库元数据
     * @param tableName  表名
     * @param columnName 列名
     * @return 如果列存在返回 true，否则返回 false
     * @throws SQLException SQL 异常
     */
    default boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    /**
     * 判断列是否需要跳过（包含列类型和 NOT NULL 判断）。
     *
     * @param metaData   数据库元数据
     * @param table      表名
     * @param column     列名
     * @param sqlSegment 对应的 ALTER COLUMN SQL 段
     * @return 如果列类型和 NOT NULL 属性一致，返回 true；否则返回 false
     * @throws SQLException SQL 异常
     */
    default boolean shouldSkipColumn(DatabaseMetaData metaData, String table, String column,
                                     String sqlSegment) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (!rs.next()) {
                return false;
            }

            String dbType = rs.getString("TYPE_NAME");
            int columnSize = rs.getInt("COLUMN_SIZE");
            int nullable = rs.getInt("NULLABLE");
            String dbTypeFull = dbType + "(" + columnSize + ")";
            boolean dbNotNull = nullable == DatabaseMetaData.columnNoNulls;

            String sqlType = parseSqlColumnType(sqlSegment, column);
            boolean sqlNotNull = sqlSegment.toUpperCase().contains("NOT NULL");

            return dbTypeFull.equalsIgnoreCase(sqlType) && dbNotNull == sqlNotNull;
        }
    }

    /** ----------------- 列类型 + NOT NULL 判断 ----------------- */

    /**
     * 判断索引是否存在
     *
     * @param metaData  数据库元数据
     * @param tableName 表名
     * @param indexName 索引名
     * @return 如果索引存在返回 true，否则返回 false
     * @throws SQLException SQL 异常
     */
    default boolean indexExists(DatabaseMetaData metaData, String tableName, String indexName) throws SQLException {
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String idx = rs.getString("INDEX_NAME");
                if (idx != null && idx.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从 SQL 语句中解析列类型。
     *
     * @param sqlSegment ALTER COLUMN SQL 段
     * @param column     列名
     * @return 列类型字符串，例如 VARCHAR(255)
     */
    default String parseSqlColumnType(String sqlSegment, String column) {
        sqlSegment = sqlSegment.replaceAll("\\s+", " ");
        int idx = sqlSegment.toUpperCase().indexOf(column.toUpperCase());
        if (idx < 0) {
            return "";
        }
        String colSegment = sqlSegment.substring(idx + column.length()).trim();
        String[] parts = colSegment.split("\\s+");
        return parts.length > 0 ? parts[0].toUpperCase() : "";
    }
}
