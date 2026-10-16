package com.bidr.forge.service.statistic;

import com.bidr.forge.bo.DatasetColumns;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.engine.builder.BaseSqlBuilder;
import com.bidr.forge.engine.driver.DatasetDriver;
import com.bidr.forge.utils.PortalDatasetSqlUtil;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: DatasetSummarySqlTest
 * Description: Dataset 汇总（summary）产物 SQL 的列名口径断言。
 * <p>
 * Dataset 汇总把请求条件拼在预览子查询的<b>外层</b>，外层只能看到 columnAlias；
 * 一旦别名映射给出物理列（portal 的 buildAliasMap 口径），就拼出 {@code t.pmp_state} 这种
 * 外层不可见的引用，Doris 直接报 Unknown column。这里用真实上下文与真实构建器把最终 SQL 抓下来断言，
 * 并保留「错误映射照样产出错误 SQL」的负向腿，证明断言本身没有失效。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-16
 */
public class DatasetSummarySqlTest {

    private static final String CAMEL_ALIAS = "pmpState";
    private static final String PHYSICAL_COLUMN = "t.pmp_state";

    private static final DriverStatisticSupportService SUPPORT = new DriverStatisticSupportService(null, null);

    /**
     * 执行层拦下来留 SQL，其余（预览子查询、条件拼装、别名解析）走真实实现
     */
    private static class CapturingJdbc extends JdbcConnectService {
        private String sql;

        CapturingJdbc() {
            super(null, null, null);
        }

