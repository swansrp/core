package com.bidr.forge.service.statistic;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

/**
 * Title: DriverStatisticPivotRatioTest
 * Description: {@link DriverStatisticSupportService#pivot} 比率(ratio)度量的聚合形态单测。
 * <p>
 * 线上故障形态：动态门户(矩阵/数据集)透视报表配了 ratio 度量后，Doris 直接报
 * 1105 errCode=2 detailMessage=select list expression not produced by aggregation output
 * (missing from GROUP BY clause?)，整张报表 500。原因是 ratio 分支把 分子/分母 两条
 * 裸 CASE WHEN 表达式直接相除，两腿都没进聚合函数，GROUP BY 行维度之外就是非法 SELECT。
 * 期望口径：与 kernel 的 AdminStatisticPivotInf.buildPivotAgg 一致，恒为
 * sum(分子腿) / nullif(sum(分母腿), 0)，两腿各自独立套同一透视条件。
 * <p>
 * 产物 SQL 的捕获工装见 {@link PivotSqlTestSupport}（排序形态单测共用）。
 *
 * @author Sharp
 * @since 2026-10-09
 */
public class DriverStatisticPivotRatioTest {

    private static final MetricCondition PIVOT_L3 = new MetricCondition("L3", "三级",
            PivotSqlTestSupport.leaf("gradeLevel", 1, "3"));

    @Test
    public void testRatioLegsAreBothAggregated() {
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3,
                PivotSqlTestSupport.ratioMeasure("weightedScore", "propStdScore", "phaseProp")));

        // 正解形态：两腿各自 sum()，除零回落 nullif
        Pattern expected = Pattern.compile(
                "sum\\(case when t\\.grade_level = :param_\\d+ then t\\.prop_std_score else null end\\)"
                        + " / nullif\\(sum\\(case when t\\.grade_level = :param_\\d+ then t\\.phase_prop else null end\\), 0\\)"
                        + " AS `L3__weightedScore`");
        Assert.assertTrue(expected.matcher(sql).find(), "ratio 未拼成 sum(分子)/nullif(sum(分母),0): " + sql);

        // 故障形态不得复现：裸列直接相除（GROUP BY 之外的列出现在 SELECT 里）
        Assert.assertFalse(sql.contains("t.prop_std_score /"), "分子腿未被聚合函数包裹: " + sql);
        Assert.assertFalse(sql.contains("nullif(t.phase_prop"), "分母腿未被聚合函数包裹: " + sql);
        Assert.assertTrue(sql.contains("GROUP BY t.assess_name"), "行维度应参与 GROUP BY: " + sql);
    }

    @Test
    public void testRatioWithoutPivotConditionStillAggregated() {
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(
                new MetricCondition("TOTAL", "合计", null),
                PivotSqlTestSupport.ratioMeasure("weightedScore", "propStdScore", "phaseProp")));

        Assert.assertTrue(sql.contains(
                        "sum(t.prop_std_score) / nullif(sum(t.phase_prop), 0) AS `TOTAL__weightedScore`"),
                "无条件时 ratio 应退化为纯度量聚合相除: " + sql);
    }

    @Test
    public void testRatioAndSumMeasureCoexist() {
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3,
                PivotSqlTestSupport.sumMeasure("score"),
                PivotSqlTestSupport.ratioMeasure("weightedScore", "propStdScore", "phaseProp")));

        Assert.assertTrue(sql.contains("sum(case when t.grade_level = :param_"), "普通度量聚合不受影响: " + sql);
        Assert.assertTrue(sql.contains(" AS `L3__score`"), "普通度量列别名不变: " + sql);
    }

    @Test
    public void testRatioWithoutNumeratorRejected() {
        PivotMeasure measure = PivotSqlTestSupport.ratioMeasure("weightedScore", null, "phaseProp");
        try {
            PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(PIVOT_L3, measure));
            Assert.fail("缺少分子的比率度量应在拼 SQL 前被拒");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("weightedScore"), expected.getMessage());
        }
    }
}
