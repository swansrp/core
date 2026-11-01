package com.bidr.forge.service.statistic;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Title: DriverStatisticPivotMeasureConditionTest
 * Description: 透视「度量自身条件」的产物 SQL 单测：度量条件与透视列条件必须按 AND 叠加进同一个 CASE WHEN，
 * 输出键（同字段多条件时的列标识）必须落进列别名。
 * <p>
 * 这两件事都只能靠拼出来的 SQL 证伪：少拼一侧条件是静默错数，别名没带键则是同名字段互相覆盖——
 * 接口照样返回数据，只是数字不对/列少一截。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-28
 */
public class DriverStatisticPivotMeasureConditionTest {

    private static final int EQUAL = 1;

    private static final MetricCondition PIVOT_L3 = new MetricCondition("L3", "三级",
            PivotSqlTestSupport.leaf("gradeLevel", EQUAL, "3"));

    private static PivotMeasure measure(String field, String key, String agg, AdvancedQuery condition) {
        PivotMeasure measure = new PivotMeasure();
        measure.setField(field);
        measure.setKey(key);
        measure.setAgg(agg);
        measure.setCondition(condition);
        return measure;
    }

    private static AdvancedQuery orGroup(AdvancedQuery... children) {
        AdvancedQuery query = new AdvancedQuery();
        query.setAndOr(AdvancedQuery.OR);
        query.getConditionList().addAll(java.util.Arrays.asList(children));
        return query;
    }

    /** 两侧都有条件 ⇒ 同一 CASE WHEN 内 AND 叠加，值全部走命名参数 */
    @Test
    public void testMeasureConditionAndedWithPivotCondition() {
        PivotMeasure measure = measure("score", null, "sum",
                PivotSqlTestSupport.leaf("manageMode", EQUAL, "1"));

        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3, measure));

        Assert.assertTrue(sql.contains("sum(case when (t.grade_level = :param_0) and (manageMode = :param_1)"
                + " then t.score else null end) AS `L3__score`"), "度量条件未与列条件叠加: " + sql);
    }

    /**
     * 没有透视列条件时（列条件为 null）只包度量条件，不额外塞 1=1；
     * 判别腿：同一请求去掉度量条件必须回到裸列，否则本测只证明"多了一层 case when"
     */
    @Test
    public void testMeasureConditionAlone() {
        PivotMeasure withCondition = measure("score", null, "sum",
                PivotSqlTestSupport.leaf("manageMode", EQUAL, "1"));
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(
                new MetricCondition("total", "", null), withCondition));

        Assert.assertTrue(sql.contains("case when manageMode = :param_0 then t.score else null end"),
                "无列条件时应只包度量条件: " + sql);
        Assert.assertFalse(sql.contains("1=1"), "叠加时不应留下占位恒真条件: " + sql);

        String withoutCondition = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(
                new MetricCondition("total", "", null), measure("score", null, "sum", null)));
        Assert.assertTrue(withoutCondition.contains("sum(t.score)"), "无条件应退化为裸列聚合: " + withoutCondition);
        Assert.assertFalse(withoutCondition.contains("case when"), "无条件不应出现 CASE WHEN: " + withoutCondition);
    }

    /**
     * 老形态兼容：前端未配透视列时下发占位空条件 {}，无语义条件却仍产出 case when 1=1。
     * 这是既有行为，叠加改造不得顺手改掉（产物逐字节一致才好比对升级前后的数字）
     */
    @Test
    public void testEmptyObjectPivotConditionKeepsLegacyShape() {
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(
                new MetricCondition("total", "", new AdvancedQuery()), measure("score", null, "sum", null)));

        Assert.assertTrue(sql.contains("sum(case when 1=1 then t.score else null end)"), sql);
    }

    /** 「或」组整体带括号参与叠加，不能被 AND 优先级拍平 */
    @Test
    public void testMeasureOrGroupKeepsParentheses() {
        PivotMeasure measure = measure("score", null, "sum", orGroup(
                PivotSqlTestSupport.leaf("manageMode", EQUAL, "1"),
                PivotSqlTestSupport.leaf("manageMode", EQUAL, "2")));

        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3, measure));

        Assert.assertTrue(sql.contains("(t.grade_level = :param_0) and ((manageMode = :param_1) OR (manageMode = :param_2))"),
                "或组未被括号保护: " + sql);
    }

    /** 同字段多条件各出一列：输出键进别名，取数仍用原字段 */
    @Test
    public void testMeasureKeyUsedInAlias() {
        PivotMeasure first = measure("score", "score", "sum", null);
        PivotMeasure second = measure("score", "score_2", "avg",
                PivotSqlTestSupport.leaf("manageMode", EQUAL, "1"));

        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3, first, second));

        Assert.assertTrue(sql.contains("AS `L3__score`"), "首列别名应沿用字段名: " + sql);
        Assert.assertTrue(sql.contains("AS `L3__score_2`"), "重名列别名应带输出键: " + sql);
        Assert.assertTrue(sql.contains("avg(case when (t.grade_level = :param_1) and (manageMode = :param_2)"
                + " then t.score else null end)"), "重名列取数应仍走原字段: " + sql);
    }

    /** ratio 两条腿都要包同一条件（只包一边会把另一腿算进错误分母） */
    @Test
    public void testRatioLegsBothCarryMeasureCondition() {
        PivotMeasure ratio = PivotSqlTestSupport.ratioMeasure("achieveRate", "actualAmt", "targetAmt");
        ratio.setCondition(PivotSqlTestSupport.leaf("manageMode", EQUAL, "1"));

        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3, ratio));

        Assert.assertTrue(sql.contains("sum(case when (t.grade_level = :param_0) and (manageMode = :param_1)"
                + " then t.actual_amt else null end)"), "分子腿缺条件: " + sql);
        Assert.assertTrue(sql.contains("sum(case when (t.grade_level = :param_2) and (manageMode = :param_3)"
                + " then t.target_amt else null end)"), "分母腿缺条件: " + sql);
    }

    /** 输出键会拼进反引号别名：构造带引号的键必须整体拒收 */
    @Test
    public void testUnsafeMeasureKeyRejected() {
        try {
            PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3,
                    measure("score", "score`", "sum", null)));
            Assert.fail("非法输出键应在拼 SQL 前被拒");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("度量列标识非法"), expected.getMessage());
        }
    }
}