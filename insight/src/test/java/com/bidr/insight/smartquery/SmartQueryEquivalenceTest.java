package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * Title: SmartQueryEquivalenceTest
 * Description: Java 移植与 Python 引擎的双跑等价性测试。金样由 Python 侧
 * gen_golden.py（validate_query + sql_gen）对 4 道典型题固化生成：
 * G1 码值域过滤 / G2 order_by+limit / G3 scope_filter 半连接 / G4 list DISTINCT。
 * 比对粒度：SQL 逐字符（%s→? 归一）+ 参数序列 + notes + translate + columns。
 * 金样更新流程：改 Python 真源 → 重跑 gen_golden.py → 提交新 golden.json
 *
 * @author Sharp
 * @since 2026/8/18
 */
public class SmartQueryEquivalenceTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SemanticLayer layer;
    private static SemanticQueryValidator validator;
    private static SqlGenerator generator;
    private static JsonNode golden;

    @BeforeClass
    public static void setup() throws Exception {
        layer = new SemanticLayer();
        layer.init();
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public Map<String, String> assetsFor(String agentCode) {
                return java.util.Collections.emptyMap();
            }
        });
        validator = new SemanticQueryValidator(registry);
        generator = new SqlGenerator(registry);
        try (InputStream in = SmartQueryEquivalenceTest.class
                .getResourceAsStream("/smartquery/golden.json")) {
            Assert.assertNotNull("golden.json 缺失，请先运行 gen_golden.py", in);
            golden = OM.readTree(in);
        }
    }

    @Test
    public void sqlEquivalentToPythonGolden() throws Exception {
        Iterator<Map.Entry<String, JsonNode>> it = golden.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String caseName = entry.getKey();
            JsonNode sqNode = entry.getValue().get("semantic_query");
            JsonNode expected = entry.getValue();

            SemanticQuery sq = OM.treeToValue(sqNode, SemanticQuery.class);

            // 校验结论一致
            ValidationResult vr = validator.validate(sq, sqNode);
            boolean expectedValid = expected.get("validation").get("valid").asBoolean();
            Assert.assertEquals(caseName + " 校验结论不一致: " + vr.getErrors(),
                    expectedValid, vr.isValid());
            if (!expectedValid) {
                continue;
            }

            GenResult gen;
            try {
                gen = generator.generate(sq);
            } catch (RuntimeException e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                Assert.fail(caseName + " generate 异常: " + sw);
                return;
            }
            JsonNode expectedGen = expected.get("gen");

            // SQL 逐字符比对（Python %s → JDBC ? 归一）
            String expectedSql = expectedGen.get("sql").asText().replace("%s", "?");
            Assert.assertEquals(caseName + " SQL 不一致", expectedSql, gen.getSql());

            // 参数序列比对
            JsonNode expectedParams = expectedGen.get("params");
            Assert.assertEquals(caseName + " 参数个数不一致",
                    expectedParams.size(), gen.getParams().size());
            for (int i = 0; i < expectedParams.size(); i++) {
                Assert.assertEquals(caseName + " 参数[" + i + "]不一致",
                        expectedParams.get(i).asText(), String.valueOf(gen.getParams().get(i)));
            }

            // notes 比对
            JsonNode expectedNotes = expectedGen.get("notes");
            Assert.assertEquals(caseName + " notes 个数不一致",
                    expectedNotes.size(), gen.getNotes().size());
            for (int i = 0; i < expectedNotes.size(); i++) {
                Assert.assertEquals(caseName + " notes[" + i + "]不一致",
                        expectedNotes.get(i).asText(), gen.getNotes().get(i));
            }

            // translate 比对
            JsonNode expectedTranslate = expectedGen.get("translate");
            Assert.assertEquals(caseName + " translate 大小不一致",
                    expectedTranslate.size(), gen.getTranslate().size());
            Iterator<Map.Entry<String, JsonNode>> ti = expectedTranslate.fields();
            while (ti.hasNext()) {
                Map.Entry<String, JsonNode> te = ti.next();
                Assert.assertEquals(caseName + " translate[" + te.getKey() + "]不一致",
                        te.getValue().asText(), gen.getTranslate().get(te.getKey()));
            }

            // columns 比对（alias/kind/display）
            JsonNode expectedColumns = expectedGen.get("columns");
            Assert.assertEquals(caseName + " columns 个数不一致",
                    expectedColumns.size(), gen.getColumns().size());
            for (int i = 0; i < expectedColumns.size(); i++) {
                JsonNode ec = expectedColumns.get(i);
                GenResult.ColumnInfo ac = gen.getColumns().get(i);
                Assert.assertEquals(caseName + " columns[" + i + "].alias",
                        ec.get("alias").asText(), ac.getAlias());
                Assert.assertEquals(caseName + " columns[" + i + "].kind",
                        ec.get("kind").asText(), ac.getKind());
                Assert.assertEquals(caseName + " columns[" + i + "].display",
                        ec.get("display").asText(), ac.getDisplay());
            }
        }
    }

    @Test
    public void invalidQueryBlocked() {
        // §6.1.1 未定义指标
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(java.util.Collections.singletonList("not_a_metric"));
        ValidationResult vr = validator.validate(sq, OM.createObjectNode());
        Assert.assertFalse(vr.isValid());
        Assert.assertEquals("§6.1.1", vr.getErrors().get(0).getRule());

        // §6.3.7 filters 中禁止引用指标名
        com.bidr.insight.smartquery.model.FilterNode leaf =
                new com.bidr.insight.smartquery.model.FilterNode();
        leaf.setDimension("contract_amount");
        leaf.setOperator("=");
        leaf.setValue("x");
        com.bidr.insight.smartquery.model.FilterNode root =
                new com.bidr.insight.smartquery.model.FilterNode();
        root.setConditions(java.util.Collections.singletonList(leaf));
        SemanticQuery sq2 = new SemanticQuery();
        sq2.setMetrics(java.util.Collections.singletonList("contract_amount"));
        sq2.setFilters(root);
        ValidationResult vr2 = validator.validate(sq2, OM.createObjectNode());
        Assert.assertFalse(vr2.isValid());
        boolean has637 = vr2.getErrors().stream().anyMatch(e -> "§6.3.7".equals(e.getRule()));
        Assert.assertTrue("应命中 §6.3.7", has637);
    }
}
