package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: AdminStatisticPivotMeasureConditionTest
 * Description: 度量自身条件与透视列条件的叠加规则、度量输出键单测。
 * <p>
 * 叠加是 CASE WHEN 的核心语义：只认列条件会把度量自己的口径算成全量（数字变大却不报错），
 * 用 OR 合并则会把不该计入的行放进来，两种错都只能靠产物 SQL 证伪。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-28
 */
public class AdminStatisticPivotMeasureConditionTest {

    private static final int EQUAL = 1;

    private static final int GREATER = 3;

    private static final AdminStatisticParseInf PARSE = new AdminStatisticParseInf() {
    };

    private static AdvancedQuery leaf(String property, int relation, Object value) {
        AdvancedQuery query = new AdvancedQuery();
        query.setProperty(property);
        query.setRelation(relation);
        query.setValue(java.util.Collections.singletonList(value));
        return query;
    }

    private static AdvancedQuery group(String andOr, AdvancedQuery... children) {
        AdvancedQuery query = new AdvancedQuery();
        query.setAndOr(andOr);
        query.getConditionList().addAll(java.util.Arrays.asList(children));
        return query;
    }

    private static PivotMeasure measure(String field, String key, AdvancedQuery condition) {
        PivotMeasure measure = new PivotMeasure();
        measure.setField(field);
        measure.setKey(key);
        measure.setCondition(condition);
        return measure;
    }

    /** 老配置无 key ⇒ 输出键回落字段名，别名逐字节不变 */
    @Test
    public void testMeasureKeyFallsBackToField() {
        Assert.assertEquals("score", AdminStatisticPivotInf.measureKey(measure("score", null, null)));
        Assert.assertEquals("score", AdminStatisticPivotInf.measureKey(measure("score", "", null)));
        Assert.assertEquals("score_2", AdminStatisticPivotInf.measureKey(measure("score", "score_2", null)));
    }

    /** 空条件树（{} / 空 conditionList）不算生效条件，避免无谓地多包一层 case when */
    @Test
    public void testEmptyConditionTreeIsNotEffective() {
        Assert.assertFalse(AdminStatisticPivotInf.hasCondition(null));
        Assert.assertFalse(AdminStatisticPivotInf.hasCondition(new AdvancedQuery()));
        Assert.assertFalse(AdminStatisticPivotInf.hasCondition(group(AdvancedQuery.AND)));
        Assert.assertTrue(AdminStatisticPivotInf.hasCondition(group(AdvancedQuery.AND,
                new AdvancedQuery(), leaf("score", GREATER, 100))));
    }

    /** 只有度量条件时原样用它（不塞空列条件） */
    @Test
    public void testMeasureConditionAlonePassesThrough() {
        AdvancedQuery measureCond = leaf("manageMode", EQUAL, "1");
        AdvancedQuery merged = AdminStatisticPivotInf.mergeCondition(new AdvancedQuery(), measureCond);
        Assert.assertSame(measureCond, merged);
    }

    /** 两侧都有效 ⇒ AND 树，产物 SQL 为 (列条件) and (度量条件)，不是 or 也不是只取其一 */
    @Test
    public void testBothConditionsMergedWithAnd() {
        AdvancedQuery pivotCond = leaf("gradeLevel", EQUAL, "3");
        AdvancedQuery measureCond = leaf("manageMode", EQUAL, "1");
        AdvancedQuery merged = AdminStatisticPivotInf.mergeCondition(pivotCond, measureCond);

        Assert.assertEquals(AdvancedQuery.AND, merged.getAndOr());
        Assert.assertEquals(2, merged.getConditionList().size());

        String sql = PARSE.parseStatisticSelect(merged, "score", false);
        Assert.assertEquals("case when (gradeLevel = '3') and (manageMode = '1') then score else 0 end", sql);
    }

    /**
     * 度量条件自身是「或」组时不能被拍平成 AND：
     * 组节点要带括号整体参与叠加，否则 A or B and 列条件 会被 AND 优先级改义
     */
    @Test
    public void testMeasureOrGroupKeepsParentheses() {
        AdvancedQuery pivotCond = leaf("gradeLevel", EQUAL, "3");
        AdvancedQuery measureCond = group(AdvancedQuery.OR,
                leaf("manageMode", EQUAL, "1"), leaf("manageMode", EQUAL, "2"));
        AdvancedQuery merged = AdminStatisticPivotInf.mergeCondition(pivotCond, measureCond);

        String sql = PARSE.parseStatisticSelect(merged, "score", false);
        Assert.assertEquals("case when (gradeLevel = '3') and ((manageMode = '1') or (manageMode = '2'))"
                + " then score else 0 end", sql);
    }

    /** 输出键会拼进别名引号位置：带引号/反引号/空白的键必须被拒 */
    @Test
    public void testUnsafeMeasureKeyRejected() {
        Assert.assertTrue(AdminStatisticPivotInf.isSafeMeasureKey("score_2"));
        Assert.assertTrue(AdminStatisticPivotInf.isSafeMeasureKey("2024-07"));
        Assert.assertFalse(AdminStatisticPivotInf.isSafeMeasureKey("score`"));
        Assert.assertFalse(AdminStatisticPivotInf.isSafeMeasureKey("score x"));
        Assert.assertFalse(AdminStatisticPivotInf.isSafeMeasureKey("a--b"));
        Assert.assertFalse(AdminStatisticPivotInf.isSafeMeasureKey(null));
    }
}