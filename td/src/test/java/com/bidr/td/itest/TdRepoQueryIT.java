package com.bidr.td.itest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.td.vo.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdRepo 查询集成测试
 * 前置数据: 3 设备 x 10 条 = 30 条，10 分钟时间跨度
 */
public class TdRepoQueryIT extends AbstractTdIT {

    private SensorRepo repo;
    private long baseTs;
    private static final int DEVICE_COUNT = 3;
    private static final int RECORDS_PER_DEVICE = 10;

    @Override
    protected String getStableName() {
        return "itest_sensor";
    }

    /**
     * 查询测试依赖 @BeforeAll 插入的数据，不做清理
     */
    @Override
    @BeforeEach
    void cleanupChildTables() {
        // 查询测试依赖 @BeforeAll 插入的数据，不做清理
    }

    @BeforeAll
    void initRepoAndData() {
        // 清理旧数据（可能有其他测试类残留的子表）
        try {
            String querySubTables = "SELECT table_name FROM information_schema.ins_tables WHERE stable_name = ?";
            List<Map<String, Object>> subTables = taosJdbcTemplate.queryForList(querySubTables, "itest_sensor");
            for (Map<String, Object> row : subTables) {
                String subTableName = String.valueOf(row.get("table_name"));
                taosJdbcTemplate.execute("DROP TABLE IF EXISTS " + subTableName);
            }
        } catch (Exception e) {
            System.out.println("[ITest] 清理旧子表: " + e.getMessage());
        }

        // 创建 STABLE
        SensorSchema schema = new SensorSchema();
        schema.initStable(taosJdbcTemplate);

        repo = new SensorRepo(taosJdbcTemplate);

        // 构造预置数据：3 设备 x 10 条 = 30 条，时间跨度 10 分钟
        baseTs = System.currentTimeMillis() - 600_000; // 10 分钟前
        String[] deviceIds = {"dev_q1", "dev_q2", "dev_q3"};
        String[] locations = {"北京", "上海", "广州"};

        for (int d = 0; d < DEVICE_COUNT; d++) {
            List<SensorEntity> entities = new ArrayList<>();
            for (int i = 0; i < RECORDS_PER_DEVICE; i++) {
                SensorEntity e = new SensorEntity();
                e.setTs(baseTs + i * 60_000L); // 每分钟一条
                e.setTemperature(20.0 + d * 5 + i * 0.5);
                e.setHumidity(50.0 + d * 10 + i);
                e.setStatus(i % 2);
                e.setDeviceId(deviceIds[d]);
                e.setLocation(locations[d]);
                entities.add(e);
            }
            String subTable = "itest_sub_q_" + d;
            repo.insertBatch(subTable, entities);
        }

        System.out.println("[ITest] 预置数据已插入: " + DEVICE_COUNT + " 设备 x " + RECORDS_PER_DEVICE + " 条");
    }

    @Test
    @DisplayName("时间范围查询")
    void testQueryRangeBasic() {
        TdRangeReq req = new TdRangeReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);

        Page<Map<String, Object>> page = repo.queryRange(req);
        assertNotNull(page);
        assertNotNull(page.getRecords());
        // 预期返回最多 20 条（默认 pageSize=20，共 30 条数据）
        assertTrue(page.getRecords().size() > 0, "时间范围查询应返回结果");

