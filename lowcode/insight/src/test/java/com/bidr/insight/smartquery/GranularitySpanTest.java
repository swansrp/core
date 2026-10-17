package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.TimeSpec;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
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
 * Title: GranularitySpanTest
 * Description: 时间跨度放宽覆盖月/季粒度回归测试（debug 背景：跨度上限放宽原先只认
 * granularity=year，「近三年每月合同额」按 366 天封顶被 §6.4.5 直接拒掉，而按月/季分组
 * 输出行数同样有界（月≈12 行/年、季≈4 行/年），本质与按年分组一致，应同口径放宽）。
 * 解法：校验器判定从"含年粒度维度"推广为"含年/季/月粒度维度"。
 *
 * @author Sharp
 * @since 2026/8/24
 */
public class GranularitySpanTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static SemanticQueryValidator validator;

    @BeforeClass
    public static void setup() {
        Map<String, String> assets = new HashMap<>();
        assets.put("entities.json", "["
                + "{\"name\":\"Contract\",\"display_name\":\"合同表\",\"table\":\"dw_test.t_contract\","
                + "\"primary_key\":[\"id\"],\"fields\":["
                + "{\"name\":\"id\",\"display_name\":\"主键\"},"
                + "{\"name\":\"sign_date\",\"display_name\":\"签订日期\"},"
                + "{\"name\":\"amt\",\"display_name\":\"合同额\"}]}"
                + "]");
        assets.put("metrics.json", "["
                + "{\"name\":\"m_contract_amt\",\"display_name\":\"合同额\",\"type\":\"atomic\","
                + "\"source_table\":\"dw_test.t_contract\",\"formula\":\"SUM(dw_test.t_contract.amt)\"}"
                + "]");
        assets.put("dimensions.json", "["
                + "{\"name\":\"d_date\",\"display_name\":\"签订日期\",\"expression\":\"dw_test.t_contract.sign_date\"},"
                + "{\"name\":\"d_year\",\"display_name\":\"签订日期（年）\",\"expression\":\"dw_test.t_contract.sign_date\",\"granularity\":\"year\"},"
                + "{\"name\":\"d_quarter\",\"display_name\":\"签订日期（季）\",\"expression\":\"dw_test.t_contract.sign_date\",\"granularity\":\"quarter\"},"
                + "{\"name\":\"d_month\",\"display_name\":\"签订日期（月）\",\"expression\":\"dw_test.t_contract.sign_date\",\"granularity\":\"month\"}"
                + "]");
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public Map<String, String> assetsFor(String agentCode) {
                return Collections.emptyMap();
            }
        });
        validator = new SemanticQueryValidator(registry);
    }

    /** 构造按指定维度分组、三年区间（约 1095 天 > 366）的指标查询 */
    private static SemanticQuery query(String dim) {
        SemanticQuery sq = new SemanticQuery();
        sq.setQueryType("metric");
        sq.setMetrics(Collections.singletonList("m_contract_amt"));
        sq.setDimensions(Collections.singletonList(dim));
        TimeSpec time = new TimeSpec();
        time.setField("dw_test.t_contract.sign_date");
        time.setBetween(Arrays.asList("2023-01-01", "2025-12-31"));
        sq.setTime(time);
        return sq;
    }

    private static long countSpanIssue(ValidationResult r) {
        return r.getErrors().stream().filter(i -> "§6.4.5".equals(i.getRule())).count();
    }

    @Test
    public void monthAndQuarterSpanRelaxedLikeYear() {
        // 基线：年粒度放宽保持原行为（防回归）
        Assert.assertEquals(0, countSpanIssue(validator.validate(query("d_year"), OM.valueToTree(query("d_year")))));
        // 本次修复：月/季粒度与年同口径放宽，三年跨度不再被 §6.4.5 拒绝
        Assert.assertEquals(0, countSpanIssue(validator.validate(query("d_month"), OM.valueToTree(query("d_month")))));
        Assert.assertEquals(0, countSpanIssue(validator.validate(query("d_quarter"), OM.valueToTree(query("d_quarter")))));
    }

    @Test
    public void noGranularityDimStillCapped() {
        // 对照：无粒度维度的查询仍按 366 天封顶，放宽不得泛化
        ValidationResult r = validator.validate(query("d_date"), OM.valueToTree(query("d_date")));
        Assert.assertEquals(1, countSpanIssue(r));
    }
}
