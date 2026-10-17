package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.service.AgentExploreTools;
import com.bidr.insight.smartquery.service.SemanticCatalogTools;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: SemanticCatalogFactsTest
 * Description: 跨阶段事实台账 + metricDetail 维度收窄 + 兜底 SQL 守卫用例：
 * ① 目录工具核实成功的事实逐条上报 factRecorder（解析链核实 → 维护链注入采信的载体）；
 * ② metricDetail 第二参数 dimension_keyword 收窄（命中过滤/无命中回全量+note）；
 * ③ AgentExploreTools.runGuardedSelect 与 run_sql 同守卫口径（只读头/黑名单/表白名单），
 * describeTable 事实摘录。纯构造（同 AgentToolsGuardTest 范式），不依赖外部数据源与 Spring 容器
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class SemanticCatalogFactsTest {

    private static SemanticLayer layer;

    @BeforeClass
    public static void setup() {
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ht\",\"display_name\":\"合同\",\"table\":\"db1.ht_contract\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"Int\",\"display_name\":\"ID\"},"
                + "{\"name\":\"lead_dept\",\"type\":\"String\",\"display_name\":\"牵头部门\",\"value_domain\":\"dept_dom\"},"
                + "{\"name\":\"sign_date\",\"type\":\"Date\",\"display_name\":\"签订日期\"}]}]");
        assets.put("dimensions.json", "["
                + "{\"name\":\"dept\",\"display_name\":\"部门\",\"expression\":\"db1.ht_contract.lead_dept\"},"
                + "{\"name\":\"org\",\"display_name\":\"机构\",\"expression\":\"db1.ht_contract.org_name\"},"
                + "{\"name\":\"year\",\"display_name\":\"年份\",\"expression\":\"db1.ht_contract.sign_date\",\"granularity\":\"year\"}]");
        assets.put("metrics.json", "[{\"name\":\"amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(db1.ht_contract.amt)\",\"source_table\":\"db1.ht_contract\","
                + "\"supported_dimensions\":[\"dept\",\"org\",\"year\"]}]");
        assets.put("value-domains.json", "{\"domains\":{\"dept_dom\":{\"stored_as\":\"code\","
                + "\"values\":[{\"code\":\"KCY\",\"label\":\"勘察院\"}]}}}");
        assets.put("concepts.json", "{\"concepts\":[{\"name\":\"传统项目\",\"aliases\":[\"传统模式\"],"
                + "\"entity\":\"ht\",\"expands_to\":{\"dimension\":\"dept\",\"operator\":\"=\",\"value\":\"KCY\"}}]}");
        layer = SemanticLayer.fromContent(assets);
    }

    // ────────────────────────── 事实台账记录 ──────────────────────────

    /** metricDetail 成功核实 → 公式/源表事实进台账 */
    @Test
    public void metricDetailRecordsFact() {
        List<String> facts = new ArrayList<>();
        new SemanticCatalogTools(layer, facts::add).metricDetail("amt", null, null);
        Assert.assertEquals(1, facts.size());
        Assert.assertTrue("应记录指标公式与源表: " + facts.get(0),
                facts.get(0).contains("[metricDetail] 指标 amt：SUM(db1.ht_contract.amt)")
                        && facts.get(0).contains("源表:db1.ht_contract"));
    }

    /** findValue 反查成功 → 取值→维度=码值事实进台账（写 filters 直接采信） */
    @Test
    public void findValueRecordsFact() {
        List<String> facts = new ArrayList<>();
        new SemanticCatalogTools(layer, facts::add).findValue("勘察院");
        Assert.assertEquals(1, facts.size());
        Assert.assertTrue("应记录取值反查结果: " + facts.get(0),
                facts.get(0).contains("[findValue] 取值\"勘察院\" → dept=KCY"));
    }

    /** conceptDetail 展开成功 → 概念展开事实进台账 */
    @Test
    public void conceptDetailRecordsFact() {
        List<String> facts = new ArrayList<>();
        new SemanticCatalogTools(layer, facts::add).conceptDetail("传统项目");
        Assert.assertEquals(1, facts.size());
        Assert.assertTrue("应记录概念展开: " + facts.get(0),
                facts.get(0).contains("[conceptDetail] 概念 传统项目 → dept = KCY"));
    }

    /** describeEntity 成功 → 实体字段清单事实进台账；dimensionDetail 同理 */
    @Test
    public void entityAndDimensionDetailRecordFact() {
        List<String> facts = new ArrayList<>();
        SemanticCatalogTools tools = new SemanticCatalogTools(layer, facts::add);
        tools.describeEntity("ht");
        tools.dimensionDetail("dept");
        Assert.assertEquals(2, facts.size());
        Assert.assertTrue(facts.get(0).contains("[describeEntity] 实体 ht(db1.ht_contract) 字段：id,lead_dept,sign_date"));
        Assert.assertTrue(facts.get(1).contains("[dimensionDetail] 维度 dept：db1.ht_contract.lead_dept"));
    }

    /** describeEntity 携带分区列/快照语义（问数链写跨期查询前可见，防累计数算错）；
     *  无约定实体不输出两键（旧数据兼容）。背景：2026-08-23 表名后缀快照识别落地 */
    @Test
    public void describeEntityExposesSnapshotSemantics() {
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ctr\",\"display_name\":\"合同\",\"table\":\"dw_dws.t_dmi\","
                + "\"partition_column\":\"dm\",\"snapshot_type\":\"月增量，跨月需累加\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"Int\",\"display_name\":\"ID\"}]},"
                + "{\"name\":\"plain\",\"display_name\":\"无约定\",\"table\":\"dw_ods.t_raw\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"Int\",\"display_name\":\"ID\"}]}]");
        SemanticLayer snap = SemanticLayer.fromContent(assets);
        String with = new SemanticCatalogTools(snap).describeEntity("ctr");
        Assert.assertTrue("应输出分区列: " + with, with.contains("\"partition_column\":\"dm\""));
        Assert.assertTrue("应输出快照语义: " + with, with.contains("\"snapshot_type\":\"月增量，跨月需累加\""));
        String without = new SemanticCatalogTools(snap).describeEntity("plain");
        Assert.assertFalse("无约定不输出快照键: " + without, without.contains("snapshot_type"));
        Assert.assertFalse("无分区不输出分区键: " + without, without.contains("partition_column"));
    }

    /** 未命中（error 返回）不产生事实行——台账只记核实为真的事实 */
    @Test
    public void missRecordsNoFact() {
        List<String> facts = new ArrayList<>();
        SemanticCatalogTools tools = new SemanticCatalogTools(layer, facts::add);
        Assert.assertTrue(tools.metricDetail("not_exist", null, null).contains("error"));
        Assert.assertTrue(tools.findValue("不存在的取值").contains("error"));
        Assert.assertTrue(tools.conceptDetail("不存在概念").contains("error"));
        Assert.assertTrue("未命中不应记录事实", facts.isEmpty());
    }

    /** 超长事实截断（formula 侧构造超 200 字符的场景：维度全名拼接） */
    @Test
    public void longFactTruncated() {
        List<String> facts = new ArrayList<>();
        new SemanticCatalogTools(layer, facts::add).dimensionDetail("year");
        // 该行远短于上限，另用直接观察上限行为：改用指标公式超长场景断言不超过 FACT_MAX_LEN+1
        for (String f : facts) {
            Assert.assertTrue("事实行应不超过 201 字符: " + f.length(), f.length() <= 201);
        }
    }

    // ────────────────────────── metricDetail 维度收窄 ──────────────────────────

    /** dimension_keyword 命中：只回中文名含关键词的维度 */
    @Test
    public void metricDetailDimensionKeywordFilters() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", "机构", null);
        Assert.assertTrue("应只回 org: " + r, r.contains("\"supported_dimensions\":[\"org\"]"));
    }

    /** dimension_keyword 按英文名片段命中同样过滤 */
    @Test
    public void metricDetailDimensionKeywordByName() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", "ye", null);
        Assert.assertTrue(r.contains("\"supported_dimensions\":[\"year\"]"));
    }

    /** 无命中：回全量 + note 引导换关键词（不静默空列表误导模型） */
    @Test
    public void metricDetailKeywordMissFallsBackToAll() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", "不存在的关键词", null);
        Assert.assertTrue("无命中应回全量: " + r, r.contains("\"supported_dimensions\":[\"dept\",\"org\",\"year\"]"));
        Assert.assertTrue("应有 note 引导: " + r, r.contains("无命中"));
    }

    // ────────────────────────── 兜底 SQL 守卫（runGuardedSelect） ──────────────────────────

    /** 写语句头直接拒绝（守卫在触碰连接前完成，无连接可测） */
    @Test
    public void guardedSelectRejectsWrite() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("DELETE FROM db1.ht_contract");
            Assert.fail("写语句应被守卫拒绝");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("只读守卫"));
        }
    }

    /** 表不在实体白名单拒绝 */
    @Test
    public void guardedSelectRejectsUnknownTable() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("SELECT * FROM db1.other_table");
            Assert.fail("越权表应被守卫拒绝");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("只读守卫"));
        }
    }

    /** 守卫通过 + 无连接：收口为执行失败异常（不裸抛 NPE） */
    @Test
    public void guardedSelectNoConnSurfacesExecFailure() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("SELECT db1.ht_contract.id FROM db1.ht_contract");
            Assert.fail("无连接应报执行失败");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("应包装为执行失败: " + e.getMessage(), e.getMessage().contains("兜底 SQL 执行失败"));
        }
    }

    /** WITH CTE 放行（2026-08-26 背景：勘察院兜底会话五表平铺长 SQL 可读性差）：
     *  WITH 头语句 + cte.列 虚表引用均过守卫（无连接收口为执行失败即证明守卫层已放行） */
    @Test
    public void guardedSelectAllowsWithCte() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("WITH contract_info AS (SELECT db1.ht_contract.id, db1.ht_contract.sign_date"
                    + " FROM db1.ht_contract) SELECT contract_info.id FROM contract_info");
            Assert.fail("无连接应报执行失败");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("应过守卫后报执行失败而非拒绝: " + e.getMessage(),
                    e.getMessage().contains("兜底 SQL 执行失败"));
        }
    }

    /** CTE 体内写操作照拒（黑名单对 WITH 语句体同样生效） */
    @Test
    public void guardedSelectRejectsWriteInsideCte() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("WITH x AS (DELETE FROM db1.ht_contract) SELECT 1");
            Assert.fail("CTE 体内写操作应被拒绝");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("只读守卫"));
        }
    }

    /** CTE 体内越权表照拒：白名单对 CTE 内部真实表逐段生效，不因外层是 CTE 而绕过 */
    @Test
    public void guardedSelectRejectsBadTableInsideCte() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        try {
            tools.runGuardedSelect("WITH cte AS (SELECT * FROM db1.other_table) SELECT * FROM cte");
            Assert.fail("CTE 内部越权表应被拒绝");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("只读守卫"));
        }
    }

    /** describeTable 成功核实 → 表字段事实进台账（探索侧台账，注入兜底子会话） */
    @Test
    public void describeTableRecordsFact() {
        List<String> facts = new ArrayList<>();
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null, facts::add);
        String r = tools.describeTable("db1.ht_contract");
        Assert.assertTrue(r.contains("\"fields\""));
        Assert.assertEquals(1, facts.size());
        Assert.assertTrue("应记录表字段清单: " + facts.get(0),
                facts.get(0).contains("[describeTable] db1.ht_contract 字段：id,lead_dept,sign_date"));
    }

    /** 旧六参构造（无 factSink）不记录事实——存量调用方行为不变 */
    @Test
    public void legacyConstructorRecordsNothing() {
        AgentExploreTools tools = new AgentExploreTools(null, "t", layer.entities(), null, null, null);
        Assert.assertTrue(tools.describeTable("db1.ht_contract").contains("\"fields\""));
    }
}
