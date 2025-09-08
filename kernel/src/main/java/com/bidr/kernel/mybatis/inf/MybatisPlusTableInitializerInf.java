package com.bidr.kernel.mybatis.inf;

import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Title: MybatisPlusTableInitializerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/8 13:44
 */

public interface MybatisPlusTableInitializerInf {
    /**
     * 初始化创建表
     *
     * @param dataSource 数据库连接
     */
    default void initTable(DataSource dataSource) {
        String tableName = getTableName();
        String createSql = getSql();
        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null);
            if (!rs.next()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(createSql);
                    LoggerFactory.getLogger(getClass()).info("表 {} 创建成功", tableName);
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("表 {} 检查失败", tableName, e);
        }
    }

    /**
     * 表名
     *
     * @return 表名
     */
    String getTableName();

    /**
     * 建表语句
     *
     * @return 建表语句
     */
    String getSql();
}
