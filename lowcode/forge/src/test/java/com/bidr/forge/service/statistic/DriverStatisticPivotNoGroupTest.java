package com.bidr.forge.service.statistic;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: DriverStatisticPivotNoGroupTest
 * Description: 无行维度（单行总计表）的产物 SQL 形态单测。
 * <p>
 * 2026-10-26 起行维度可空：整个过滤集聚成一行。这条通道有两个只看返回值证伪不了的缺陷——
 * 一是空行维度若仍拼 "GROUP BY "（子句后面什么都没有）Doris 直接语法报错，二是排序/别名生成可能被
 * 行维度为空带崩。此外"行维度可空"必须保留"行维度与透视列不能同时为空"的下界，本测同配负向腿。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-26
 */
public class DriverStatisticPivotNoGroupTest {

    private static final MetricCondition PIVOT_L3 = new MetricCondition("L3", "三级",
            PivotSqlTestSupport.leaf("gradeLevel", 1, "3"));

    /**
     * 无行维度 + 单个透视列 + sum(score)
     */
    private static AdvancedPivotReq noGroupReq(MetricCondition pivot, SortVO... sorts) {
        return PivotSqlTestSupport.req(Collections.emptyList(),
                Collections.singletonList(pivot),
                new ArrayList<>(Collections.singletonList(PivotSqlTestSupport.sumMeasure("score"))),
                sorts.length == 0 ? null : new ArrayList<>(Arrays.asList(sorts)));
    }

    /**
     * 正路径：行维度为空 ⇒ 不拼 GROUP BY（无 GROUP BY 的聚合天然返回单行），透视列×度量别名照常生成
     */
    @Test
    public void testNoGroupColumnsOmitsGroupBy() {
        String sql = PivotSqlTestSupport.pivot(noGroupReq(PIVOT_L3));

        Assert.assertFalse(sql.contains("GROUP BY"), "无行维度不应拼 GROUP BY: " + sql);
        Assert.assertTrue(sql.contains("AS `L3__score`"), "透视列×度量别名应照常生成: " + sql);
        Assert.assertTrue(sql.contains("FROM v_assess_detail AS t"), sql);
    }

    /**
     * 正对照腿：同一形态下补一个行维度 ⇒ GROUP BY 必须出现（证明上一条不是"整条子句压根没拼"的假绿）
     */
    @Test
    public void testGroupColumnsStillEmitGroupBy() {
        String sql = PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(
                Collections.singletonList(new KeyValueResVO("assessName", "被考核人")),
                Collections.singletonList(PIVOT_L3),
                new ArrayList<>(Collections.singletonList(PivotSqlTestSupport.sumMeasure("score"))),
                null));

        Assert.assertTrue(sql.contains("GROUP BY t.assess_name"), "有行维度必须拼 GROUP BY: " + sql);
    }

    /**
     * 无行维度下排序照常：ORDER BY 引用的是本次 SELECT 输出别名，与 GROUP BY 是否存在无关
     */
    @Test
    public void testNoGroupColumnsStillSortsByCellAlias() {
        String sql = PivotSqlTestSupport.pivot(noGroupReq(PIVOT_L3, PivotSqlTestSupport.sort("L3__score", 1)));

        Assert.assertTrue(sql.contains("ORDER BY `L3__score` DESC"), "无行维度仍应按别名排序: " + sql);
        Assert.assertFalse(sql.contains("GROUP BY"), sql);
    }

    /**
     * 前端未配透视列时下发 total 虚拟列（无条件）：走裸列聚合、别名为 total__score，同样不拼 GROUP BY
     */
    @Test
    public void testTotalFallbackColumnWithoutCondition() {
        String sql = PivotSqlTestSupport.pivot(noGroupReq(new MetricCondition("total", "", null)));

        Assert.assertFalse(sql.contains("GROUP BY"), sql);
        Assert.assertTrue(sql.contains("AS `total__score`"), sql);
        Assert.assertFalse(sql.contains("case when"), "无条件透视列不应包 CASE WHEN: " + sql);
    }

    /**
     * 负向腿：行维度与透视列同时为空必须拒——放宽的是"行维度可空"，不是"什么都没配也能查"
     * （Validator.assertTrue 抛的是 ServiceException，与 SqlIdentifierUtil 的 NoticeException 不同族）
     */
    @Test
    public void testBothGroupAndPivotEmptyRejected() {
        try {
            PivotSqlTestSupport.pivot(PivotSqlTestSupport.req(Collections.emptyList(), Collections.emptyList(),
                    new ArrayList<>(Collections.singletonList(PivotSqlTestSupport.sumMeasure("score"))), null));
            Assert.fail("行维度与透视列同时为空应在拼 SQL 前被拒");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("行维度列"), expected.getMessage());
        }
    }
}