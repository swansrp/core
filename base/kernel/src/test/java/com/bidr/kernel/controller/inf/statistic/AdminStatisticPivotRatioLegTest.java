package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: AdminStatisticPivotRatioLegTest
 * Description: {@link AdminStatisticPivotInf#buildRatioExpr} 比率两腿聚合除法壳单测。
 * <p>
 * 腿聚合缺省=sum（不配置时产物与老版本逐字节一致）；白名单外拒收——
 * 拼 SQL 层静默回落会把错数伪装成正常数字。kernel 实体模式与 forge 数据集模式
 * 两个生成点共用该实现，此处同时覆盖两条通道的比率形态。
 *
 * @author Sharp
 * @since 2026-10-27
 */
public class AdminStatisticPivotRatioLegTest {

    private static PivotMeasure ratioMeasure(String numeratorAgg, String denominatorAgg) {
        PivotMeasure measure = new PivotMeasure();
        measure.setField("achieveRate");
        measure.setAgg(AdminStatisticPivotInf.AGG_RATIO);
        measure.setNumerator("actualAmt");
        measure.setDenominator("targetAmt");
        measure.setNumeratorAgg(numeratorAgg);
        measure.setDenominatorAgg(denominatorAgg);
        return measure;
    }

    @Test
    public void testDefaultLegsKeepLegacyShape() {
        String sql = AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, null),
                "case when c then a else null end", "case when c then b else null end");
        Assert.assertEquals("sum(case when c then a else null end)"
                + " / nullif(sum(case when c then b else null end), 0)", sql);
    }

    @Test
    public void testDenominatorMaxForConstantTarget() {
        String sql = AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, AdminStatisticPivotInf.AGG_MAX),
                "a_expr", "b_expr");
        Assert.assertEquals("sum(a_expr) / nullif(max(b_expr), 0)", sql);
    }

    @Test
    public void testExplicitSumEqualsDefault() {
        Assert.assertEquals(
                AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, null), "N", "D"),
                AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(AdminStatisticPivotInf.AGG_SUM, AdminStatisticPivotInf.AGG_SUM), "N", "D"));
    }

    @Test
    public void testLegAggWhitelistCoversAllAggregations() {
        Assert.assertEquals("count(distinct N) / nullif(sum(D), 0)",
                AdminStatisticPivotInf.buildRatioExpr(ratioMeasure("countDistinct", null), "N", "D"));
        Assert.assertEquals("sum(N) / nullif(count(D), 0)",
                AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, "count"), "N", "D"));
        Assert.assertEquals("sum(N) / nullif(avg(D), 0)",
                AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, "avg"), "N", "D"));
    }

    @Test
    public void testInvalidLegAggRejected() {
        try {
            AdminStatisticPivotInf.buildRatioExpr(ratioMeasure(null, "median"), "N", "D");
            Assert.fail("白名单外的腿聚合应被拒收，静默回落会产出错数");
        } catch (ServiceException expected) {
            Assert.assertTrue("异常文案应含'聚合方式非法': " + expected.getMessage(),
                    expected.getMessage().contains("聚合方式非法"));
        }
    }
}
