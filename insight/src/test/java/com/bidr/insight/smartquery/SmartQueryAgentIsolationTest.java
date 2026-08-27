package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import com.bidr.kernel.exception.NoticeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

/**
 * Title: SmartQueryAgentIsolationTest
 * Description: 多 Agent 隔离测试（§54）：icms Agent 语义层只认 DB 已发布资产
 * （单测无 Spring 上下文，打桩把 classpath /smartquery-icms/ 内容读入内存
 * 模拟发布快照），绑定后 SQL 只落在 fpim 库 icms 表且自动
 * 追加实体 default_filters（enable=1 且 validate_status=1）；无发布资产的 Agent
 * 拒绝而非降级；默认 Agent（Doris 资产）完全不受影响。
 *
 * @author Sharp
 * @since 2026/8/18
 */
public class SmartQueryAgentIsolationTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SemanticLayer defaultLayer;
    private static SemanticLayerRegistry registry;
    private static SemanticQueryValidator validator;
    private static SqlGenerator generator;

    @BeforeClass
    public static void setup() {
        defaultLayer = new SemanticLayer();
        defaultLayer.init();
        registry = new SemanticLayerRegistry(defaultLayer, new AgentAssetCacheService() {
            @Override
            public java.util.Map<String, String> assetsFor(String agentCode) {
                // 单测无 Spring 上下文：icms 把 classpath 内容读入内存模拟 DB 发布快照，
                // 其余 Agent 恒空（验证未发布即拒绝）
                if (!"icms".equals(agentCode)) {
                    return java.util.Collections.emptyMap();
                }
                java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                for (String file : new String[]{"entities.json", "relations.json", "metrics.json",
                        "dimensions.json", "value-domains.json", "concepts.json"}) {
                    String content = readResource("/smartquery-icms/" + file);
                    if (content != null) {
                        m.put(file, content);
                    }
                }
                return m;
            }
        });
        validator = new SemanticQueryValidator(registry);
        generator = new SqlGenerator(registry);
    }

    /** 读 classpath 资源为字符串（不存在返回 null） */
    private static String readResource(String path) {
        try (java.io.InputStream in = SmartQueryAgentIsolationTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("测试资产读取失败: " + path, e);
        }
    }

    /** icms 语义层独立加载：4 实体 + 含 plan_task_count / hazard_confirmed_count 指标 */
    @Test
    public void icmsLayerLoadsIndependently() {
        SemanticLayer icms = registry.get("icms");
        Assert.assertEquals(4, icms.entityMap().size());
        Assert.assertTrue(icms.metricMap().containsKey("plan_task_count"));
        Assert.assertTrue(icms.metricMap().containsKey("hazard_confirmed_count"));
        // 与默认层互不串库：默认层不含 icms 实体表
        Assert.assertFalse(defaultLayer.tableToEntity()
                .containsKey("fpim.icms_schedule_plan"));
    }

    /** 绑定 icms 后：校验 + SQL 生成全部路由到 icms 语义层 */
    @Test
    public void bindIcmsRoutesSqlToIcmsTables() {
        registry.bind("icms");
        try {
            Assert.assertEquals("icms", registry.currentAgentCode());
            SemanticQuery sq = new SemanticQuery();
            sq.setMetrics(Collections.singletonList("plan_task_count"));

            ValidationResult vr = validator.validate(sq, OM.valueToTree(sq));
            Assert.assertTrue("icms 指标校验应通过: " + vr.getErrors(), vr.isValid());

            GenResult r = generator.generate(sq);
            Assert.assertTrue("SQL 应落在 fpim 库: " + r.getSql(),
                    r.getSql().contains("`fpim`.`icms_schedule_plan`"));
            Assert.assertTrue("应追加 default_filters enable=1: " + r.getSql(),
                    r.getSql().contains("`enable` = 1"));
            Assert.assertTrue("应追加 default_filters validate_status=1: " + r.getSql(),
                    r.getSql().contains("`validate_status` = 1"));
            Assert.assertFalse("icms 表无 dy 列，不应生成快照年子查询: " + r.getSql(),
                    r.getSql().contains("MAX(dy)"));
        } finally {
            registry.clear();
        }
    }

    /** 解绑后回到默认 Agent：默认层指标可用、icms 指标不可见 */
    @Test
    public void clearRestoresDefaultAgent() {
        registry.bind("icms");
        registry.clear();
        Assert.assertEquals(SemanticLayerRegistry.DEFAULT_AGENT, registry.currentAgentCode());
        Assert.assertSame(defaultLayer, registry.current());
    }

    /** 无发布资产的 Agent：直接拒绝（NoticeException），不降级到默认层也不回落 classpath */
    @Test
    public void unknownAgentRejected() {
        try {
            registry.get("no_such_agent");
            Assert.fail("未知 Agent 应抛 NoticeException");
        } catch (NoticeException expected) {
            Assert.assertTrue(expected.getMessage().contains("no_such_agent"));
        }
    }

    /** 缺省/空串 Agent 均解析为默认层 */
    @Test
    public void defaultAliasesResolveToDefaultLayer() {
        Assert.assertSame(defaultLayer, registry.get(null));
        Assert.assertSame(defaultLayer, registry.get(""));
        Assert.assertSame(defaultLayer, registry.get(SemanticLayerRegistry.DEFAULT_AGENT));
    }
}
