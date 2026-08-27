package com.bidr.insight.smartquery.validate.conflict;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import com.bidr.kernel.utils.FuncUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: UnitCheckRuleBase
 * Description: 单位类规则公共底座：度量列扫描 + 注释单位提取 + 裁决写回（落 unitVerified 经验标记）；
 * 子类只定义「何种情形算疑点」的具体条件——一规则一文件，具体条件不进公共类
 *
 * @author Sharp
 * @since 2026/8/25
 */
public abstract class UnitCheckRuleBase implements ConfigCheckRule {

    @Override
    public List<ConfigCheckFinding> detect(ConfigCheckContext ctx) {
        List<ConfigCheckFinding> out = new ArrayList<>();
        for (EntityDef e : ctx.entities()) {
            if (e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                // 已裁决过的列跳过（裁决即经验：同表复用不重复提）
                if (!"metric".equals(f.getRole()) || Boolean.TRUE.equals(f.getUnitVerified())) {
                    continue;
                }
                String comment = ctx.commentsOf(e.getTable()).get(f.getName());
                String commentUnit = CommentValueParser.extractUnit(comment);
                if (commentUnit == null) {
                    continue;   // 注释无显式单位：无从比对（宁可漏报不误报）
                }
                String cfg = FuncUtil.isEmpty(f.getUnit()) ? "" : f.getUnit().trim();
                if (cfg.equals(commentUnit) || !suspect(cfg, commentUnit)) {
                    continue;
                }
                ConfigCheckFinding finding = new ConfigCheckFinding();
                finding.setType(type());
                finding.setEntity(e.getName());
                finding.setTable(e.getTable());
                finding.setField(f.getName());
                finding.setCurrent(cfg.isEmpty() ? "未填写" : cfg);
                finding.setSuggestion(commentUnit);
                finding.setEvidence("列注释：" + comment);
                out.add(finding);
            }
        }
        return out;
    }

    /** 具体疑点条件（子类专属判定：已配单位与注释单位的关系） */
    protected abstract boolean suspect(String cfgUnit, String commentUnit);

    @Override
    public boolean resolve(List<EntityDef> entities, Map<String, ValueDomainDef> domains,
                           ConfigCheckResolution r) {
        if (FuncUtil.isEmpty(entities) || FuncUtil.isEmpty(r.getEntity()) || FuncUtil.isEmpty(r.getField())) {
            return false;
        }
        for (EntityDef e : entities) {
            if (!r.getEntity().equals(e.getName()) || e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (!r.getField().equals(f.getName())) {
                    continue;
                }
                // adopt：结论写进配置（打 edited 防骨架重建覆盖）；keep：不改配置，两者都落裁决经验
                if (ConfigCheckResolution.ACTION_ADOPT.equals(r.getAction()) && FuncUtil.isNotEmpty(r.getUnit())) {
                    f.setUnit(r.getUnit().trim());
                    f.setEdited(true);
                }
                f.setUnitVerified(true);
                return true;
            }
        }
        return false;
    }
}
