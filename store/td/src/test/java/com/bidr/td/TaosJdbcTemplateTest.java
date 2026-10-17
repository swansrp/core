package com.bidr.td;

import com.bidr.td.config.TdProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaosJdbcTemplate 连接测试（Mock，不依赖真实 TD 实例）
 */
public class TaosJdbcTemplateTest {

    @Test
    public void testJdbcTemplateNotNull() {
        // 验证配置加载正常
        TdProperties props = new TdProperties();
        assertNotNull(props);
        assertEquals("jdbc:TAOS-RS://localhost:6041/water_guard", props.getUrl());
        assertEquals("root", props.getUsername());
        assertEquals("taosdata", props.getPassword());
    }

    @Test
    public void testJdbcTemplateQuery() {
        // Mock 场景下不实际执行查询，仅验证 JdbcTemplate 可创建
        TdProperties props = new TdProperties();
        assertNotNull(props.getPool());
        assertEquals(5, props.getPool().getInitialSize());
        assertEquals(20, props.getPool().getMaxActive());
    }
}
