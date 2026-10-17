package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.sqlgen.RowPolicyUserContext;
import com.bidr.insight.smartquery.sqlgen.SqlGenException;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: RowPolicyInjectionTest
 * Description: 行级权限渲染期注入回归测试（P2-2）：row-policies 资产在 SqlGenerator
 * WHERE 构建处注入谓词，值参数化绑定（登录态模板 ${user.xxx} 运行期解析）。
 * 核心性质锁死：①注入对 semantic_query 载荷不可见不可绕过（渲染期系统侧注入）；
 * ②fail-closed——配了策略的表无用户上下文/变量无值时拒绝生成，绝不静默放行；
 * ③跨表 composite 两源表子查询各自注入；④无策略表零影响（旧行为不变）
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class RowPolicyInjectionTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SqlGenerator generator;
    private static RowPolicyUserContext userCtx;

    @BeforeClass
    public static void setup() {
        generator = newGenerator(baseAssetsWithPolicies());
        userCtx = new RowPolicyUserContext(1001L, "D01", "zhangsan", "张三",
                Collections.singletonMap("deptCode", "KCY"));
    }

    /** 基础资产 + 行权限：合同表 create_by=登录工号；到款表 owner in [登录工号, 共享岗] */
    private static Map<String, String> baseAssetsWithPolicies() {
        Map<String, String> assets = baseAssets();
        assets.put("row-policies.json", "{"
                + "\"schema_version\":\"1.0\",\"tables\":["
                + "{\"table\":\"dw_test.t_contract\",\"policies\":["
                + "{\"column\":\"create_by\",\"op\":\"=\",\"value\":\"${user.customerNumber}\",\"desc\":\"仅本人创建\"}]},"
                + "{\"table\":\"dw_test.t_receive\",\"policies\":["
                + "{\"column\":\"owner\",\"op\":\"in\",\"value\":[\"${user.customerNumber}\",\"SHARED\"],\"desc\":\"本人或共享岗\"}]}"
                + "]}");
        return assets;
    }

    private static Map<String, String> baseAssets() {
        Map<String, String> assets = new HashMap<>();
        assets.put("entities.json", "["
                + "{\"name\":\"Contract\",\"display_name\":\"合同表\",\"table\":\"dw_test.t_contract\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"dept_code\",\"display_name\":\"部门码\"},"
                + "{\"name\":\"amt\",\"display_name\":\"合同额\"},"
                + "{\"name\":\"create_by\",\"display_name\":\"创建人\"}]},"
                + "{\"name\":\"Receive\",\"display_name\":\"到款表\",\"table\":\"dw_test.t_receive\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"contract_id\",\"display_name\":\"合同ID\"},"
                + "{\"name\":\"dy\",\"display_name\":\"快照年\"},"
                + "{\"name\":\"pay\",\"display_name\":\"到款额\"},"
                + "{\"name\":\"owner\",\"display_name\":\"归属人\"}]}"
                + "]");
        assets.put("relations.json", "["
                + "{\"name\":\"r_receive_contract\",\"from_entity\":\"Receive\",\"to_entity\":\"Contract\","
                + "\"type\":\"many_to_one\",\"join\":[{\"left\":\"contract_id\",\"right\":\"id\"}]}"
                + "]");
        assets.put("metrics.json", "["
                + "{\"name\":\"m_contract_amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_contract\",\"formula\":\"SUM(dw_test.t_contract.amt)\"},"
                + "{\"name\":\"m_balance\",\"display_name\":\"剩余合同额\",\"type\":\"composite\","
                + "\"source_tables\":[\"dw_test.t_contract\",\"dw_test.t_receive\"],"
                + "\"formula\":\"SUM(dw_test.t_contract.amt) - SUM(dw_test.t_receive.pay)\"}"
                + "]");
        assets.put("dimensions.json", "["
                + "{\"name\":\"d_y\",\"display_name\":\"年份\",\"expression\":\"dw_test.t_contract.dy\"}"
                + "]");
        return assets;
    }

    private static SqlGenerator newGenerator(Map<String, String> assets) {
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public Map<String, String> assetsFor(String agentCode) {
                return Collections.emptyMap();
            }
        });
        return new SqlGenerator(registry);
    }

    private static SemanticQuery metricQuery(String... metrics) {
        SemanticQuery sq = new SemanticQuery();
        sq.setMetrics(Arrays.asList(metrics));
        return sq;
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

    /** 单表：谓词注入主表别名 + 参数绑定登录工号 + notes 说明 */
    @Test
    public void singleTablePolicyInjectedAndBound() {
        GenResult gen = generator.generate(metricQuery("m_contract_amt"), userCtx);
        String sql = gen.getSql();
        Assert.assertTrue("主表谓词注入", sql.contains("`create_by` = ?"));
        Assert.assertTrue("参数为登录工号", gen.getParams().contains("D01"));
        Assert.assertTrue("notes 含行权限说明", gen.getNotes().toString().contains("行级权限"));
        // LIMIT 参数在行权限参数之后（顺序：WHERE 参数 → LIMIT）
        Assert.assertEquals("D01", gen.getParams().get(0));
    }

    /** fail-closed：配了策略但无用户上下文（后台链路/未登录）→ 拒绝生成 */
    @Test
    public void failClosedWithoutUserContext() {
        try {
            generator.generate(metricQuery("m_contract_amt"));
            Assert.fail("应抛 SqlGenException");
        } catch (SqlGenException e) {
            Assert.assertTrue("报错说明 fail-closed 与表名",
                    e.getMessage().contains("dw_test.t_contract") && e.getMessage().contains("fail-closed"));
        }
    }

    /** fail-closed：模板变量在登录态无值（如部门未随 token 下发）→ 拒绝生成 */
    @Test
    public void failClosedOnUnresolvableTemplate() {
        Map<String, String> assets = baseAssets();
        assets.put("row-policies.json", "{\"schema_version\":\"1.0\",\"tables\":["
                + "{\"table\":\"dw_test.t_contract\",\"policies\":["
                + "{\"column\":\"dept_code\",\"op\":\"=\",\"value\":\"${user.attr.deptNo}\"}]}]}");
        SqlGenerator g = newGenerator(assets);
        try {
            g.generate(metricQuery("m_contract_amt"), userCtx);
            Assert.fail("应抛 SqlGenException");
        } catch (SqlGenException e) {
            Assert.assertTrue("报错指明无值变量", e.getMessage().contains("deptNo"));
        }
    }

    /** 跨表 composite：两源表子查询各自注入（子查询别名空间内），参数按序绑定 */
    @Test
    public void crossTableCompositeBothSubqueriesInjected() {
        GenResult gen = generator.generate(metricQuery("m_balance"), userCtx);
        String sql = gen.getSql();
        // 合同子查询注入 create_by 等值，到款子查询注入 owner IN（各一各，均在子查询别名空间内）
        Assert.assertEquals("合同子查询注入 create_by", 1, countOf(sql, "`create_by` = ?"));
        Assert.assertTrue("到款子查询注入 owner IN", sql.contains("`owner` IN (?, ?)"));
        // 参数序：D01(create_by) → D01+SHARED(in 数组) → 100(LIMIT)
        Assert.assertEquals("D01", gen.getParams().get(0));
        Assert.assertEquals("in 数组首元素", "D01", gen.getParams().get(1));
        Assert.assertEquals("in 数组次元素", "SHARED", gen.getParams().get(2));
    }

    /** 无 row-policies 资产：零影响（不注入、不报错，旧行为不变） */
    @Test
    public void noPolicyAssetZeroImpact() {
        SqlGenerator g = newGenerator(baseAssets());
        GenResult gen = g.generate(metricQuery("m_contract_amt"), userCtx);
        Assert.assertFalse("无策略不注入谓词", gen.getSql().contains("create_by"));
        GenResult genNoCtx = g.generate(metricQuery("m_contract_amt"));
        Assert.assertFalse("无策略无上下文也不报错", genNoCtx.getSql().contains("create_by"));
    }

    /** 常量 value（无模板）：直接参数化绑定（不解析、不拼字面量） */
    @Test
    public void constantValueBoundAsParameter() {
        Map<String, String> assets = baseAssets();
        assets.put("row-policies.json", "{\"schema_version\":\"1.0\",\"tables\":["
                + "{\"table\":\"dw_test.t_contract\",\"policies\":["
                + "{\"column\":\"dept_code\",\"op\":\"=\",\"value\":\"KCY\"}]}]}");
        SqlGenerator g = newGenerator(assets);
        GenResult gen = g.generate(metricQuery("m_contract_amt"), userCtx);
        Assert.assertTrue(gen.getSql().contains("`dept_code` = ?"));
        Assert.assertTrue("常量走参数而非字面量内联", gen.getParams().contains("KCY"));
        Assert.assertFalse("SQL 不含内联常量", gen.getSql().contains("'KCY'"));
    }

    /** 模板解析单测：内置属性 / attr.KEY 剥前缀 / 混合文本 */
    @Test
    public void templateResolutionVariants() {
        Assert.assertEquals("D01", userCtx.resolve("${user.customerNumber}"));
        Assert.assertEquals("KCY", userCtx.resolve("${user.attr.deptCode}"));
        Assert.assertEquals("dept-KCY-1001", userCtx.resolve("dept-${user.attr.deptCode}-${user.id}"));
        Assert.assertEquals("常量原样返回", "PLAIN", userCtx.resolve("PLAIN"));
    }
}
