package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.service.SemanticCatalogTools;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: HierarchyGroupingTest
 * Description: 分级目录（concepts.json hierarchy 字段驱动 metricDetail 两级导航）用例：
 * ① SemanticLayer 解析 hierarchy（dimensionGroups/groupOfDimension，含未入组 null）；
 * ② 候选维度超阈值（60）且已配分组 → 默认返回分组摘要（组名+count+preview≤5+note 引导），不出全量清单；
 * ③ group 参数取组内全量（第二级导航）；组名不匹配回落全量+note；
 * ④ dimension_keyword 优先级高于分组摘要（直达检索）；
 * ⑤ 阈值内（≤60）维持全量回显不分簇；未配 hierarchy 超上限回落截断+引导（旧路径兼容）。
 * 纯构造 fixture（同 SemanticCatalogFactsTest 范式），不依赖外部数据源与 Spring 容器
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class HierarchyGroupingTest {

    private static SemanticLayer layer;

    @BeforeClass
    public static void setup() {
        StringBuilder dims = new StringBuilder("[");
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            all.add("dim_time_" + i);
            all.add("dim_org_" + i);
        }
        for (int i = 0; i < 50; i++) {
            all.add("dim_other_" + i);
        }
        for (String d : all) {
            if (dims.length() > 1) {
                dims.append(',');
            }
            dims.append("{\"name\":\"").append(d).append("\",\"display_name\":\"")
                    .append(d).append("\",\"expression\":\"db1.ht_contract.").append(d).append("\"}");
        }
        dims.append(']');
        StringBuilder supported = new StringBuilder();
        for (String d : all) {
            if (supported.length() > 0) {
                supported.append(',');
            }
            supported.append('"').append(d).append('"');
        }
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ht\",\"display_name\":\"合同\",\"table\":\"db1.ht_contract\","
                + "\"fields\":[{\"name\":\"amt\",\"type\":\"Decimal\",\"display_name\":\"金额\"}]}]");
        assets.put("dimensions.json", dims.toString());
        assets.put("metrics.json", "[{\"name\":\"amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(db1.ht_contract.amt)\",\"source_table\":\"db1.ht_contract\","
                + "\"supported_dimensions\":[" + supported + "]}]");
        assets.put("value-domains.json", "{\"domains\":{}}");
        assets.put("concepts.json", "{\"concepts\":[],\"hierarchy\":["
                + "{\"name\":\"时间类\",\"members\":[\"dim_time_0\",\"dim_time_1\",\"dim_time_2\",\"dim_time_3\","
                + "\"dim_time_4\",\"dim_time_5\",\"dim_time_6\",\"dim_time_7\",\"dim_time_8\",\"dim_time_9\"]},"
                + "{\"name\":\"组织类\",\"members\":[\"dim_org_0\",\"dim_org_1\",\"dim_org_2\",\"dim_org_3\","
                + "\"dim_org_4\",\"dim_org_5\",\"dim_org_6\",\"dim_org_7\",\"dim_org_8\",\"dim_org_9\"]}]}");
        layer = SemanticLayer.fromContent(assets);
    }

    // ────────────────────────── 语义层 hierarchy 加载 ──────────────────────────

    /** hierarchy 解析：两组各 10 成员；组内/组外反查正确 */
    @Test
    public void hierarchyLoadedFromConcepts() {
        Assert.assertEquals(2, layer.dimensionGroups().size());
        Assert.assertEquals("时间类", layer.dimensionGroups().get(0).getName());
        Assert.assertEquals(10, layer.dimensionGroups().get(0).getMembers().size());
        Assert.assertEquals("时间类", layer.groupOfDimension("dim_time_3"));
        Assert.assertEquals("组织类", layer.groupOfDimension("dim_org_7"));
        Assert.assertNull("未入组维度应返回 null（目录工具归「其他」桶）", layer.groupOfDimension("dim_other_0"));
    }

    // ────────────────────────── 分组摘要（第一级导航） ──────────────────────────

    /** 70 维（>60 阈值）+已配分组 → 默认返回分组摘要：不出全量清单、组名/count/preview≤5、note 引导取组 */
    @Test
    public void groupSummaryWhenOverThreshold() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", null, null);
        Assert.assertFalse("超阈值已配分组不应再回全量清单: " + r, r.contains("\"supported_dimensions\""));
        Assert.assertTrue("应返回分组摘要数组", r.contains("\"dimension_groups\""));
        Assert.assertTrue(r.contains("\"group\":\"时间类\""));
        Assert.assertTrue(r.contains("\"group\":\"组织类\""));
        Assert.assertTrue("未入组应落「其他」桶", r.contains("\"group\":\"其他\""));
        Assert.assertTrue("时间类应有 count=10", r.contains("\"count\":10"));
        Assert.assertTrue("其他桶应为 50", r.contains("\"count\":50"));
        Assert.assertFalse("preview 每组最多 5 个，不应出现 count 与 preview 矛盾",
                r.contains("\"preview\":[\"dim_time_0\",\"dim_time_1\",\"dim_time_2\",\"dim_time_3\",\"dim_time_4\",\"dim_time_5\"]"));
        Assert.assertTrue("note 应引导 group 取组内全量", r.contains("group="));
    }

    // ────────────────────────── group 参数（第二级导航） ──────────────────────────

    /** group=时间类 → 组内 10 个全量回显 */
    @Test
    public void groupParamReturnsGroupMembers() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", null, "时间类");
        Assert.assertTrue("应回组内全量: " + r, r.contains("\"supported_dimensions\":[\"dim_time_0\""));
        Assert.assertTrue(r.contains("\"dim_time_9\""));
        Assert.assertFalse("不应混入组织类成员", r.contains("dim_org_0"));
        Assert.assertFalse("不应混入未分组维度", r.contains("dim_other_0"));
    }

    /** group 传了不存在的组名 → 回落全量+note（不静默空列表误导模型） */
    @Test
    public void groupParamMissFallsBackToAll() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", null, "不存在的组");
        Assert.assertTrue("组名不匹配应回全量: " + r, r.contains("dim_org_0") && r.contains("dim_other_0"));
        Assert.assertTrue("应有 note 引导组名以摘要为准", r.contains("无命中"));
    }

    // ────────────────────────── 优先级与回落路径 ──────────────────────────

    /** dimension_keyword 命中优先：不分组直达（关键词是精确意图，分组是目录浏览） */
    @Test
    public void keywordTakesPriorityOverGrouping() {
        String r = new SemanticCatalogTools(layer).metricDetail("amt", "dim_org_3", null);
        Assert.assertTrue("关键词应直达过滤: " + r, r.contains("\"supported_dimensions\":[\"dim_org_3\"]"));
        Assert.assertFalse("不应同时触发分组摘要", r.contains("\"dimension_groups\""));
    }

    /** 阈值内（≤60）维持全量回显不分簇——当前资产体量（28~40 维）零行为变化 */
    @Test
    public void underThresholdFullList() {
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ht\",\"display_name\":\"合同\",\"table\":\"db1.ht_contract\","
                + "\"fields\":[{\"name\":\"amt\",\"type\":\"Decimal\",\"display_name\":\"金额\"}]}]");
        assets.put("dimensions.json", "[{\"name\":\"dept\",\"display_name\":\"部门\",\"expression\":\"db1.ht_contract.lead_dept\"}]");
        assets.put("metrics.json", "[{\"name\":\"amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(db1.ht_contract.amt)\",\"source_table\":\"db1.ht_contract\","
                + "\"supported_dimensions\":[\"dept\"]}]");
        assets.put("value-domains.json", "{\"domains\":{}}");
        assets.put("concepts.json", "{\"concepts\":[],\"hierarchy\":[{\"name\":\"组织类\",\"members\":[\"dept\"]}]}");
        SemanticLayer small = SemanticLayer.fromContent(assets);
        String r = new SemanticCatalogTools(small).metricDetail("amt", null, null);
        Assert.assertTrue("阈值内应全量回显不分簇: " + r, r.contains("\"supported_dimensions\":[\"dept\"]"));
        Assert.assertFalse(r.contains("\"dimension_groups\""));
    }

    /** 未配 hierarchy 且超回显上限（120）→ 回落旧路径：截断+note 引导（分组缺失不炸） */
    @Test
    public void noHierarchyFallsBackToTruncation() {
        StringBuilder dims = new StringBuilder("[");
        StringBuilder supported = new StringBuilder();
        for (int i = 0; i < 130; i++) {
            String d = "dim_x_" + i;
            if (dims.length() > 1) {
                dims.append(',');
            }
            dims.append("{\"name\":\"").append(d).append("\",\"display_name\":\"x\",\"expression\":\"db1.t.c\"}");
            if (supported.length() > 0) {
                supported.append(',');
            }
            supported.append('"').append(d).append('"');
        }
        dims.append(']');
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ht\",\"display_name\":\"合同\",\"table\":\"db1.ht_contract\","
                + "\"fields\":[{\"name\":\"amt\",\"type\":\"Decimal\",\"display_name\":\"金额\"}]}]");
        assets.put("dimensions.json", dims.toString());
        assets.put("metrics.json", "[{\"name\":\"amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(db1.ht_contract.amt)\",\"source_table\":\"db1.ht_contract\","
                + "\"supported_dimensions\":[" + supported + "]}]");
        assets.put("value-domains.json", "{\"domains\":{}}");
        assets.put("concepts.json", "{\"concepts\":[],\"hierarchy\":[]}");
        SemanticLayer noHierarchy = SemanticLayer.fromContent(assets);
        String r = new SemanticCatalogTools(noHierarchy).metricDetail("amt", null, null);
        Assert.assertTrue("无分组超上限应回落截断: " + r, r.contains("已截断"));
        Assert.assertTrue("note 应同时引导关键词与分组", r.contains("dimension_keyword"));
        Assert.assertFalse("未配分组不应出摘要数组", r.contains("\"dimension_groups\""));
    }

    // ───────────── LLM 合并根重建（ConceptsSupport.rebuildRoot：hierarchy 单源化——恒保留现存，赋组不落库） ─────────────

    /** LLM 来项携 hierarchy 也不落库：分级目录是实体列级归类的派生视图，不旁路第二份真源；
     *  背景：2026-08-24 归类单源化改造，原「来项非空采用本次」语义废弃 */
    @Test
    public void rebuildIgnoresIncomingHierarchy() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode oldH = om.readTree(
                "[{\"name\":\"时间类\",\"members\":[\"dw_cdt\"]}]");
        com.fasterxml.jackson.databind.node.ObjectNode root =
                com.bidr.insight.smartquery.meta.ConceptsSupport.rebuildRoot(oldH, om.createArrayNode());
        Assert.assertEquals("1.0", root.path("schema_version").asText());
        Assert.assertEquals("现存派生目录应保留", "时间类",
                root.path("hierarchy").get(0).path("name").asText());
    }

    /** 现存目录非空 → 整体保留（实体归类派生成果不被合并冲掉），概念条目正常合入 */
    @Test
    public void rebuildKeepsOldHierarchy() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode oldH = om.readTree(
                "[{\"name\":\"时间类\",\"members\":[\"dw_cdt\",\"dw_cdt_2\"]}]");
        // 模拟认证合并产物（概念条目由主干 merge 构建，rebuild 只负责包装根）
        com.fasterxml.jackson.databind.node.ArrayNode merged = om.createArrayNode();
        merged.add(om.readTree("{\"name\":\"传统项目\",\"certified\":false}"));
        com.fasterxml.jackson.databind.node.ObjectNode root =
                com.bidr.insight.smartquery.meta.ConceptsSupport.rebuildRoot(oldH, merged);
        Assert.assertEquals(1, root.path("hierarchy").size());
        Assert.assertEquals("时间类", root.path("hierarchy").get(0).path("name").asText());
        Assert.assertEquals("概念条目正常合入", 1, root.path("concepts").size());
    }

    /** 无现存目录 → 输出空数组（语义层按未配置处理，回落全量回显） */
    @Test
    public void rebuildEmptyWhenNoOldHierarchy() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode root =
                com.bidr.insight.smartquery.meta.ConceptsSupport.rebuildRoot(null, om.createArrayNode());
        Assert.assertTrue(root.path("hierarchy").isArray());
        Assert.assertEquals(0, root.path("hierarchy").size());
    }

    // ───────────── 悬空概念同步清理（ConceptsSupport.dropDanglingConcepts：派生件跟随维度单源） ─────────────

    /** 展开到已移除维度的概念被删、有效概念与 hierarchy 原样保留：
     *  背景：2026-08-26 bidr_isgc 列禁用后维度重派生移除，但 LLM 当时（该维度还在骨架）
     *  写的概念「台账总包项目」仍展开到它，发布校验报悬空；实体保存同步清理口径同一键修复 */
    @Test
    public void dropDanglingConceptsFollowsDimensionRederive() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree("{\"schema_version\":\"1.0\",\"concepts\":["
                + "{\"name\":\"台账总包项目\",\"entity\":\"opp\",\"expands_to\":{\"dimension\":\"ods_om_opportunity_ddf_bidr_isgc\",\"operator\":\"=\",\"value\":\"1\"}},"
                + "{\"name\":\"活跃项目\",\"entity\":\"opp\",\"expands_to\":{\"dimension\":\"manage_status\",\"operator\":\"=\",\"value\":\"活跃\"}}],"
                + "\"hierarchy\":[{\"name\":\"状态类\",\"members\":[\"manage_status\"]}]}");
        java.util.List<String> dropped = com.bidr.insight.smartquery.meta.ConceptsSupport.dropDanglingConcepts(
                root, new java.util.HashSet<>(java.util.Collections.singletonList("manage_status")));
        Assert.assertEquals("悬空概念被删且报名", java.util.Collections.singletonList("台账总包项目"), dropped);
        Assert.assertEquals("有效概念保留", 1, root.path("concepts").size());
        Assert.assertEquals("活跃项目", root.path("concepts").get(0).path("name").asText());
        Assert.assertEquals("hierarchy 原样保留", 1, root.path("hierarchy").size());
    }
}
