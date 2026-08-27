package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.adapter.StatisticPayloadAdapter;
import com.bidr.insight.smartquery.derive.IndicatorDeriver;
import com.bidr.insight.smartquery.derive.InteractionMerger;
import com.bidr.insight.smartquery.derive.PortalConfigDeriver;
import com.bidr.insight.smartquery.derive.StatisticResConverter;
import com.bidr.insight.smartquery.exec.SmartQueryJdbcExecutor;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.QueryRows;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.SmartQueryResult;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.service.SmartQueryService;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: SmartQueryEndpointFlowTest
 * Description: 三端点融合链路端到端测试（不依赖外部数据源）：
 * plan 推导（chartMode 推断/indicator/portalConfig）→ statistic 条件合并
 * （白名单/label→码值/重校验）→ table 明细切换（list 模式）→ payload→StatisticRes 形状。
 * 金样：垫资 Top30（rankingBar）与职称分布（metricsPie/bar 单维度）
 *
 * @author Sharp
 * @since 2026/8/18
 */
public class SmartQueryEndpointFlowTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SemanticLayer layer;
    private static SmartQueryParser parser;
    private static SemanticQueryValidator validator;
    private static SqlGenerator generator;
    private static SmartQueryService service;
    private static InteractionMerger merger;
    private static IndicatorDeriver indicatorDeriver;
    private static PortalConfigDeriver portalConfigDeriver;
    private static StatisticResConverter converter;
    private static StatisticPayloadAdapter adapter;

    @BeforeClass
    public static void setup() {
        layer = new SemanticLayer();
        layer.init();
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public java.util.Map<String, String> assetsFor(String agentCode) {
                return java.util.Collections.emptyMap();
            }
        });
        parser = new SmartQueryParser();
        validator = new SemanticQueryValidator(registry);
        generator = new SqlGenerator(registry);
        service = new SmartQueryService(parser, validator, generator,
                new SmartQueryJdbcExecutor(), new StatisticPayloadAdapter(registry));
        merger = new InteractionMerger(registry);
        indicatorDeriver = new IndicatorDeriver(registry);
        portalConfigDeriver = new PortalConfigDeriver(registry);
        converter = new StatisticResConverter();
        adapter = new StatisticPayloadAdapter(registry);
    }

    /** 垫资 Top30（g2）：plan 推导 rankingBar → 穿透条件合并 → 重校验 + SQL 生成 */
    @Test
    public void rankingTop30PlanAndDrillMerge() throws Exception {
        String ctx = "{\"metrics\":[\"payment_amount\"],\"dimensions\":[\"om_project_code\"],"
                + "\"order_by\":[{\"field\":\"payment_amount\",\"direction\":\"desc\"}],\"limit\":30}";

        // plan：dryRun 校验+SQL，chartMode 推断，indicator/portalConfig 推导
        SmartQueryResult dry = service.dryRun(ctx);
        Assert.assertTrue("垫资Top30 校验应通过: " + dry.getErrors(), dry.isValid());
        SemanticQuery sq = parser.parse(ctx).getQuery();
        Assert.assertEquals("rankingBar", indicatorDeriver.inferChartMode(sq));

        Map<String, Object> cfg = indicatorDeriver.derive(sq, "rankingBar", "垫资最严重的项目");
        Assert.assertNull("排行榜无一级维度", cfg.get("firstDimension"));
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) ((List<?>) cfg.get("dataMetrics")).get(0);
        Assert.assertEquals("om_project_code", row.get("groupByField"));
        Assert.assertEquals(Integer.valueOf(30), row.get("topN"));
        Assert.assertEquals("desc → UI 倒序 1", 1, row.get("sortOrder"));
        Assert.assertEquals("payment_amount", row.get("dataField"));

        PortalWithColumnsRes pc = portalConfigDeriver.derive(sq, dry.getColumns(), "垫资最严重的项目");
        Assert.assertEquals("insight/smart-query", pc.getUrl());
        Assert.assertEquals("1", pc.getReadOnly());
        Assert.assertEquals(dry.getColumns().size(), pc.getColumns().size());
        Assert.assertTrue(pc.getAssociates().isEmpty());

        // statistic：穿透条件（经营模式=传统，label 需翻译为码值 0）合并
        AdvancedStatisticReq req = new AdvancedStatisticReq();
        AdvancedQuery root = new AdvancedQuery();
        root.setConditionList(new ArrayList<>(Collections.singletonList(
                cond("manage_mode", 1, "传统"))));
        req.setCondition(root);
        req.setLimit(30);
        merger.mergeStatistic(sq, req);

        String merged = OM.writeValueAsString(sq);
        SmartQueryParser.ParseResult reparsed = parser.parse(merged);
        ValidationResult vr = validator.validate(reparsed.getQuery(), reparsed.getRaw());
        Assert.assertTrue("合并后应通过全量重校验: " + vr.getErrors(), vr.isValid());
        GenResult gen = generator.generate(reparsed.getQuery());
        Assert.assertTrue("码值翻译后 SQL 参数应含 '0'", gen.getParams().contains("0"));
        Assert.assertEquals("重拉不缩小范围：原 limit 保留", Integer.valueOf(30), reparsed.getQuery().getLimit());
    }

    /** 职称分布（g1 变体）：metricsPie 形状 + 条件合并 + table 端点切 list 模式 */
    @Test
    public void titleDistributionPieAndDrillTable() throws Exception {
        String ctx = "{\"metrics\":[\"user_count\"],\"dimensions\":[\"professional_type_name\"]}";
        SemanticQuery sq = parser.parse(ctx).getQuery();
        Assert.assertEquals("bar", indicatorDeriver.inferChartMode(sq));

        // statistic：追加 user_status=在职（码值 1）增量条件
        AdvancedStatisticReq req = new AdvancedStatisticReq();
        AdvancedQuery root = new AdvancedQuery();
        root.setConditionList(new ArrayList<>(Collections.singletonList(
                cond("user_status", 1, "在职"))));
        req.setCondition(root);
        merger.mergeStatistic(sq, req);
        String merged = OM.writeValueAsString(sq);
        SmartQueryParser.ParseResult reparsed = parser.parse(merged);
        Assert.assertTrue(validator.validate(reparsed.getQuery(), reparsed.getRaw()).isValid());
        GenResult gen = generator.generate(reparsed.getQuery());
        Assert.assertTrue("user_status 应翻译为码值 '1'", gen.getParams().contains("1"));

        // table：metric 查询切 list 模式（实体=首指标源表实体，补维度列，去聚合）
        SemanticQuery sq2 = parser.parse(merged).getQuery();
        AdvancedQueryReq drill = new AdvancedQueryReq();
        AdvancedQuery drillRoot = new AdvancedQuery();
        drillRoot.setConditionList(new ArrayList<>(Collections.singletonList(
                cond("professional_type_name", 1, "高级"))));
        drill.setCondition(drillRoot);
        merger.merge(sq2, drill);
        toListModeForTest(sq2);
        Assert.assertEquals("list", sq2.getQueryType());
        Assert.assertNotNull(sq2.getEntity());
        Assert.assertNull("明细模式不应保留指标", sq2.getMetrics());
        Assert.assertNull("明细模式不应保留 having", sq2.getHaving());
        sq2.setLimit(20);
        SmartQueryParser.ParseResult rp2 = parser.parse(OM.writeValueAsString(sq2));
        ValidationResult vr2 = validator.validate(rp2.getQuery(), rp2.getRaw());
        Assert.assertTrue("list 明细查询应通过校验: " + vr2.getErrors(), vr2.isValid());
        GenResult gen2 = generator.generate(rp2.getQuery());
        Assert.assertFalse(gen2.getSql().isEmpty());
    }

    /** 超纲交互拒绝：白名单外字段 / 不支持的 relation */
    @Test
    public void outOfScopeInteractionRejected() {
        SemanticQuery sq = parser.parse(
                "{\"metrics\":[\"user_count\"],\"dimensions\":[\"professional_type_name\"]}").getQuery();
        AdvancedQueryReq req = new AdvancedQueryReq();
        AdvancedQuery root = new AdvancedQuery();
        root.setConditionList(new ArrayList<>(Collections.singletonList(
                cond("not_a_dim", 1, "x"))));
        req.setCondition(root);
        try {
            merger.merge(sq, req);
            Assert.fail("白名单外字段应被拒绝");
        } catch (NoticeException expected) {
            Assert.assertTrue(expected.getMessage().contains("not_a_dim"));
        }

        SemanticQuery sq2 = parser.parse(
                "{\"metrics\":[\"user_count\"],\"dimensions\":[\"professional_type_name\"]}").getQuery();
        AdvancedQueryReq req2 = new AdvancedQueryReq();
        AdvancedQuery root2 = new AdvancedQuery();
        root2.setConditionList(new ArrayList<>(Collections.singletonList(
                cond("user_status", 9, "在"))));
        req2.setCondition(root2);
        try {
            merger.merge(sq2, req2);
            Assert.fail("LIKE 关系应被拒绝（引擎无对应操作符）");
        } catch (NoticeException expected) {
            // ok
        }
    }

    /** payload → StatisticRes 形状：metricsPie 单行 + rankingBar 扁平（§45.5 双金样） */
    @Test
    public void payloadConvertsToStatisticRes() {
        // 标准嵌套（职称分布形状）：顶层合计 + children=N 维度值
        GenResult gen = new GenResult();
        gen.getColumns().add(new GenResult.ColumnInfo("professional_type_name", "dimension", "职称"));
        gen.getColumns().add(new GenResult.ColumnInfo("user_count", "metric", "人数"));
        QueryRows rows = new QueryRows();
        rows.getRows().add(Arrays.<Object>asList("高级", 13));
        rows.getRows().add(Arrays.<Object>asList("正高", 8));
        rows.getRows().add(Arrays.<Object>asList("中级", 5));
        List<Map<String, Object>> std = adapter.toStandardPayload(gen, rows);
        List<StatisticRes> stdRes = converter.convert(std);
        Assert.assertEquals(3, stdRes.size());
        Assert.assertEquals("professional_type_name", stdRes.get(0).getMetricColumn());
        Assert.assertEquals("高级", stdRes.get(0).getMetric());
        Assert.assertEquals(new BigDecimal("13"), stdRes.get(0).getStatistic());
        Assert.assertEquals("人数", stdRes.get(0).getChildren().get(0).getMetric());

        // 扁平（垫资 Top30 形状）
        List<Map<String, Object>> flat = adapter.toRankingPayload(gen, rows);
        List<StatisticRes> flatRes = converter.convert(flat);
        Assert.assertEquals(3, flatRes.size());
        Assert.assertTrue("扁平形状无 children", flatRes.get(0).getChildren().isEmpty());
        Assert.assertEquals(new BigDecimal("13"), flatRes.get(0).getStatistic());
    }

    // ── 与 SmartQueryPlanController.toListMode 同逻辑的测试副本 ──

    /** 叶子条件构造辅助（AdvancedQuery 无 (String,Integer,List) 匹配构造） */
    private AdvancedQuery cond(String property, int relation, Object... values) {
        AdvancedQuery c = new AdvancedQuery();
        c.setProperty(property);
        c.setRelation(relation);
        c.setValue(new ArrayList<>(Arrays.asList(values)));
        return c;
    }

    private boolean isFieldOf(com.bidr.insight.smartquery.layer.EntityDef ent, String col) {
        for (com.bidr.insight.smartquery.layer.EntityDef.EntityFieldDef f
                : ent.getFields() == null ? new ArrayList<com.bidr.insight.smartquery.layer.EntityDef.EntityFieldDef>()
                : ent.getFields()) {
            if (col.equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

    private void toListModeForTest(SemanticQuery sq) {
        com.bidr.insight.smartquery.layer.MetricDef m = layer.metricMap().get(sq.getMetrics().get(0));
        String entName = layer.tableToEntity().get(m.getSourceTable());
        com.bidr.insight.smartquery.layer.EntityDef ent = layer.entityMap().get(entName);
        List<String> fields = new ArrayList<>();
        for (String f : ent.getDisplayFields() == null ? new ArrayList<String>() : ent.getDisplayFields()) {
            if (isFieldOf(ent, f)) {
                fields.add(f);
            }
        }
        for (String dim : sq.getDimensions() == null ? new ArrayList<String>() : sq.getDimensions()) {
            com.bidr.insight.smartquery.layer.DimensionDef d = layer.dimensionMap().get(dim);
            if (d == null || d.getExpression() == null || !entName.equals(layer.dimEntityOfOrNull(dim))) {
                continue;
            }
            String col = SemanticLayer.splitExpr(d.getExpression())[1];
            if (isFieldOf(ent, col) && !fields.contains(col)) {
                fields.add(col);
            }
        }
        sq.setQueryType("list");
        sq.setEntity(entName);
        sq.setFields(fields.isEmpty() ? null : fields);
        sq.setMetrics(null);
        sq.setDimensions(null);
        sq.setHaving(null);
        sq.setWindow(null);
        sq.setScopeFilter(null);
        sq.setTime(null);
        sq.setOrderBy(null);
    }
}
