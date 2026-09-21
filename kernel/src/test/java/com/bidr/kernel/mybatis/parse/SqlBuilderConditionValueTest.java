package com.bidr.kernel.mybatis.parse;

import com.bidr.kernel.mybatis.bo.SqlColumn;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: SqlBuilderConditionValueTest
 * Description: {@link SqlBuilder} 高级查询条件值清洗与残缺 SQL 防御单测（Dataset 列表查询路径，字面量内联）。
 * <p>
 * 线上故障形态：前端筛选项脏配置 value:[[]] 经本类内联后得到 `t.project_lead_dept = ` ，
 * Doris 直接报 1105 Syntax error，整张报表（列表 + 汇总栏）500。
 * 期望口径：脏值整条条件跳过（AND→1=1 / OR→1<>1），合法值（含空串）正常内联为字面量。
 *
 * @author Sharp
 * @since 2026-09-20
 */
public class SqlBuilderConditionValueTest {

    private static final List<SqlColumn> COLUMNS = Arrays.asList(
            new SqlColumn("t.project_name", "projectName"),
            new SqlColumn("t.project_lead_dept", "projectLeadDept"),
            new SqlColumn("t.pmp_state", "pmpState"));

    private static AdvancedQuery leaf(String property, Integer relation, Object... values) {
        AdvancedQuery query = new AdvancedQuery();
        query.setProperty(property);
        query.setRelation(relation);
        query.setValue(new ArrayList<>(Arrays.asList(values)));
        return query;
    }

    private static AdvancedQuery group(String andOr, AdvancedQuery... children) {
        AdvancedQuery query = new AdvancedQuery();
        query.setAndOr(andOr);
        query.setConditionList(new ArrayList<>(Arrays.asList(children)));
        return query;
    }

    private static String whereSqlOf(AdvancedQuery where) {
        String sql = SqlBuilder.buildSql(COLUMNS, "(SELECT 1) AS t", where, null, null, null, null);
        int whereIndex = sql.indexOf(" WHERE ");
        Assert.assertTrue("应生成 WHERE 子句: " + sql, whereIndex >= 0);
        return sql.substring(whereIndex + " WHERE ".length());
    }

    @Test
    public void testDirtyNestedValueSkippedWithoutBrokenSql() {
        // 复现线上请求：projectName=特斯拉 + projectLeadDept=[[]] + pmpState=1
        AdvancedQuery where = group(AdvancedQuery.AND,
                leaf("projectName", 1, "特斯拉"),
                leaf("projectLeadDept", 1, Collections.emptyList()),
                leaf("pmpState", 1, "1"));

        String sql = whereSqlOf(where);
        Assert.assertTrue(sql, sql.contains("t.project_name = '特斯拉'"));
        Assert.assertTrue(sql, sql.contains("t.pmp_state = '1'"));
        // 脏值条件整条剔除，不能留下 `= ` 这种残缺片段
        Assert.assertFalse(sql, sql.contains("project_lead_dept"));
        assertNoDanglingOperator(sql);
    }

    @Test
    public void testEmptyStringIsLegalValue() {
        String sql = whereSqlOf(leaf("projectName", 1, ""));
        Assert.assertEquals("t.project_name = ''", sql);
    }

    @Test
    public void testLikeUsesFirstScalarValue() {
        // 原实现把整个 List 传给 buildSmartLike，得到 LIKE '%[特斯拉]%' 这种永远匹配不到的条件
        String sql = whereSqlOf(leaf("projectName", 9, "特斯拉"));
        Assert.assertEquals("t.project_name LIKE '%特斯拉%'", sql);
    }

    @Test
    public void testBetweenWithInsufficientValueSkipped() {
        String sql = whereSqlOf(leaf("projectName", 13, "a"));
        Assert.assertEquals("1=1", sql);
    }

    @Test
    public void testInCleansNestedElement() {
        String sql = whereSqlOf(leaf("projectName", 11, "a", Arrays.asList("junk"), "b"));
        Assert.assertEquals("t.project_name IN ('a', 'b')", sql);
    }

    @Test
    public void testUnknownRelationSkipped() {
        // 未知关系码此前会在 switch(null) 处抛 NPE，现在整条跳过
        String sql = whereSqlOf(leaf("projectName", 999, "a"));
        Assert.assertEquals("1=1", sql);
    }

    @Test
    public void testNullAndNotNullNeedNoValue() {
        Assert.assertEquals("t.project_name IS NULL", whereSqlOf(leaf("projectName", 7)));
        Assert.assertEquals("t.project_name IS NOT NULL", whereSqlOf(leaf("projectName", 8)));
    }

    @Test
    public void testContainWithOnlyEmptyValueKeepsSqlValid() {
        // 全部值为空串时 buildContain 会拼出空片段，外层括号包裹后即为非法的 ()
        AdvancedQuery where = group(AdvancedQuery.AND,
                leaf("projectName", 15, ""),
                leaf("pmpState", 1, "1"));
        String sql = whereSqlOf(where);
        Assert.assertTrue(sql, sql.contains("t.pmp_state = '1'"));
        Assert.assertFalse(sql, sql.contains("()"));
        assertNoDanglingOperator(sql);
    }

    @Test
    public void testOrGroupFallsToFalseIdentity() {
        AdvancedQuery where = group(AdvancedQuery.OR,
                leaf("projectName", 1, Collections.emptyList()),
                leaf("pmpState", 1, "1"));
        String sql = whereSqlOf(where);
        Assert.assertTrue(sql, sql.contains("1<>1"));
        assertNoDanglingOperator(sql);
    }

    /**
     * 断言不存在运算符右侧缺失的残缺片段（`xxx = ` / `xxx IN ()` / `xxx = AND`）
     */
    private static void assertNoDanglingOperator(String sql) {
        Assert.assertFalse("存在悬空运算符: " + sql, sql.matches("(?s).*[=<>] +(AND|OR|\\)).*"));
        Assert.assertFalse("存在空括号: " + sql, sql.contains("()"));
        Assert.assertFalse("存在空 IN 列表: " + sql, sql.matches("(?s).*IN +\\( *\\).*"));
    }
}
