package com.bidr.td.itest;

import com.bidr.td.repository.BaseTdSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdRepo CRUD 集成测试
 */
public class TdRepoCrudIT extends AbstractTdIT {

    private SensorRepo repo;

    @Override
    protected String getStableName() {
        return "itest_sensor";
    }

    @BeforeAll
    void initRepoAndStable() {
        // 确保 STABLE 已创建
        SensorSchema schema = new SensorSchema();
        schema.initStable(taosJdbcTemplate);

        repo = new SensorRepo(taosJdbcTemplate);
    }

    @Test
    @DisplayName("单条插入带 tag，查询验证")
    void testInsertOne() {
        long now = System.currentTimeMillis();
        SensorEntity entity = new SensorEntity();
        entity.setTs(now);
        entity.setTemperature(25.5);
        entity.setHumidity(60.0);
        entity.setStatus(1);
        entity.setDeviceId("dev_001");
        entity.setLocation("北京");

        repo.insertOne("itest_sub_dev_001", entity);

        // 查询验证
        List<Map<String, Object>> results = taosJdbcTemplate.queryForList(
                "SELECT * FROM itest_sub_dev_001");
        assertEquals(1, results.size());
        Map<String, Object> row = results.get(0);
        assertEquals(25.5, ((Number) row.get("temperature")).doubleValue(), 0.001);
        assertEquals(60.0, ((Number) row.get("humidity")).doubleValue(), 0.001);

        System.out.println("[ITest] 单条插入验证通过: " + row);
    }

    @Test
    @DisplayName("批量插入 5 条，查询返回 5 条")
    void testInsertBatch() {
        long baseTs = System.currentTimeMillis();
        List<SensorEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SensorEntity e = new SensorEntity();
            e.setTs(baseTs + i * 1000);
            e.setTemperature(20.0 + i);
            e.setHumidity(50.0 + i);
            e.setStatus(1);
            e.setDeviceId("dev_batch");
            e.setLocation("上海");
            entities.add(e);
        }

        repo.insertBatch("itest_sub_dev_batch", entities);

        List<Map<String, Object>> results = taosJdbcTemplate.queryForList(
                "SELECT COUNT(*) AS cnt FROM itest_sub_dev_batch");
        int count = ((Number) results.get(0).get("cnt")).intValue();
        assertEquals(5, count, "应插入 5 条记录");

        System.out.println("[ITest] 批量插入 5 条验证通过");
    }

    @Test
    @DisplayName("多表多批次插入")
    void testInsertMultiTableBatch() {
        long baseTs = System.currentTimeMillis();
        Map<String, List<SensorEntity>> tableDataMap = new LinkedHashMap<>();

        for (int t = 0; t < 3; t++) {
            String subTable = "itest_sub_multi_" + t;
            List<SensorEntity> entities = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                SensorEntity e = new SensorEntity();
                e.setTs(baseTs + t * 10000 + i * 1000);
                e.setTemperature(18.0 + t + i);
                e.setHumidity(45.0 + t);
                e.setStatus(0);
                e.setDeviceId("dev_multi_" + t);
                e.setLocation("深圳");
                entities.add(e);
            }
            tableDataMap.put(subTable, entities);
        }

        repo.insertMultiTableBatch(tableDataMap);

        // 验证每个子表各有 3 条
        for (int t = 0; t < 3; t++) {
            List<Map<String, Object>> results = taosJdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS cnt FROM itest_sub_multi_" + t);
            int count = ((Number) results.get(0).get("cnt")).intValue();
            assertEquals(3, count, "子表 itest_sub_multi_" + t + " 应有 3 条记录");
        }

        System.out.println("[ITest] 多表多批次插入验证通过 (3表 x 3条)");
    }

    @Test
    @DisplayName("显式创建子表，DESCRIBE 验证")
    void testCreateSubTable() {
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("device_id", "dev_create");
        tags.put("location", "广州");

        repo.createSubTable("itest_sub_create", tags);

        // DESCRIBE 验证子表存在
        List<Map<String, Object>> desc = taosJdbcTemplate.queryForList("DESCRIBE itest_sub_create");
        assertFalse(desc.isEmpty(), "子表应存在");

        System.out.println("[ITest] 创建子表验证通过");
    }

    @Test
    @DisplayName("修改 tag 值")
    void testAlterTagValue() {
        // 先创建子表并插入数据
        long now = System.currentTimeMillis();
        SensorEntity entity = new SensorEntity();
        entity.setTs(now);
        entity.setTemperature(22.0);
        entity.setHumidity(55.0);
        entity.setStatus(1);
        entity.setDeviceId("dev_alter");
        entity.setLocation("杭州");
        repo.insertOne("itest_sub_alter", entity);

        // 修改 location tag
        repo.alterTagValue("itest_sub_alter", "location", "南京");

        // 验证 tag 已修改 — 使用 SHOW TAGS FROM 查看 tag 值
        List<Map<String, Object>> tags = taosJdbcTemplate.queryForList("SHOW TAGS FROM itest_sub_alter");
        assertNotNull(tags);
        boolean found = false;
        for (Map<String, Object> tag : tags) {
            if ("location".equals(String.valueOf(tag.get("tag_name")))) {
                assertEquals("南京", String.valueOf(tag.get("tag_value")),
                        "location tag 值应被修改为 南京");
                found = true;
                break;
            }
        }
        assertTrue(found, "location tag should exist");

        System.out.println("[ITest] 修改 tag 值验证通过");
    }

    @Test
    @DisplayName("删除子表")
    void testDropSubTable() {
        // 先创建子表
        long now = System.currentTimeMillis();
        SensorEntity entity = new SensorEntity();
        entity.setTs(now);
        entity.setTemperature(20.0);
        entity.setHumidity(50.0);
        entity.setStatus(0);
        entity.setDeviceId("dev_drop");
        entity.setLocation("成都");
        repo.insertOne("itest_sub_drop", entity);

        // 确认子表存在
        List<Map<String, Object>> before = taosJdbcTemplate.queryForList(
                "SELECT COUNT(*) AS cnt FROM itest_sub_drop");
        assertTrue(((Number) before.get(0).get("cnt")).intValue() > 0);

        // 删除子表
        repo.dropSubTable("itest_sub_drop");

        // 验证子表已删除 — 查询已删除的表应抛异常
        assertThrows(Exception.class, () -> {
            taosJdbcTemplate.queryForList("SELECT COUNT(*) AS cnt FROM itest_sub_drop");
        }, "子表删除后查询应抛异常");

        System.out.println("[ITest] 删除子表验证通过");
    }

    @Test
    @DisplayName("插入 null 值列不抛异常")
    void testInsertNullValue() {
        long now = System.currentTimeMillis();
        SensorEntity entity = new SensorEntity();
        entity.setTs(now);
        entity.setTemperature(null);     // null
        entity.setHumidity(65.0);
        entity.setStatus(null);          // null
        entity.setDeviceId("dev_null");
        entity.setLocation("武汉");

        assertDoesNotThrow(() -> repo.insertOne("itest_sub_null", entity));

        // 验证记录已插入
        List<Map<String, Object>> results = taosJdbcTemplate.queryForList(
                "SELECT * FROM itest_sub_null");
        assertEquals(1, results.size());
        assertNotNull(results.get(0).get("humidity"));

        System.out.println("[ITest] 插入 null 值验证通过");
    }
}

