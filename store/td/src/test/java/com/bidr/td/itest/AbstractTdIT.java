package com.bidr.td.itest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TDengine 集成测试基类
 * 手动创建 JdbcTemplate（不使用 @SpringBootTest），使用独立数据库 water_guard_itest 做隔离
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractTdIT {

    protected static final String DATABASE = "water_itest";
    protected static final String ADMIN_URL = "jdbc:TAOS-RS://sharp.tanya-plus.com.cn:16041/?useSSL=true&httpPoolSize=20&httpConnectTimeout=10000&httpSocketTimeout=30000";
    protected static final String TEST_URL = "jdbc:TAOS-RS://sharp.tanya-plus.com.cn:16041/" + DATABASE + "?useSSL=true&httpPoolSize=20&httpConnectTimeout=10000&httpSocketTimeout=30000";
    protected static final String USERNAME = "hj212";
    protected static final String PASSWORD = "Adm!n123";

    protected JdbcTemplate adminJdbcTemplate;
    protected JdbcTemplate taosJdbcTemplate;

    private final List<HikariDataSource> dataSources = new ArrayList<>();

    /**
     * 子类返回要清理的 STABLE 名（如 "itest_sensor"），返回 null 表示不清理
     */
    protected abstract String getStableName();

    @AfterAll
    void tearDown() {
        for (HikariDataSource ds : dataSources) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
        dataSources.clear();
    }

    @BeforeAll
    void setupDatabase() {
        // 创建 admin JdbcTemplate（不带数据库名）
        System.out.println("[ITest] 创建 admin 连接: " + ADMIN_URL);
        adminJdbcTemplate = createJdbcTemplate(ADMIN_URL);
        System.out.println("[ITest] admin 连接成功");

        // 创建测试数据库
        try {
            adminJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE + " PRECISION 'ms' KEEP 3650");
            System.out.println("[ITest] 数据库 " + DATABASE + " 已创建/确认存在");
        } catch (Exception e) {
            System.err.println("[ITest] 无法创建数据库: " + e.getMessage());
            throw e;
        }

        // 创建测试 JdbcTemplate（带数据库名）
        System.out.println("[ITest] 创建 test 连接: " + TEST_URL);
        taosJdbcTemplate = createJdbcTemplate(TEST_URL);
        System.out.println("[ITest] test 连接成功");
    }

    /**
     * 默认 @BeforeEach：仅删除子表，保留 STABLE 结构
     * 子类如需完整清理（包括 STABLE），请重写 @BeforeEach 并调用此方法后再删除 STABLE
     */
    @BeforeEach
    void cleanupChildTables() {
        String stableName = getStableName();
        if (stableName == null || stableName.isEmpty()) {
            return;
        }
        try {
            String querySubTables = "SELECT table_name FROM information_schema.ins_tables WHERE stable_name = ?";
            List<Map<String, Object>> subTables = taosJdbcTemplate.queryForList(querySubTables, stableName);
            for (Map<String, Object> row : subTables) {
                String subTableName = String.valueOf(row.get("table_name"));
                taosJdbcTemplate.execute("DROP TABLE IF EXISTS " + subTableName);
            }
        } catch (Exception e) {
            System.out.println("[ITest] cleanupChildTables: " + e.getMessage());
        }
    }

    private JdbcTemplate createJdbcTemplate(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        config.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setPoolName("TaosITestPool-" + url.hashCode());
        HikariDataSource ds = new HikariDataSource(config);
        dataSources.add(ds);
        return new JdbcTemplate(ds);
    }
}
