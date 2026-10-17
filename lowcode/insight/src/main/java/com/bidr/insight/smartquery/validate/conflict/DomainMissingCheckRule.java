package com.bidr.insight.smartquery.validate.conflict;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import com.bidr.kernel.utils.FuncUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: DomainMissingCheckRule
 * Description: 规则·码值域缺码：storedAs=code 的未认证域，采样真实码值比登记多
 * （如 statuscode 注释解析出 8 个码、数据实有 12 个——骨架阶段不采样即发现不了，
 * 模型到收口才撞上的问题改由确定性程序前置探测）。已登记与人工裁决忽略之外的码即疑点；
 * 裁决 adopt 补登记 / keep 记入忽略清单，均落经验防重提
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class DomainMissingCheckRule implements ConfigCheckRule {

    public static final String TYPE = "domain_missing";

    /** 疑点建议文案里缺码预览个数（完整清单在 missingCodes，前端可展开） */
    private static final int SUGGEST_PREVIEW = 8;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<ConfigCheckFinding> detect(ConfigCheckContext ctx) {
        List<ConfigCheckFinding> out = new ArrayList<>();
        for (Map.Entry<String, ValueDomainDef> entry : ctx.domains().entrySet()) {
            ValueDomainDef d = entry.getValue();
            // 认证域是人拍板的权威结论不质疑；非 code 域无码值语义
            if (!"code".equals(d.getStoredAs()) || Boolean.TRUE.equals(d.getCertified())
                    || FuncUtil.isEmpty(d.getEntity()) || FuncUtil.isEmpty(d.getField())) {
                continue;
            }
            String table = ctx.tableOfEntity(d.getEntity());
            if (FuncUtil.isEmpty(table)) {
                continue;
            }
            List<String> actual = ctx.sampleDistinct(table, d.getField());
            if (actual.isEmpty()) {
                continue;   // 非枚举（超上限）或采样失败：不提
            }
            Set<String> known = new HashSet<>();
            if (d.getValues() != null) {
                d.getValues().forEach(v -> known.add(String.valueOf(v.getCode())));
            }
            if (d.getIgnoredCodes() != null) {
                known.addAll(d.getIgnoredCodes());   // 裁决忽略过的码是经验，不重提
            }
            List<String> missing = new ArrayList<>();
            for (String code : actual) {
                if (!known.contains(code)) {
                    missing.add(code);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            ConfigCheckFinding finding = new ConfigCheckFinding();
            finding.setType(TYPE);
            finding.setEntity(d.getEntity());
            finding.setTable(table);
            finding.setField(d.getField());
            finding.setDomainKey(entry.getKey());
            finding.setCurrent("已登记 " + (d.getValues() == null ? 0 : d.getValues().size()) + " 个码值");
            finding.setSuggestion("补登 " + preview(missing));
            finding.setEvidence("采样真实码值 " + actual.size() + " 个，" + missing.size() + " 个未登记");
            finding.setMissingCodes(missing);
            out.add(finding);
        }
        return out;
    }

    @Override
    public boolean resolve(List<EntityDef> entities, Map<String, ValueDomainDef> domains,
                           ConfigCheckResolution r) {
        if (domains == null || FuncUtil.isEmpty(r.getDomainKey()) || FuncUtil.isEmpty(r.getCodes())) {
            return false;
        }
        ValueDomainDef d = domains.get(r.getDomainKey());
        if (d == null) {
            return false;
        }
        if (ConfigCheckResolution.ACTION_ADOPT.equals(r.getAction())) {
            // 补登记：label 暂同码（可再编辑）；已存在的码跳过防重复
            Set<String> existed = new HashSet<>();
            if (d.getValues() == null) {
                d.setValues(new ArrayList<>());
            }
            d.getValues().forEach(v -> existed.add(String.valueOf(v.getCode())));
            for (String code : r.getCodes()) {
                if (FuncUtil.isEmpty(code) || existed.contains(code)) {
                    continue;
                }
                d.getValues().add(CommentValueParser.domainValue(code, code));
                existed.add(code);
            }
            return true;
        }
        // 维持原值：码记入忽略清单（脏数据/废弃码裁决经验，后续自查不再提）
        Set<String> ignored = new HashSet<>();
        if (d.getIgnoredCodes() == null) {
            d.setIgnoredCodes(new ArrayList<>());
        }
        ignored.addAll(d.getIgnoredCodes());
        for (String code : r.getCodes()) {
            if (FuncUtil.isNotEmpty(code) && ignored.add(code)) {
                d.getIgnoredCodes().add(code);
            }
        }
        return true;
    }

    /** 缺码预览文案：前 N 个 + 省略计数 */
    private static String preview(List<String> codes) {
        if (codes.size() <= SUGGEST_PREVIEW) {
            return String.join("、", codes);
        }
        return String.join("、", codes.subList(0, SUGGEST_PREVIEW)) + "…等 " + codes.size() + " 个";
    }
}
