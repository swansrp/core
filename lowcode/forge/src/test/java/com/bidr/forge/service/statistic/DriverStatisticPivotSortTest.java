package com.bidr.forge.service.statistic;

import com.bidr.forge.service.perm.ColumnAliasMap;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: DriverStatisticPivotSortTest
 * Description: {@link DriverStatisticSupportService#pivot} 外层 ORDER BY 的产物形态单测。
 * <p>
 * 透视报表点表头排序走的是「前端把列名当 sortList 下发 → 后端原样拼进反引号」这条通道，
 * 两个必须当场证伪的点：一是拼出来的 ORDER BY 只引用本次 SELECT 的输出别名（行维度字段名 或
 * ${透视列标识}__${度量字段}），二是列权限收窄时不能用裸物理列名排序把隐藏列的值反推出来。
 * 只看接口返回值这两个缺陷都不会暴露（错别名照样返回空集）。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-09
 */
public class DriverStatisticPivotSortTest {

    private static final MetricCondition PIVOT_L3 = new MetricCondition("L3", "三级",
            PivotSqlTestSupport.leaf("gradeLevel", 1, "3"));

    /**
     * 行维度 assessName + 透视列 L3 + 度量：sum(score) 及（可选）ratio(weightedScore)
     */
    private static AdvancedPivotReq scoreReq(boolean withRatio, SortVO... sorts) {
        List<PivotMeasure> measures = new ArrayList<>();
        measures.add(PivotSqlTestSupport.sumMeasure("score"));
        if (withRatio) {
            measures.add(PivotSqlTestSupport.ratioMeasure("weightedScore", "propStdScore", "phaseProp"));
        }
        return PivotSqlTestSupport.req(
                Collections.singletonList(new KeyValueResVO("assessName", "被考核人")),
                Collections.singletonList(PIVOT_L3),
                measures,
                sorts.length == 0 ? null : new ArrayList<>(Arrays.asList(sorts)));
    }

    /**
     * 单元格列按别名倒序：前端点「三级·得分」表头下发的就是 L3__score
     */
    @Test
    public void testSortByPivotCellAlias() {
        String sql = PivotSqlTestSupport.pivot(scoreReq(true, PivotSqlTestSupport.sort("L3__score", 1)));

        Assert.assertTrue(sql.contains("ORDER BY `L3__score` DESC"), "点单元格列未拼成按别名倒序: " + sql);
        Assert.assertTrue(sql.indexOf("GROUP BY") < sql.indexOf("ORDER BY"), "ORDER BY 必须在 GROUP BY 之后: " + sql);
    }

    /**
     * 比率度量的别名同样是真列，可正可倒；多键按下发顺序生效
     */
    @Test
    public void testMultiKeySortKeepsRequestOrder() {
        String sql = PivotSqlTestSupport.pivot(scoreReq(true,
                PivotSqlTestSupport.sort("assessName", 0),
                PivotSqlTestSupport.sort("L3__weightedScore", 1)));

        Assert.assertTrue(sql.endsWith("ORDER BY `assessName` ASC, `L3__weightedScore` DESC"),
                "多键排序顺序/方向不符: " + sql);
    }

    @Test
    public void testNoSortListEmitsNoOrderBy() {
        String sql = PivotSqlTestSupport.pivot(scoreReq(false));

        Assert.assertFalse(sql.contains("ORDER BY"), "未下发排序不应有 ORDER BY: " + sql);
    }

    /**
     * 空 property 跳过，不能留下 "ORDER BY , `x`" 这种半截子句
     */
    @Test
    public void testBlankPropertySkippedWithoutBreakingClause() {
        String sql = PivotSqlTestSupport.pivot(scoreReq(false,
                PivotSqlTestSupport.sort("", 0),
                PivotSqlTestSupport.sort("L3__score", 1)));

        Assert.assertTrue(sql.endsWith("ORDER BY `L3__score` DESC"), "空键应被跳过且子句完整: " + sql);
    }

    @Test
    public void testAllBlankPropertiesEmitNoOrderBy() {
        String sql = PivotSqlTestSupport.pivot(scoreReq(false,
                PivotSqlTestSupport.sort("", 0),
                PivotSqlTestSupport.sort(null, 1)));

        Assert.assertFalse(sql.contains("ORDER BY"), "全空键不应出现空 ORDER BY 子句: " + sql);
    }

    /**
     * 字段名来自请求体，含反引号即可逃逸 `...` 引用改写整条 SQL，与列权限无关都必须拦
     */
    @Test
    public void testUnsafeSortPropertyRejected() {
        try {
            PivotSqlTestSupport.pivot(scoreReq(false, PivotSqlTestSupport.sort("L3__score`; DROP TABLE t; --", 1)));
            Assert.fail("非法排序字段名应在拼 SQL 前被拒");
        } catch (NoticeException expected) {
            Assert.assertTrue(expected.getMessage().contains("非法字段名"), expected.getMessage());
        }
    }

    /**
     * 列权限收窄后：只认本次输出别名。score 是映射里的真实列（隐藏列仍留在映射中供条件解析）但不是本次输出列，
     * 以它排序能把隐藏列的值反推出来，必须拒；同一张权限地图下排 L3__score 是正路径，必须放行——
     * 缺这条判别腿的话本测只证明了「没崩」，证明不了「闸门真的在拦该拦的」
     */
    @Test
    public void testNarrowedColumnPermissionWhitelistsOutputAliasesOnly() {
        ColumnAliasMap narrowed = new ColumnAliasMap();
        narrowed.putAll(PivotSqlTestSupport.ALIAS_MAP);
        narrowed.markColumnFilterNarrowed(Arrays.asList("assessName", "gradeLevel", "propStdScore"),
                Arrays.asList("assessName", "gradeLevel"));

        String sql = PivotSqlTestSupport.pivot(scoreReq(false, PivotSqlTestSupport.sort("L3__score", 1)), narrowed);
        Assert.assertTrue(sql.contains("ORDER BY `L3__score` DESC"), "输出别名排序在收窄权限下仍应放行: " + sql);

        try {
            PivotSqlTestSupport.pivot(scoreReq(false, PivotSqlTestSupport.sort("score", 1)), narrowed);
            Assert.fail("列权限收窄时不得以非输出列排序");
        } catch (NoticeException expected) {
            Assert.assertTrue(expected.getMessage().contains("score"), expected.getMessage());
        }
    }
}
