package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Title: SemanticQueryUnknownKeysTest
 * Description: 条件树未知键显式报错（§6.3.0）边界用例：模型猜错的键（如 field/dim）
 * 必须报出且错误信息含合法键清单；FilterNode 往返序列化的良性冗余
 * （null 值键、group 布尔键、conditions:null）不报——EndpointFlowTest 的
 * mergeStatistic 重校验现场即后者。纯构造，不依赖外部数据源与 Spring 容器
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class SemanticQueryUnknownKeysTest {

    private static SmartQueryParser parser;
    private static SemanticQueryValidator validator;

    @BeforeClass
    public static void setup() {
        SemanticLayer layer = new SemanticLayer();
        layer.init();
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public java.util.Map<String, java.lang.String> assetsFor(String agentCode) {
                return java.util.Collections.emptyMap();
            }
        });
        parser = new SmartQueryParser();
        validator = new SemanticQueryValidator(registry);
    }

    /** 叶子写错键名（field 而非 dimension）：报 §6.3.0 并给出合法键清单（静默丢弃变显式） */
    @Test
    public void leafUnknownKeyReportedWithLegalKeys() {
        String ctx = "{\"query_type\":\"metric\",\"metrics\":[\"payment_amount\"],\"dimensions\":[\"om_project_code\"],"
                + "\"filters\":{\"field\":\"manage_mode\",\"operator\":\"=\",\"value\":\"传统\"}}";
        SmartQueryParser.ParseResult pr = parser.parse(ctx);
        ValidationResult vr = validator.validate(pr.getQuery(), pr.getRaw());
        Assert.assertFalse("未知键应导致校验失败", vr.isValid());
        boolean hit = false;
        for (ValidationResult.Issue i : vr.getErrors()) {
            if ("§6.3.0".equals(i.getRule())) {
                Assert.assertTrue("错误应点名未知键并给合法键清单: " + i.getMessage(),
                        i.getMessage().contains("'field'")
                                && i.getMessage().contains("dimension/metric/operator/value"));
                hit = true;
            }
        }
        Assert.assertTrue("应存在 §6.3.0 错误", hit);
    }

    /** 组节点混入非条件键：同样报出（组节点合法键清单） */
    @Test
    public void groupUnknownKeyReported() {
        String ctx = "{\"query_type\":\"metric\",\"metrics\":[\"payment_amount\"],\"dimensions\":[\"om_project_code\"],"
                + "\"filters\":{\"operator\":\"AND\",\"cond\":[{\"dimension\":\"manage_mode\",\"operator\":\"=\",\"value\":\"传统\"}]}}";
        SmartQueryParser.ParseResult pr = parser.parse(ctx);
        ValidationResult vr = validator.validate(pr.getQuery(), pr.getRaw());
        Assert.assertTrue("组节点未知键 'cond' 应被报出: " + vr.getErrors(),
                vr.getErrors().stream().anyMatch(i -> "§6.3.0".equals(i.getRule())
                        && i.getMessage().contains("'cond'")));
    }

    /** 往返序列化冗余不报：null 值键 + group 布尔键 + conditions:null（mergeStatistic 重校验现场形态） */
    @Test
    public void roundTripRedundancyNotReported() {
        String ctx = "{\"query_type\":\"metric\",\"metrics\":[\"payment_amount\"],\"dimensions\":[\"om_project_code\"],"
                + "\"filters\":{\"operator\":\"AND\",\"conditions\":[{\"dimension\":\"manage_mode\",\"operator\":\"=\","
                + "\"value\":\"传统\",\"metric\":null,\"conditions\":null,\"group\":false}],"
                + "\"dimension\":null,\"metric\":null,\"value\":null,\"group\":true}}";
        SmartQueryParser.ParseResult pr = parser.parse(ctx);
        ValidationResult vr = validator.validate(pr.getQuery(), pr.getRaw());
        Assert.assertTrue("往返冗余键不应报错: " + vr.getErrors(), vr.isValid());
    }
}
