package com.bidr.insight.smartquery.validate.conflict;

/**
 * Title: UnitAbsentCheckRule
 * Description: 规则·缺单位：度量列未填单位而列注释有显式单位声明（单位是问数口径刚需，
 * 注释已写明却漏配，逐条裁决补齐或确认无单位）
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class UnitAbsentCheckRule extends UnitCheckRuleBase {

    public static final String TYPE = "unit_absent";

    @Override
    public String type() {
        return TYPE;
    }

    /** 未配单位即疑点（注释单位存在性底座已保证） */
    @Override
    protected boolean suspect(String cfgUnit, String commentUnit) {
        return cfgUnit.isEmpty();
    }
}