        @Override
        public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> parameters) {
            this.sql = sql;
            return Collections.emptyList();
        }
    }

    /**
     * 复刻线上数据集形态：主表别名 t，输出列别名为驼峰
     */
    private static DatasetStatisticQueryContext newContext(String baseFilter) {
        DatasetColumns datasetColumns = new DatasetColumns();
        datasetColumns.setId(1L);

        SysDatasetTable table = new SysDatasetTable();
        table.setId(289L);
        table.setDatasetId(1L);
        table.setTableOrder(1);
        table.setTableSql("dw_dws.dws_dc_project_compliance_dyf");
        table.setTableAlias("t");

        List<SysDatasetColumn> columns = new ArrayList<>(Arrays.asList(
                column(1, "t.dy", "dy"),
                column(2, PHYSICAL_COLUMN, CAMEL_ALIAS),
                column(3, "t.overdue_count", "overdueCount")));

        return new DatasetStatisticQueryContext(datasetColumns, Collections.singletonList(table), columns, baseFilter);
    }

    private static SysDatasetColumn column(int order, String columnSql, String alias) {
        SysDatasetColumn column = new SysDatasetColumn();
        column.setDatasetId(1L);
        column.setDisplayOrder(order);
        column.setColumnSql(columnSql);
        column.setColumnAlias(alias);
        column.setIsAggregate(CommonConst.NO);
        column.setIsVisible(CommonConst.YES);
        return column;
    }

    private static AdvancedSummaryReq summaryReq() {
        AdvancedQuery leaf = new AdvancedQuery();
        leaf.setProperty(CAMEL_ALIAS);
        leaf.setRelation(1);
        leaf.setValue(new ArrayList<>(Collections.singletonList("1")));

        AdvancedSummaryReq req = new AdvancedSummaryReq();
        req.setColumns(Collections.singletonList("overdueCount"));
        req.setCondition(leaf);
        return req;
    }

    private static String runSummary(Map<String, String> aliasMap) {
        CapturingJdbc jdbc = new CapturingJdbc();
        SUPPORT.summary(jdbc, summaryReq(), newContext(null), aliasMap);
        return jdbc.sql;
    }

    /**
     * 正向腿：统计口径的别名映射（value=columnAlias）产出外层可见的驼峰引用
     */
    @Test
    public void summaryShouldFilterByOutputAliasNotPhysicalColumn() {
        Map<String, String> aliasMap = new DatasetDriver(null, null, null, null, null)
                .buildStatisticAliasMap(Arrays.asList(
                        column(1, "t.dy", "dy"),
                        column(2, PHYSICAL_COLUMN, CAMEL_ALIAS),
                        column(3, "t.overdue_count", "overdueCount")));

        String sql = runSummary(aliasMap);

        Assert.assertTrue(sql.contains("WHERE t." + CAMEL_ALIAS + " = :param_0"),
                "汇总外层条件应引用子查询输出别名，实际 SQL: " + sql);
        Assert.assertFalse(sql.contains(PHYSICAL_COLUMN + " ="),
                "汇总外层不应出现物理列引用，实际 SQL: " + sql);
    }

    /**
     * 负向腿（针还活着）：换成 portal 口径的别名映射（value=columnSql），同样的断言必须失败
     */
    @Test
    public void physicalColumnAliasMapStillReproducesTheDefect() {
        Map<String, String> wrongAliasMap = new HashMap<>();
        wrongAliasMap.put(CAMEL_ALIAS, PHYSICAL_COLUMN);

        String sql = runSummary(wrongAliasMap);

        Assert.assertTrue(sql.contains("WHERE " + PHYSICAL_COLUMN + " = :param_0"),
                "错误映射应复现外层引用物理列的形态，实际 SQL: " + sql);
    }

    /**
     * 常驻过滤谓词仍留在内层（预览子查询），不与外层条件重复拼接
     */
    @Test
    public void baseFilterStaysInPreviewSubQuery() {
        CapturingJdbc jdbc = new CapturingJdbc();
        DatasetStatisticQueryContext ctx = newContext("t.dy = '${currentYear}'");

        SUPPORT.summary(jdbc, summaryReq(), ctx, new DatasetDriver(null, null, null, null, null)
                .buildStatisticAliasMap(Arrays.asList(
                        column(1, "t.dy", "dy"),
                        column(2, PHYSICAL_COLUMN, CAMEL_ALIAS),
                        column(3, "t.overdue_count", "overdueCount"))));

        String sql = jdbc.sql;
        int firstWhere = sql.indexOf(" WHERE ");
        int lastWhere = sql.lastIndexOf(" WHERE ");
        Assert.assertTrue(firstWhere > 0, "内层应带常驻过滤谓词，实际 SQL: " + sql);
        Assert.assertNotEquals(firstWhere, lastWhere, "常驻谓词与请求条件应分处内外两层，实际 SQL: " + sql);
        Assert.assertEquals(ctx.getPreviewParams().size(), 1,
                "常驻谓词变量应参数化，实际 SQL: " + sql);
    }

    /**
     * 别名映射口径：下划线别名额外登记驼峰 key，value 仍是子查询真实暴露的别名
     */
    @Test
    public void statisticAliasMapRegistersCamelKeyForUnderlineAlias() {
        Map<String, String> aliasMap = new DatasetDriver(null, null, null, null, null)
                .buildStatisticAliasMap(Arrays.asList(
                        column(1, "x", "pmp_state"),
                        column(2, "x", "overdueCount")));

        Assert.assertEquals(aliasMap.get("pmp_state"), "pmp_state");
        Assert.assertEquals(aliasMap.get("pmpState"), "pmp_state",
                "驼峰 key 必须映射回真实暴露的下划线别名");
        Assert.assertEquals(aliasMap.get("overdueCount"), "overdueCount");
        Assert.assertFalse(aliasMap.containsKey("overdue_count"), "已是驼峰的别名不应再造反向 key");
    }

    /**
     * 冲突取舍：同名驼峰变体不得覆盖另一列的精确别名
     */
    @Test
    public void exactAliasWinsOverCamelVariantOfAnotherColumn() {
        Map<String, String> aliasMap = new DatasetDriver(null, null, null, null, null)
                .buildStatisticAliasMap(Arrays.asList(
                        column(1, "x", "pmp_state"),
                        column(2, "y", "pmpState")));

        Assert.assertEquals(aliasMap.get("pmpState"), "pmpState");
    }

    /**
     * 无条件汇总仍走单条 WHERE 之外的分支（不带外层 WHERE）
     */
    @Test
    public void summaryWithoutConditionHasNoOuterWhere() {
        AdvancedSummaryReq req = new AdvancedSummaryReq();
        req.setColumns(Collections.singletonList("overdueCount"));

        CapturingJdbc jdbc = new CapturingJdbc();
        SUPPORT.summary(jdbc, req, newContext(null), Collections.emptyMap());

        Assert.assertFalse(jdbc.sql.contains(" WHERE "), "无条件汇总不应拼出 WHERE: " + jdbc.sql);
    }

    /**
     * 上下文条件构建器与预览构建器分工：条件构建器不再重复拼 base_filter
     */
    @Test
    public void conditionBuilderDoesNotReApplyBaseFilter() {
        DatasetStatisticQueryContext ctx = newContext("t.dw_cdt = 'A'");
        BaseSqlBuilder builder = ctx.getConditionBuilder();

        String clauses = builder.buildQueryClauses(summaryReq(), Collections.singletonMap(CAMEL_ALIAS, CAMEL_ALIAS),
                new HashMap<>(), false);

        Assert.assertFalse(clauses.contains("dw_cdt"), "外层条件子句不应重复带出常驻谓词: " + clauses);
        Assert.assertTrue(clauses.contains("t.pmpState"), "外层条件子句应引用输出别名: " + clauses);
    }

    /**
     * 汇总列名来自请求体且直接拼进 SUM(col)：非法字集必须在送执行前被拒，不留畸形 SQL
     */
    @Test
    public void illegalSummaryColumnRejectedBeforeExecution() {
        List<String> payloads = Arrays.asList(
                "overdueCount) FROM x UNION SELECT pwd FROM users --",
                "overdue`Count");

        for (String payload : payloads) {
            AdvancedSummaryReq req = summaryReq();
            req.setColumns(Collections.singletonList(payload));
            CapturingJdbc jdbc = new CapturingJdbc();
            try {
                SUPPORT.summary(jdbc, req, newContext(null), Collections.emptyMap());
                Assert.fail("非法汇总列名应被拒绝: " + payload);
            } catch (ServiceException expected) {
                Assert.assertNull(jdbc.sql, "拒绝应发生在 SQL 送出执行之前: " + payload);
            }
        }
    }

    /**
     * 同族第二处：旧的 buildSumSql 走同一不变式，正向列名照常产出
     */
    @Test
    public void legacyBuildSumSqlGuardsSameInvariant() {
        Assert.assertTrue(PortalDatasetSqlUtil.buildSumSql("SELECT x FROM t", Collections.singletonList("overdueCount"))
                .contains("SUM(overdueCount) AS overdueCount"), "合法列名不应被拦");

        try {
            PortalDatasetSqlUtil.buildSumSql("SELECT x FROM t",
                    Collections.singletonList("a) FROM b UNION SELECT 1 --"));
            Assert.fail("非法汇总列名应被拒绝");
        } catch (ServiceException expected) {
            // 拒绝即通过：注入形态不得拼进 SQL
        }
    }
}
