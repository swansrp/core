package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.sqlgen.SqlGenException;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: SmartQueryCrossTableCompositeTest
 * Description: 跨源表 composite 渲染分支回归测试（debug 背景：17min 死循环案例——
 * "剩余合同额=合同额−到款额"跨两事实表，协议引擎旧版直接拒绝，LLM 被迫协议内死磕）。
 * 引擎侧解法：每源表预聚合子查询（粒度=分组维度∪JOIN 键）→ 外层按维度 LEFT JOIN →
 * 公式算术组合，聚合先于 JOIN 无扇出。本测试锁死该口径的 SQL 结构性质。
 * fixture 用 SemanticLayer.fromContent 自构 inline 资产（合同/到款/孤岛表），不依赖真源演进。
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class SmartQueryCrossTableCompositeTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SemanticQueryValidator validator;
    private static SqlGenerator generator;

    @BeforeClass
    public static void setup() {
        Map<String, String> assets = new HashMap<>();
        assets.put("entities.json", "["
                + "{\"name\":\"Contract\",\"display_name\":\"合同表\",\"table\":\"dw_test.t_contract\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"dept_code\",\"display_name\":\"部门码\"},"
                + "{\"name\":\"amt\",\"display_name\":\"合同额\"}]},"
                + "{\"name\":\"Receive\",\"display_name\":\"到款表\",\"table\":\"dw_test.t_receive\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"contract_id\",\"display_name\":\"合同ID\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"pay\",\"display_name\":\"到款额\"}]},"
                + "{\"name\":\"Other\",\"display_name\":\"孤岛表\",\"table\":\"dw_test.t_other\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"val\",\"display_name\":\"某值\"}]}"
                + "]");
        assets.put("relations.json", "["
                + "{\"name\":\"r_receive_contract\",\"from_entity\":\"Receive\",\"to_entity\":\"Contract\","
                + "\"type\":\"many_to_one\",\"join\":[{\"left\":\"contract_id\",\"right\":\"id\"}]}"
                + "]");
        assets.put("metrics.json", "["
                + "{\"name\":\"m_contract_amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_contract\",\"formula\":\"SUM(dw_test.t_contract.amt)\"},"
                + "{\"name\":\"m_receive_amt\",\"display_name\":\"到款额\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_receive\",\"formula\":\"SUM(dw_test.t_receive.pay)\"},"
                + "{\"name\":\"m_balance\",\"display_name\":\"剩余合同额\",\"type\":\"composite\","
                + "\"source_tables\":[\"dw_test.t_contract\",\"dw_test.t_receive\"],"
                + "\"formula\":\"SUM(dw_test.t_contract.amt) - SUM(dw_test.t_receive.pay)\"},"
                + "{\"name\":\"m_other\",\"display_name\":\"孤岛指标\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_other\",\"formula\":\"SUM(dw_test.t_other.val)\"},"
                + "{\"name\":\"m_no_src\",\"display_name\":\"缺声明复合\",\"type\":\"composite\","
                + "\"formula\":\"SUM(dw_test.t_contract.amt) - SUM(dw_test.t_receive.pay)\"}"
                + "]");
        assets.put("dimensions.json", "["
                + "{\"name\":\"d_y\",\"display_name\":\"年份\",\"expression\":\"dw_test.t_contract.dy\"},"
                + "{\"name\":\"d_dept\",\"display_name\":\"部门\",\"expression\":\"dw_test.t_contract.dept_code\"}"
                + "]");
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public Map<String, String> assetsFor(String agentCode) {
                return Collections.emptyMap();
            }
        });
        validator = new SemanticQueryValidator(registry);
        generator = new SqlGenerator(registry);
    }

    private static int countOf(String sql, String token) {
        int count = 0;
        int idx = sql.indexOf(token);
        while (idx >= 0) {
            count++;
            idx = sql.indexOf(token, idx + 1);
        }
        return count;
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

    /** 主用例：剩余合同额按年+部门——双子查询预聚合、维度对齐 LEFT JOIN、缺失侧 COALESCE 0 */
    @Test
    public void crossTableCompositeAlignedSubqueries() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_balance"));
        sq.setDimensions(Arrays.asList("d_y", "d_dept"));
        GenResult gen = generator.generate(sq);
        String sql = gen.getSql();

        // 两个预聚合子查询：各含分组维度输出与各自聚合项，各带 GROUP BY；
        // 按年分组时不套 MAX(dy) 默认（全年份序列展示，与单表口径一致）
        Assert.assertEquals("应有 2 个子查询 GROUP BY", 2, countOf(sql, "GROUP BY"));
        Assert.assertFalse("按年分组不套 MAX(dy) 默认", sql.contains("(SELECT MAX(dy)"));
        Assert.assertTrue("合同子查询聚合项 m1", sql.contains("SUM(`amt`) AS `m1`"));
        Assert.assertTrue("到款子查询聚合项 m2", sql.contains("SUM(`pay`) AS `m2`"));

        // 到款子查询经关系拉合同表取部门维度（d_dept 定义在合同表上）
        Assert.assertTrue("到款子查询 JOIN 合同表", sql.contains("LEFT JOIN `dw_test`.`t_contract`"));

        // 外层：维度对齐 LEFT JOIN + 公式组合（驱动表=排序首表 t_contract，非驱动侧 COALESCE 0）
        Assert.assertTrue("外层维度对齐连接",
                sql.contains("jt1.`d_y` = jt2.`d_y`") && sql.contains("jt1.`d_dept` = jt2.`d_dept`"));
        Assert.assertTrue("公式组合（缺失侧按 0）",
                sql.contains("jt1.`m1` - COALESCE(jt2.`m2`, 0) AS `m_balance`"));

        // 参数仅有 LIMIT（快照年走子查询无参数）
        Assert.assertEquals(1, gen.getParams().size());
        Assert.assertEquals("100", String.valueOf(gen.getParams().get(0)));

        // 列结构：2 维度 + 1 指标
        Assert.assertEquals(3, gen.getColumns().size());
        Assert.assertEquals("dimension", gen.getColumns().get(0).getKind());
        Assert.assertEquals("m_balance", gen.getColumns().get(2).getAlias());
        Assert.assertTrue(gen.getNotes().stream().anyMatch(n -> n.contains("跨源表 composite")));
    }

    /** 多指标不同源（两个 atomic）总量口径：无分组维度 → CROSS JOIN 单行对齐 */
    @Test
    public void crossTableMultiMetricTotalCrossJoin() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Arrays.asList("m_contract_amt", "m_receive_amt"));
        GenResult gen = generator.generate(sq);
        String sql = gen.getSql();

        Assert.assertTrue("总量口径交叉连接", sql.contains("\nCROSS JOIN (\n"));
        Assert.assertFalse("无分组维度不 GROUP BY", sql.contains("GROUP BY"));
        Assert.assertTrue("驱动表指标直引", sql.contains("jt1.`m1` AS `m_contract_amt`"));
        Assert.assertTrue("非驱动表指标 COALESCE", sql.contains("COALESCE(jt2.`m2`, 0) AS `m_receive_amt`"));
    }

    /** 过滤下发：同一过滤条件在每张源表子查询各渲染一次（到款侧经 JOIN 别名） */
    @Test
    public void filtersPushedToBothSubqueries() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_balance"));
        sq.setDimensions(Collections.singletonList("d_dept"));
        sq.setFilters(filter("d_dept", "=", "D01"));
        GenResult gen = generator.generate(sq);
        String sql = gen.getSql();

        Assert.assertEquals("过滤下发两个子查询", 2, countOf(sql, "WHERE"));
        // 不按年分组且无年过滤：每子查询各套一条最新快照年默认（d_y 表达式在合同表，两表自带 dy 均本表渲染）
        Assert.assertEquals("每子查询各带最新快照年默认", 2, countOf(sql, "(SELECT MAX(dy)"));
        Assert.assertEquals("参数：部门码×2 + LIMIT",
                Arrays.asList("D01", "D01", "100"),
                Arrays.asList(String.valueOf(gen.getParams().get(0)),
                        String.valueOf(gen.getParams().get(1)),
                        String.valueOf(gen.getParams().get(2))));
    }

    /** 显式年份过滤：dy 特例——两表各自本表列渲染（到款子查询免拉合同表），无 MAX(dy) 默认 */
    @Test
    public void explicitYearFilterUsesLocalDy() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_balance"));
        sq.setDimensions(Collections.singletonList("d_y"));
        sq.setFilters(filter("d_y", "=", 2024));
        GenResult gen = generator.generate(sq);
        String sql = gen.getSql();

        Assert.assertFalse("显式年份不走 MAX(dy) 默认", sql.contains("MAX(dy)"));
        Assert.assertEquals("两个子查询各有年份条件", 2, countOf(sql, "WHERE"));
        Assert.assertEquals("参数：年×2 + LIMIT", 3, gen.getParams().size());
        Assert.assertEquals("2024", String.valueOf(gen.getParams().get(0)));
    }

    /** 不可达维度：分组维度从某源表无 JOIN 路径时明确拒绝（防粒度错位静默错算） */
    @Test
    public void unreachableDimensionRejected() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Arrays.asList("m_balance", "m_other"));
        sq.setDimensions(Collections.singletonList("d_dept"));
        try {
            generator.generate(sq);
            Assert.fail("孤岛表源 + 合同维度应拒绝");
        } catch (SqlGenException e) {
            Assert.assertTrue("报错须指明不可达维度与源表",
                    e.getMessage().contains("d_dept") && e.getMessage().contains("dw_test.t_other")
                            && e.getMessage().contains("不可达"));
        }
    }

    /** 校验器口径：composite 缺 source_tables 仍拦截（§6.1.4，文案指向跨表支持新口径） */
    @Test
    public void validatorStillRequiresSourceTables() {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Collections.singletonList("m_no_src"));
        ValidationResult vr = validator.validate(sq, OM.createObjectNode());
        Assert.assertFalse(vr.isValid());
        Assert.assertTrue("应命中 §6.1.4",
                vr.getErrors().stream().anyMatch(i -> "§6.1.4".equals(i.getRule())));
    }
}
