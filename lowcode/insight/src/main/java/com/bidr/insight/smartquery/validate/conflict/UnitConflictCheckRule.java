package com.bidr.insight.smartquery.validate.conflict;

import com.bidr.kernel.utils.FuncUtil;

/**
 * Title: UnitConflictCheckRule
 * Description: 规则·单位矛盾：度量列已配单位 ≠ 列注释显式声明的单位（最典型：注释写万元、
 * 实际配置元——人工初配最易犯的错）。证据链实证过的矛盾类，必须人逐条裁决不自动改
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class UnitConflictCheckRule extends UnitCheckRuleBase {

    public static final String TYPE = "unit_conflict";

    @Override
    public String type() {
        return TYPE;
    }

    /** 已配且与注释单位不同即疑点（相等情况底座已排除） */
    @Override
    protected boolean suspect(String cfgUnit, String commentUnit) {
        return FuncUtil.isNotEmpty(cfgUnit);
    }
}
