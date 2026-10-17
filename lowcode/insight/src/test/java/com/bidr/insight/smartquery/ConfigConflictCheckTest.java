package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import com.bidr.insight.smartquery.validate.ConfigConflictDetector;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckContext;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckFinding;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckResolution;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ConfigConflictCheckTest
 * Description: 配置自查回归（确定性规则，无 LLM 无库连接）：注释单位提取、单位矛盾/缺单位探测、
 * 码值域缺码探测（采样覆写）、逐条裁决写回（adopt 写配置 / keep 记经验，两者都落裁决标记）
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class ConfigConflictCheckTest {

    // ---------------- 注释单位确定性提取 ----------------

    @Test
    public void extractUnitDeclaredAndParen() {
        Assert.assertEquals("万元", CommentValueParser.extractUnit("单位：万元"));
        Assert.assertEquals("元", CommentValueParser.extractUnit("单位:元"));
        Assert.assertEquals("万元", CommentValueParser.extractUnit("以万元为单位"));
        Assert.assertEquals("万元", CommentValueParser.extractUnit("合同金额（万元）"));
        Assert.assertEquals("元", CommentValueParser.extractUnit("金额(元)"));
    }

    /** 无把握写法不提（宁可漏报不误报）：裸词干、非单位括注、空值 */
    @Test
    public void extractUnitRejectAmbiguous() {
        Assert.assertNull(CommentValueParser.extractUnit("合同金额"));
        Assert.assertNull(CommentValueParser.extractUnit("备注(元数据)"));
        Assert.assertNull(CommentValueParser.extractUnit(""));
        Assert.assertNull(CommentValueParser.extractUnit(null));
    }

    // ---------------- 单位类规则探测（注释注入，免读库） ----------------

    private EntityDef entity(String name, String table, EntityDef.EntityFieldDef... fields) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable(table);
        e.setFields(new ArrayList<>(Arrays.asList(fields)));
        return e;
    }

    private EntityDef.EntityFieldDef field(String name, String role, String unit, Boolean verified) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        f.setRole(role);
        f.setUnit(unit);
        f.setUnitVerified(verified);
        return f;
    }

    private ConfigCheckContext ctxWithComments(List<EntityDef> entities, Map<String, ValueDomainDef> domains,
                                               String table, String col, String comment) {
        ConfigCheckContext ctx = new ConfigCheckContext(null, entities, domains);
        Map<String, Map<String, String>> preset = new HashMap<>();
        Map<String, String> cols = new HashMap<>();
        cols.put(col, comment);
        preset.put(table, cols);
        ctx.useComments(preset);
        return ctx;
    }

    /** 已配单位 ≠ 注释单位 → 单位矛盾；单位一致 / 无注释单位 / 已裁决 / 非度量列 均不提 */
    @Test
    public void unitRulesDetect() {
        List<EntityDef> ents = Collections.singletonList(entity("合同", "db1.ht_contract",
                field("amount", "metric", "元", null),
                field("ok_amount", "metric", "万元", null),
                field("plain", "metric", "元", null),
                field("verified", "metric", "元", true),
                field("region", "dimension", null, null)));
        ConfigCheckContext ctx = new ConfigCheckContext(null, ents, new LinkedHashMap<>());
        Map<String, Map<String, String>> preset = new HashMap<>();
        Map<String, String> cols = new HashMap<>();
        cols.put("amount", "合同金额(万元)");
        cols.put("ok_amount", "合同金额(万元)");
        cols.put("plain", "普通备注无单位");
        cols.put("verified", "合同金额(万元)");
        cols.put("region", "地区(万元)");
        preset.put("db1.ht_contract", cols);
        ctx.useComments(preset);

        List<ConfigCheckFinding> findings = new com.bidr.insight.smartquery.validate.conflict
                .UnitConflictCheckRule().detect(ctx);
        Assert.assertEquals(1, findings.size());
        ConfigCheckFinding f = findings.get(0);
        Assert.assertEquals("unit_conflict", f.getType());
        Assert.assertEquals("amount", f.getField());
        Assert.assertEquals("元", f.getCurrent());
        Assert.assertEquals("万元", f.getSuggestion());
    }

    /** 度量列未填单位而注释有单位 → 缺单位 */
    @Test
    public void unitAbsentDetect() {
        List<EntityDef> ents = Collections.singletonList(entity("合同", "db1.ht_contract",
                field("amount", "metric", null, null)));
        ConfigCheckContext ctx = ctxWithComments(ents, new LinkedHashMap<>(),
                "db1.ht_contract", "amount", "合同金额，单位：万元");
        List<ConfigCheckFinding> findings = new com.bidr.insight.smartquery.validate.conflict
                .UnitAbsentCheckRule().detect(ctx);
        Assert.assertEquals(1, findings.size());
        Assert.assertEquals("unit_absent", findings.get(0).getType());
        Assert.assertEquals("未填写", findings.get(0).getCurrent());
        Assert.assertEquals("万元", findings.get(0).getSuggestion());
    }

    /** 裁决写回：adopt 写单位并打 edited；keep 不改配置；两者都落 unitVerified 经验 */
    @Test
    public void unitResolutionAdoptAndKeep() {
        List<EntityDef> ents = Collections.singletonList(entity("合同", "db1.ht_contract",
                field("amount", "metric", "元", null),
                field("cnt", "metric", null, null)));
        Map<String, ValueDomainDef> domains = new LinkedHashMap<>();

        ConfigCheckResolution adopt = new ConfigCheckResolution();
        adopt.setType("unit_conflict");
        adopt.setEntity("合同");
        adopt.setField("amount");
        adopt.setAction(ConfigCheckResolution.ACTION_ADOPT);
        adopt.setUnit("万元");
        Assert.assertTrue(ConfigConflictDetector.applyResolution(ents, domains, adopt));
        EntityDef.EntityFieldDef amount = ents.get(0).getFields().get(0);
        Assert.assertEquals("万元", amount.getUnit());
        Assert.assertEquals(Boolean.TRUE, amount.getEdited());
        Assert.assertEquals(Boolean.TRUE, amount.getUnitVerified());

        ConfigCheckResolution keep = new ConfigCheckResolution();
        keep.setType("unit_absent");
        keep.setEntity("合同");
        keep.setField("cnt");
        keep.setAction(ConfigCheckResolution.ACTION_KEEP);
        Assert.assertTrue(ConfigConflictDetector.applyResolution(ents, domains, keep));
        EntityDef.EntityFieldDef cnt = ents.get(0).getFields().get(1);
        Assert.assertNull(cnt.getUnit());
        Assert.assertEquals(Boolean.TRUE, cnt.getUnitVerified());
    }

    // ---------------- 码值域缺码规则（采样覆写，免读库） ----------------

    /** 采样覆写探针：预置 DISTINCT 结果，域规则探测逻辑与真实链路一致 */
    private static class SampleCtx extends ConfigCheckContext {
        private final Map<String, List<String>> samples = new HashMap<>();

        SampleCtx(List<EntityDef> entities, Map<String, ValueDomainDef> domains) {
            super(null, entities, domains);
        }

        void sample(String table, String col, List<String> values) {
            samples.put(table + "." + col, values);
        }

        @Override
        public List<String> sampleDistinct(String fullName, String col) {
            return samples.getOrDefault(fullName + "." + col, Collections.emptyList());
        }
    }

    private ValueDomainDef domain(String entity, String field, String storedAs, Boolean certified,
                                  String... registered) {
        ValueDomainDef d = new ValueDomainDef();
        d.setEntity(entity);
        d.setField(field);
        d.setStoredAs(storedAs);
        d.setCertified(certified);
        List<ValueDomainDef.DomainValue> values = new ArrayList<>();
        for (String code : registered) {
            values.add(CommentValueParser.domainValue(code, code));
        }
        d.setValues(values);
        return d;
    }

    /** 采样真实码值比登记多 → 缺码疑点（忽略清单内的码不重提；认证域/非 code 域不探） */
    @Test
    public void domainMissingDetect() {
        List<EntityDef> ents = Collections.singletonList(entity("合同", "db1.ht_contract",
                field("status_code", "dimension", null, null)));
        Map<String, ValueDomainDef> domains = new LinkedHashMap<>();
        ValueDomainDef d = domain("合同", "status_code", "code", null, "0", "1");
        d.setIgnoredCodes(new ArrayList<>(Collections.singletonList("9")));
        domains.put("ht_contract.status_code", d);
        // 认证域与非 code 域对照：即便缺码也不提
        domains.put("certified_one", domain("合同", "status_code", "code", true, "0"));
        domains.put("name_one", domain("合同", "status_code", "name", null, "0"));

        SampleCtx ctx = new SampleCtx(ents, domains);
        ctx.sample("db1.ht_contract", "status_code", Arrays.asList("0", "1", "2", "9"));

        List<ConfigCheckFinding> findings = new com.bidr.insight.smartquery.validate.conflict
                .DomainMissingCheckRule().detect(ctx);
        Assert.assertEquals(1, findings.size());
        ConfigCheckFinding f = findings.get(0);
        Assert.assertEquals("domain_missing", f.getType());
        Assert.assertEquals("ht_contract.status_code", f.getDomainKey());
        // 9 在忽略清单是经验不重提，只缺 2
        Assert.assertEquals(Collections.singletonList("2"), f.getMissingCodes());
    }

    /** 裁决写回：adopt 补登记（已有码去重）；keep 记入忽略清单（脏数据经验防重提） */
    @Test
    public void domainResolutionAdoptAndKeep() {
        List<EntityDef> ents = Collections.singletonList(entity("合同", "db1.ht_contract"));
        Map<String, ValueDomainDef> domains = new LinkedHashMap<>();
        domains.put("k1", domain("合同", "status_code", "code", null, "0"));
        domains.put("k2", domain("合同", "type_code", "code", null, "a"));

        ConfigCheckResolution adopt = new ConfigCheckResolution();
        adopt.setType("domain_missing");
        adopt.setDomainKey("k1");
        adopt.setAction(ConfigCheckResolution.ACTION_ADOPT);
        adopt.setCodes(Arrays.asList("2", "0"));   // 0 已登记不重复加
        Assert.assertTrue(ConfigConflictDetector.applyResolution(ents, domains, adopt));
        Assert.assertEquals(2, domains.get("k1").getValues().size());

        ConfigCheckResolution keep = new ConfigCheckResolution();
        keep.setType("domain_missing");
        keep.setDomainKey("k2");
        keep.setAction(ConfigCheckResolution.ACTION_KEEP);
        keep.setCodes(Collections.singletonList("x"));
        Assert.assertTrue(ConfigConflictDetector.applyResolution(ents, domains, keep));
        Assert.assertEquals(Collections.singletonList("x"), domains.get("k2").getIgnoredCodes());
    }

    /** 编排兜底：未知疑点类型/缺定位返 false 丢弃；无实体清单探测返空 */
    @Test
    public void detectorGuard() {
        ConfigCheckResolution unknown = new ConfigCheckResolution();
        unknown.setType("not_a_rule");
        unknown.setEntity("合同");
        Assert.assertFalse(ConfigConflictDetector.applyResolution(
                new ArrayList<>(), new LinkedHashMap<>(), unknown));
        Assert.assertFalse(ConfigConflictDetector.applyResolution(
                new ArrayList<>(), new LinkedHashMap<>(), null));
        Assert.assertTrue(ConfigConflictDetector.detect(null, Collections.emptyList(),
                new LinkedHashMap<>()).isEmpty());
    }
}
