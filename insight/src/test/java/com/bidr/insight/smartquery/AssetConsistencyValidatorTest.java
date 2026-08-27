package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.validate.AssetConsistencyValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AssetConsistencyValidatorTest
 * Description: 发布前置校验单测：以 entities 骨架为锚点拦截悬空引用
 * （模拟「取消选表后人工四类残留」场景），error 阻断、warn 不阻断
 *
 * @author Sharp
 * @since 2026/8/19
 */
public class AssetConsistencyValidatorTest {

    /** 骨架：单实体合同表（含 amount/project_id 两列） */
    private static final String ENTITIES = "[{"
            + "\"name\":\"contract\",\"display_name\":\"合同\",\"table\":\"smartpm.t_contract\","
            + "\"primary_key\":[\"id\"],\"listable\":true,\"time_field\":\"create_time\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"bigint\"},"
            + "{\"name\":\"amount\",\"display_name\":\"金额\",\"type\":\"decimal\"},"
            + "{\"name\":\"project_id\",\"type\":\"varchar\",\"value_domain\":\"project\"},"
            + "{\"name\":\"status\",\"type\":\"varchar\",\"value_domain\":\"contract_status\"}"
            + "]}]";

    /** 全部引用落在骨架内的一致资产集 */
    private static Map<String, String> consistentAssets() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("entities", ENTITIES);
        m.put("dimensions", "[{\"name\":\"project\",\"display_name\":\"项目\","
                + "\"expression\":\"smartpm.t_contract.project_id\",\"value_domain\":\"project\"}]");
        m.put("metrics", "[{\"name\":\"contract_amount\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(smartpm.t_contract.amount)\",\"source_table\":\"smartpm.t_contract\","
                + "\"supported_dimensions\":[\"project\"]}]");
        m.put("value-domains", "{\"domains\":{\"project\":{\"P1\":\"项目一\"},"
                + "\"contract_status\":{\"1\":\"生效\"}}}");
        m.put("concepts", "{\"concepts\":[{\"name\":\"大合同\",\"entity\":\"contract\"}]}");
        m.put("sensitive-fields", "{\"fields\":[{\"entity\":\"contract\",\"field\":\"project_id\"}]}");
        return m;
    }

    @Test
    public void consistentAssetsPassWithoutErrors() {
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(consistentAssets());
        Assert.assertFalse("一致资产不应有 error：" + r.errorMessages(), r.hasErrors());
    }

    @Test
    public void danglingRelationsBlocked() {
        Map<String, String> m = consistentAssets();
        // 关系引用了已取消勾选（不在 entities）的实体
        m.put("relations", "[{\"name\":\"contract_project\",\"from_entity\":\"contract\","
                + "\"to_entity\":\"project\",\"join\":[{\"left\":\"project_id\",\"right\":\"id\"}]}]");
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(m);
        Assert.assertTrue(r.hasErrors());
        Assert.assertTrue(r.errorMessages().stream()
                .anyMatch(s -> s.contains("[relations]") && s.contains("project")));
    }

    @Test
    public void danglingMetricSourceTableBlocked() {
        Map<String, String> m = consistentAssets();
        // 指标源表不在实体清单（表取消勾选后的残留）
        m.put("metrics", "[{\"name\":\"orphan\",\"type\":\"atomic\","
                + "\"formula\":\"SUM(smartpm.t_gone.amount)\",\"source_table\":\"smartpm.t_gone\"}]");
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(m);
        Assert.assertTrue(r.hasErrors());
        Assert.assertEquals(2, r.errorMessages().stream()
                .filter(s -> s.startsWith("[metrics]")).count()); // 公式列引用 + source_table 各一条
    }

    @Test
    public void danglingConceptAndSensitiveBlocked() {
        Map<String, String> m = consistentAssets();
        m.put("concepts", "{\"concepts\":[{\"name\":\"幽灵\",\"entity\":\"gone_entity\"}]}");
        m.put("sensitive-fields", "{\"fields\":[{\"entity\":\"gone_entity\",\"field\":\"x\"}]}");
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(m);
        List<String> errs = r.errorMessages();
        Assert.assertTrue(errs.stream().anyMatch(s -> s.startsWith("[concepts]")));
        Assert.assertTrue(errs.stream().anyMatch(s -> s.startsWith("[sensitive-fields]")));
    }

    @Test
    public void missingValueDomainIsErrorForEntityField() {
        Map<String, String> m = consistentAssets();
        // 码值域缺失 contract_status（实体字段引用）→ error；project 保留
        m.put("value-domains", "{\"domains\":{\"project\":{\"P1\":\"项目一\"}}}");
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(m);
        Assert.assertTrue(r.errorMessages().stream()
                .anyMatch(s -> s.startsWith("[value-domains]") && s.contains("contract_status")));
    }

    @Test
    public void emptyEntitiesAndNoMetricsReported() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("entities", "[]");
        m.put("metrics", "[]");
        AssetConsistencyValidator.Result r = AssetConsistencyValidator.validate(m);
        Assert.assertTrue("空实体必须阻断发布", r.hasErrors());
        Assert.assertTrue(r.getIssues().stream()
                .anyMatch(i -> "warn".equals(i.getLevel()) && "metrics".equals(i.getAssetType())));
    }
}
