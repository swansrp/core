package com.bidr.td.itest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDengine 连接验证集成测试
 */
public class TdConnectionIT extends AbstractTdIT {

    @Override
    protected String getStableName() {
        return null; // 不需要清理任何 STABLE
    }

    @Test
    @DisplayName("测试 TDengine 连接 - SELECT SERVER_VERSION() 非空")
    void testConnection() {
        String version = adminJdbcTemplate.queryForObject("SELECT SERVER_VERSION()", String.class);
        assertNotNull(version, "SERVER_VERSION() 不应返回 null");
        assertFalse(version.isEmpty(), "SERVER_VERSION() 不应返回空字符串");
        System.out.println("[ITest] TDengine 版本: " + version);
    }

    @Test
    @DisplayName("测试测试数据库存在 - USE water_guard_itest 不抛异常")
    void testDatabaseExists() {
        assertDoesNotThrow(() -> {
            adminJdbcTemplate.execute("USE " + DATABASE);
        }, "USE " + DATABASE + " 应该成功");
    }

    @Test
    @DisplayName("测试服务器版本号包含 3.")
    void testServerVersion() {
        String version = adminJdbcTemplate.queryForObject("SELECT SERVER_VERSION()", String.class);
        assertNotNull(version);
        assertTrue(version.contains("3."),
                "版本号应包含 '3.'，实际为: " + version);
    }
}
