package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.sqlgen.SqlGenException;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: MultiValueDimensionTest
 * Description: 多值维度（match=multi，逗号分隔 code 串）过滤改写回归（2026-08-25 背景：
 * AI 评审发现 business_types/phase_codes 类多值 code 列设为 dimension 后等值过滤失真——
 * 多值列是合法维度形态，等值过滤须改写 FIND_IN_SET 包含匹配，不能整串等值漏行）：
 * 等值→FIND_IN_SET 包含、in→OR 组、比较类显式拒绝不静默失真、普通维度零影响
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class MultiValueDimensionTest {

    private static SqlGenerator generator;

    @BeforeClass
    public static void setup() {
        Map<String, String> assets = new HashMap<>();
        assets.put("entities.json", "["
                + "{\"name\":\"Contract\",\"display_name\":\"合同表\",\"table\":\"dw_test.t_contract\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"amt\",\"display_name\":\"合同额\"},"
                + "{\"name\":\"business_types\",\"display_name\":\"业务类型\",\"multi_value\":true}]}"
                + "]");
        assets.put("metrics.json", "["
                + "{\"name\":\"m_amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_contract\",\"formula\":\"SUM(dw_test.t_contract.amt)\"}"
                + "]");
        assets.put("dimensions.json", "["
                + "{\"name\":\"d_y\",\"display_name\":\"年份\",\"expression\":\"dw_test.t_contract.dy\"},"
                + "{\"name\":\"d_btypes\",\"display_name\":\"业务类型\",\"expression\":\"dw_test.t_contract.business_types\",\"match\":\"multi\"}"
                + "]");
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public Map<String, String> assetsFor(String agentCode) {
                return Collections.emptyMap();
            }
        });
        generator = new SqlGenerator(registry);
    }

    private static FilterNode filter(String dim, String op, Object value) {
        FilterNode leaf = new FilterNode();
        leaf.setDimension(dim);
        leaf.setOperator(op);
        leaf.setValue(value);
        FilterNode root = new FilterNode();
        root.setConditions(Collections.singletonList(leaf));
        return root;
    }

    private static SemanticQuery queryWithFilter(String dim, String op, Object value) {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_amt"));
        sq.setFilters(filter(dim, op, value));
        return sq;
    }

    /** 等值过滤改写：多值维度 = 转 FIND_IN_SET 包含匹配（整串等值会漏「A,B」行），参数化绑定且出提示 */
    @Test
    public void equalsRewrittenToFindInSet() {
        GenResult gen = generator.generate(queryWithFilter("d_btypes", "=", "BT01"));
        Assert.assertTrue("等值改写为 FIND_IN_SET 包含", gen.getSql().contains("FIND_IN_SET(?, "));
        Assert.assertFalse("不出现整串等值", gen.getSql().contains("`business_types` = ?"));
        Assert.assertEquals("值参数化绑定", "BT01", gen.getParams().get(gen.getParams().size() - 2));
        Assert.assertTrue("notes 注明改写口径",
                gen.getNotes().stream().anyMatch(n -> n.contains("FIND_IN_SET")));
    }

    /** in 过滤改写：多值 in 转 FIND_IN_SET OR 组（任一命中即命中），逐值参数化 */
    @Test
    public void inRewrittenToFindInSetOrGroup() {
        GenResult gen = generator.generate(queryWithFilter("d_btypes", "in", Arrays.asList("BT01", "BT02")));
        String sql = gen.getSql();
        Assert.assertEquals("两值各一条 FIND_IN_SET", 2, countOf(sql, "FIND_IN_SET(?,"));
        Assert.assertTrue("OR 组连接", sql.contains("FIND_IN_SET(?, ") && sql.contains(" OR "));
        Assert.assertFalse("不出现 IN (", sql.contains("`business_types` IN ("));
    }

    /** 比较类显式拒绝：多值列上 > /between 语义不成立，报错不静默退化为整串比较 */
    @Test
    public void comparisonOperatorRejected() {
        try {
            generator.generate(queryWithFilter("d_btypes", ">", "BT01"));
            Assert.fail("多值维度比较类过滤应拒绝");
        } catch (SqlGenException e) {
            Assert.assertTrue(e.getMessage().contains("多值维度"));
        }
    }

    /** 普通维度零影响：未标 match=multi 的维度等值仍是整串等值（旧行为不变） */
    @Test
    public void plainDimensionUnchanged() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_amt"));
        sq.setDimensions(Collections.singletonList("d_y"));
        sq.setFilters(filter("d_y", "=", 2026));
        GenResult gen = generator.generate(sq);
        Assert.assertFalse("普通维度不改写", gen.getSql().contains("FIND_IN_SET"));
        Assert.assertTrue(gen.getSql().contains("= ?"));
    }

    private static int countOf(String sql, String token) {
        int count = 0;
        int i = 0;
        while ((i = sql.indexOf(token, i)) >= 0) {
            count++;
            i += token.length();
        }
        return count;
    }
}
