package com.bidr.forge.service.statistic;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * Title: DriverStatisticPivotHavingTest
 * Description: 透视「统计列过滤」的产物 SQL 单测：表头漏斗打在聚合列上的条件必须落成 HAVING，
 * 且只能引用本次 SELECT 的输出别名、值走命名参数绑定。
 * <p>
 * 这两条都不能靠接口返回值证伪：别名写错照样返回 0 行，值内联进 SQL 在单库里也看不出问题，
 * 但后者等于把「用户可输入的筛选值」拼进语句。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-23
 */
public class DriverStatisticPivotHavingTest {

    private static final MetricCondition PIVOT_L3 = new MetricCondition("L3", "三级",
            PivotSqlTestSupport.leaf("gradeLevel", 1, "3"));

    private static final Integer GREATER = 3;

    private static final Integer BETWEEN = 13;

    private static AdvancedPivotReq reqWithHaving(AdvancedQuery having) {
        AdvancedPivotReq req = PivotSqlTestSupport.req(PIVOT_L3, PivotSqlTestSupport.sumMeasure("score"));
        req.setHavingCondition(having);
        return req;
    }

    /**
     * 正路径：按单元格别名过滤，子句位置在 GROUP BY 之后，值以命名参数绑定
     */
    @Test
    public void testHavingOnCellAliasBoundToParameter() {
        String sql = PivotSqlTestSupport.pivot(reqWithHaving(
                PivotSqlTestSupport.leaf("L3__score", GREATER, 100)));

        Assert.assertTrue(sql.contains("HAVING `L3__score` > :param_"),
                "统计列过滤未拼成按别名的 HAVING: " + sql);
        Assert.assertTrue(sql.indexOf("GROUP BY") < sql.indexOf("HAVING"),
                "HAVING 必须在 GROUP BY 之后: " + sql);
        Assert.assertFalse(sql.contains("`L3__score` > 100"),
                "筛选值必须绑定参数，不能内联字面量: " + sql);
    }

    /**
     * 数字漏斗实际下发的是 BETWEEN 双值；与表头排序共存时子句顺序须为 GROUP BY → HAVING → ORDER BY
     */
    @Test
    public void testHavingBetweenSitsBeforeOrderBy() {
        AdvancedPivotReq req = reqWithHaving(PivotSqlTestSupport.leaf("L3__score", BETWEEN, 10, 50));
        req.setSortList(Collections.singletonList(PivotSqlTestSupport.sort("assessName", 1)));

        String sql = PivotSqlTestSupport.pivot(req);

        Assert.assertTrue(sql.contains("HAVING `L3__score` BETWEEN :param_"),
                "数字区间未拼成 BETWEEN: " + sql);
        Assert.assertTrue(sql.indexOf("HAVING") < sql.indexOf("ORDER BY"),
                "HAVING 必须排在 ORDER BY 之前: " + sql);
    }

    @Test
    public void testNoHavingWhenConditionAbsent() {
        String sql = PivotSqlTestSupport.pivot(reqWithHaving(null));

        Assert.assertFalse(sql.contains("HAVING"), "未下发统计列过滤不应有 HAVING: " + sql);
    }

    /**
     * 无值条件（用户清空输入框）整条剔除后不能留下半截子句
     */
    @Test
    public void testEmptyValueEmitsNoHavingClause() {
        String sql = PivotSqlTestSupport.pivot(reqWithHaving(
                PivotSqlTestSupport.leaf("L3__score", GREATER)));

        Assert.assertFalse(sql.contains("HAVING"), "无值条件不应产出 HAVING: " + sql);
    }

    /**
     * 白名单闸门：score 是别名映射里的真实列，但不是本次输出列，拿它做聚合位过滤可以读出未输出的列。
     * 判别腿——同一张权限地图（未收窄，ORDER BY 那条腿此时不拦）下换成输出别名必须放行，
     * 否则本测只证明了"没崩"，证明不了闸门真的在拦该拦的
     */
    @Test
    public void testNonOutputAliasRejectedEvenWithoutNarrowedPermission() {
        try {
            PivotSqlTestSupport.pivot(reqWithHaving(PivotSqlTestSupport.leaf("score", GREATER, 100)));
            Assert.fail("非输出别名应在拼 SQL 前被拒");
        } catch (ServiceException expected) {
            // 拒绝类失败走 ServiceException(非 NoticeException)，前端才读得到 details
            Assert.assertTrue(expected.getMessage().contains("score"), expected.getMessage());
        }

        String sql = PivotSqlTestSupport.pivot(reqWithHaving(
                PivotSqlTestSupport.leaf("L3__score", GREATER, 100)));
        Assert.assertTrue(sql.contains("HAVING"), "输出别名过滤不应被误拒: " + sql);
    }
}
