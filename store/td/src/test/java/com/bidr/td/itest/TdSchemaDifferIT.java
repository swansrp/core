package com.bidr.td.itest;

import com.bidr.td.sync.TdSchemaDiffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdSchemaDiffer 集成测试
 */
public class TdSchemaDifferIT extends AbstractTdIT {

    @Override
    protected String getStableName() {
        return "itest_sensor";
    }

    /**
     * Schema diff 测试需要完整清理（包括 STABLE），因为每个测试都要用不同的 schema 创建 STABLE
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
    @DisplayName("STABLE 不存在时 diff 返回空列表")
    void testDiffNewStable() {
        // @BeforeEach 已清理 STABLE，所以此时 STABLE 不存在
        TdSchemaDiffer differ = new TdSchemaDiffer(taosJdbcTemplate, "itest_sensor", SensorEntity.class);
        List<String> alterStatements = differ.diff();
        assertNotNull(alterStatements);
        assertTrue(alterStatements.isEmpty(), "STABLE 不存在时 diff 应返回空列表");

        System.out.println("[ITest] diff 新 STABLE 结果: " + alterStatements);
    }

    @Test
    @DisplayName("已有 STABLE 缺少列时，diff 返回 ADD COLUMN 语句")
    void testDiffAddColumn() {
        // 创建一个缺少列的 STABLE
        taosJdbcTemplate.execute(
                "CREATE STABLE IF NOT EXISTS itest_sensor (ts TIMESTAMP, temperature DOUBLE) TAGS (device_id BINARY(64))");

        TdSchemaDiffer differ = new TdSchemaDiffer(taosJdbcTemplate, "itest_sensor", SensorEntity.class);
        List<String> alterStatements = differ.diff();

        assertNotNull(alterStatements);
        assertFalse(alterStatements.isEmpty(), "缺少列时 diff 应返回 ALTER 语句");

        // 应包含 ADD COLUMN humidity 和 status，以及 ADD TAG location
        String allStatements = String.join("; ", alterStatements);
        assertTrue(allStatements.contains("humidity"), "应包含 ADD COLUMN humidity");
        assertTrue(allStatements.contains("status"), "应包含 ADD COLUMN status");
        assertTrue(allStatements.contains("location"), "应包含 ADD TAG location");

        System.out.println("[ITest] diff 缺列结果: " + alterStatements);
    }

    @Test
    @DisplayName("已有 STABLE 缺少 tag 时，diff 返回 ADD TAG 语句")
    void testDiffAddTag() {
        // 创建一个缺少 tag 的 STABLE（只有 device_id，缺 location）
        taosJdbcTemplate.execute(
                "CREATE STABLE IF NOT EXISTS itest_sensor (ts TIMESTAMP, temperature DOUBLE, humidity DOUBLE, status INT) TAGS (device_id BINARY(64))");

        TdSchemaDiffer differ = new TdSchemaDiffer(taosJdbcTemplate, "itest_sensor", SensorEntity.class);
        List<String> alterStatements = differ.diff();

        assertNotNull(alterStatements);
        assertFalse(alterStatements.isEmpty());

        String allStatements = String.join("; ", alterStatements);
        assertTrue(allStatements.contains("location"), "应包含 ADD TAG location");
        assertTrue(allStatements.contains("ADD TAG"), "应包含 ADD TAG 语句");

        System.out.println("[ITest] diff 缺 tag 结果: " + alterStatements);
    }

    @Test
    @DisplayName("STABLE 完全匹配时，diff 返回空列表")
    void testDiffNoChange() {
        // 先用 Schema 创建完整的 STABLE
        SensorSchema schema = new SensorSchema();
        schema.initStable(taosJdbcTemplate);

        // 现在 diff 应该没有差异
        TdSchemaDiffer differ = new TdSchemaDiffer(taosJdbcTemplate, "itest_sensor", SensorEntity.class);
        List<String> alterStatements = differ.diff();

        assertNotNull(alterStatements);
        assertTrue(alterStatements.isEmpty(), "表完全匹配时 diff 应返回空列表");

        System.out.println("[ITest] diff 无变化结果: " + alterStatements);
    }
}