        System.out.println("[ITest] 时间范围查询返回: " + page.getRecords().size() + " 条");
    }

    @Test
    @DisplayName("分页查询 pageSize=5")
    void testQueryRangeWithPaging() {
        TdRangeReq req = new TdRangeReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);
        AdvancedQueryReq advanced = new AdvancedQueryReq();
        advanced.setCurrentPage(1L);
        advanced.setPageSize(5L);
        req.setAdvanced(advanced);

        Page<Map<String, Object>> page = repo.queryRange(req);
        assertNotNull(page);
        assertEquals(5, page.getRecords().size(), "第一页应返回 5 条");

        System.out.println("[ITest] 分页查询 pageSize=5 返回: " + page.getRecords().size() + " 条");
    }

    @Test
    @DisplayName("高级查询")
    void testQueryAdvanced() {
        TdAdvancedReq req = new TdAdvancedReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);
        AdvancedQueryReq advanced = new AdvancedQueryReq();
        advanced.setPageSize(100L);
        req.setAdvanced(advanced);

        List<Map<String, Object>> results = repo.queryAdvanced(req);
        assertNotNull(results);
        assertTrue(results.size() > 0, "高级查询应返回结果");

        System.out.println("[ITest] 高级查询返回: " + results.size() + " 条");
    }

    @Test
    @DisplayName("1 分钟窗口聚合")
    void testQueryInterval() {
        TdIntervalReq req = new TdIntervalReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);
        req.setWindow("1m");
        req.setFuncs(Arrays.asList("COUNT(*)", "AVG(temperature)"));

        List<Map<String, Object>> results = repo.queryInterval(req);
        assertNotNull(results);
        assertTrue(results.size() > 0, "窗口聚合应返回结果");

        System.out.println("[ITest] 1m 窗口聚合返回: " + results.size() + " 条");
        for (Map<String, Object> row : results) {
            System.out.println("  " + row);
        }
    }

    @Test
    @DisplayName("窗口 + 滑动聚合")
    void testQueryIntervalWithSliding() {
        TdIntervalReq req = new TdIntervalReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);
        req.setWindow("5m");
        req.setSliding("1m");
        req.setFuncs(Arrays.asList("AVG(temperature)", "AVG(humidity)"));

        List<Map<String, Object>> results = repo.queryInterval(req);
        assertNotNull(results);
        assertTrue(results.size() > 0, "窗口+滑动聚合应返回结果");

        System.out.println("[ITest] 5m 窗口 + 1m 滑动 返回: " + results.size() + " 条");
    }

    @Test
    @DisplayName("FILL(LINEAR) 插值聚合")
    void testQueryIntervalWithFill() {
        TdIntervalReq req = new TdIntervalReq();
        req.setFrom(baseTs);
        req.setTo(baseTs + 600_000L);
        req.setWindow("1m");
        req.setFill("LINEAR");
        req.setFuncs(Arrays.asList("AVG(temperature)"));

        List<Map<String, Object>> results = repo.queryInterval(req);
        assertNotNull(results);
        assertTrue(results.size() > 0, "FILL(LINEAR) 聚合应返回结果");

        System.out.println("[ITest] FILL(LINEAR) 聚合返回: " + results.size() + " 条");
    }

    @Test
    @DisplayName("按 device_id 分组查询")
    void testQueryGroupByTag() {
        TdGroupReq req = new TdGroupReq();
        req.setGroupByTags(Arrays.asList("device_id"));
        req.setFuncs(Arrays.asList("COUNT(*)", "AVG(temperature)"));

        List<Map<String, Object>> results = repo.queryGroupByTag(req);
        assertNotNull(results);
        // 按 3 个设备分组，应返回 3 条
        assertEquals(DEVICE_COUNT, results.size(), "按 device_id 分组应返回 " + DEVICE_COUNT + " 条");

        System.out.println("[ITest] 按 device_id 分组返回: " + results.size() + " 条");
        for (Map<String, Object> row : results) {
            System.out.println("  " + row);
        }
    }

    @Test
    @DisplayName("TOP 3 temperature")
    void testQueryTopN() {
        TdTopNReq req = new TdTopNReq();
        req.setField("temperature");
        req.setN(3);
        req.setDirection("TOP");

        List<Map<String, Object>> results = repo.queryTopN(req);
        assertNotNull(results);
        assertEquals(3, results.size(), "TOP 3 应返回 3 条");

        System.out.println("[ITest] TOP 3 temperature: " + results);
    }

    @Test
    @DisplayName("BOTTOM 3 temperature")
    void testQueryTopNBottom() {
        TdTopNReq req = new TdTopNReq();
        req.setField("temperature");
        req.setN(3);
        req.setDirection("BOTTOM");

        List<Map<String, Object>> results = repo.queryTopN(req);
        assertNotNull(results);
        assertEquals(3, results.size(), "BOTTOM 3 应返回 3 条");

        System.out.println("[ITest] BOTTOM 3 temperature: " + results);
    }

    @Test
    @DisplayName("查询每个设备最新数据")
    void testQueryLast() {
        AdvancedQueryReq advancedReq = new AdvancedQueryReq();
        List<Map<String, Object>> results = repo.queryLast(advancedReq, Arrays.asList("device_id"));
        assertNotNull(results);
        assertTrue(results.size() > 0, "LAST_ROW 查询应返回结果");

        System.out.println("[ITest] LAST_ROW 分组查询返回: " + results.size() + " 条");
    }

    @Test
    @DisplayName("LAST_ROW GROUP BY location")
    void testQueryLastWithGroupBy() {
        AdvancedQueryReq advancedReq = new AdvancedQueryReq();
        List<Map<String, Object>> results = repo.queryLast(advancedReq, Arrays.asList("location"));
        assertNotNull(results);
        assertTrue(results.size() > 0, "按 location 分组 LAST_ROW 应返回结果");

        System.out.println("[ITest] 按 location 分组 LAST_ROW 返回: " + results.size() + " 条");
    }
}
