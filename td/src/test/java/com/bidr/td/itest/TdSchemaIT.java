package com.bidr.td.itest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdSchema 集成测试
 */
public class TdSchemaIT extends AbstractTdIT {

    @Override
    protected String getStableName() {
        return "itest_sensor";
    }

    /**
     * Schema 测试需要完整清理（包括 STABLE），因为每个测试都要重新创建 STABLE
     */
    @BeforeEach
    void cleanupFull() {
        cleanupChildTables(); // 先清理子表
        try {
            taosJdbcTemplate.execute("DROP STABLE IF EXISTS " + getStableName());
        } catch (Exception e) {
            System.out.println("[ITest] cleanupFull: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("通过注解创建 STABLE 并用 DESCRIBE 验证列")
    void testCreateStableFromAnnotation() {
        SensorSchema schema = new SensorSchema();
        schema.initStable(taosJdbcTemplate);

        // 验证 STABLE 已创建
        List<Map<String, Object>> desc = taosJdbcTemplate.queryForList("DESCRIBE itest_sensor");
        assertFalse(desc.isEmpty(), "DESCRIBE 应返回非空结果");

        // 验证列名
        assertColumnExists(desc, "ts");
        assertColumnExists(desc, "temperature");
        assertColumnExists(desc, "humidity");
        assertColumnExists(desc, "status");
        assertColumnExists(desc, "device_id");
        assertColumnExists(desc, "location");

        System.out.println("[ITest] STABLE itest_sensor 列: ");
        for (Map<String, Object> row : desc) {
            System.out.println("  " + row.get("Field") + " " + row.get("Type") + " " + row.get("Note"));
        }
    }

    @Test
    @DisplayName("重复调用 initStable() 不报错（幂等性）")
    void testCreateStableIdempotent() {
        SensorSchema schema = new SensorSchema();

        // 第一次创建
        assertDoesNotThrow(() -> schema.initStable(taosJdbcTemplate));
        // 第二次创建（应该幂等，不报错）
        assertDoesNotThrow(() -> schema.initStable(taosJdbcTemplate));
    }

    @Test
    @DisplayName("getStableName() 返回 itest_sensor")
    void testGetStableName() {
        SensorSchema schema = new SensorSchema();
        assertEquals("itest_sensor", schema.getStableName());
    }

    @Test
    @DisplayName("getCreateStableSql() SQL 包含正确的列定义")
    void testGetCreateStableSql() {
        SensorSchema schema = new SensorSchema();
        String sql = schema.getCreateStableSql();

        assertNotNull(sql);
        assertTrue(sql.contains("itest_sensor"), "SQL 应包含表名 itest_sensor");
        assertTrue(sql.contains("temperature"), "SQL 应包含列 temperature");
        assertTrue(sql.contains("humidity"), "SQL 应包含列 humidity");
        assertTrue(sql.contains("status"), "SQL 应包含列 status");
        assertTrue(sql.contains("device_id"), "SQL 应包含 tag device_id");
        assertTrue(sql.contains("location"), "SQL 应包含 tag location");
        assertTrue(sql.contains("CREATE STABLE"), "SQL 应以 CREATE STABLE 开头");
        assertTrue(sql.contains("TAGS"), "SQL 应包含 TAGS 关键字");

        System.out.println("[ITest] 生成的 DDL: " + sql);
    }

    @Test
    @DisplayName("手动 DDL 路径创建 STABLE")
    void testManualDDLCreate() {
        String manualStableName = "itest_sensor_manual";
        try {
            // 使用手动 DDL 创建一个不同的 STABLE
            String manualDDL = "CREATE STABLE IF NOT EXISTS " + manualStableName + " (ts TIMESTAMP, val DOUBLE) TAGS (device_id BINARY(64))";

            // 直接执行手动 DDL
            assertDoesNotThrow(() -> taosJdbcTemplate.execute(manualDDL));

            // 验证 STABLE 已创建
            List<Map<String, Object>> desc = taosJdbcTemplate.queryForList("DESCRIBE " + manualStableName);
            assertFalse(desc.isEmpty());
            assertColumnExists(desc, "ts");
            assertColumnExists(desc, "val");
            assertColumnExists(desc, "device_id");
        } finally {
            taosJdbcTemplate.execute("DROP STABLE IF EXISTS " + manualStableName);
        }
    }

    private void assertColumnExists(List<Map<String, Object>> desc, String columnName) {
        boolean found = desc.stream()
                .anyMatch(row -> columnName.equalsIgnoreCase(String.valueOf(row.get("Field"))));
        assertTrue(found, "列 '" + columnName + "' 应存在于 DESCRIBE 结果中");
    }
}
