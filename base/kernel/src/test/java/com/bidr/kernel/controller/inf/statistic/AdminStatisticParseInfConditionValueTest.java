package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.vo.portal.AdvancedQuery;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: AdminStatisticParseInfConditionValueTest
 * Description: {@link AdminStatisticParseInf#buildQueryStr} 条件值清洗与残缺 SQL 防御单测（管理端统计列路径，字面量内联）。
 * <p>
 * 该实现按值逐个拼接字面量，脏值（嵌套数组）会得到 `'[]'` 这种永远匹配不到的条件，
 * 值个数不足（如 between 只给 1 个值）更会拼出 `col between 'x' and ` 让数据库直接语法报错。
 *
 * @author Sharp
 * @since 2026-09-20
 */
public class AdminStatisticParseInfConditionValueTest {

    private static final AdminStatisticParseInf PARSER = new AdminStatisticParseInf() {
    };

    private static AdvancedQuery leaf(String property, Integer relation, String andOr, Object... values) {
        AdvancedQuery query = new AdvancedQuery();
        query.setProperty(property);
        query.setRelation(relation);
        query.setAndOr(andOr);
        query.setValue(new ArrayList<>(Arrays.asList(values)));
        return query;
    }

    @Test
    public void testNestedValueFallsToIdentity() {
        String sql = PARSER.buildQueryStr(leaf("project_name", 1, AdvancedQuery.AND, Collections.emptyList()));
        Assert.assertEquals("1=1", sql);
    }

    @Test
    public void testInsufficientValueForBetweenFallsToIdentity() {
        String sql = PARSER.buildQueryStr(leaf("stat_date", 13, AdvancedQuery.AND, "2026-01-01"));
        Assert.assertEquals("1=1", sql);
    }

    @Test
    public void testBetweenWithTwoValuesBuildsValidSql() {
        String sql = PARSER.buildQueryStr(leaf("stat_date", 13, AdvancedQuery.AND, "2026-01-01", "2026-02-01"));
        Assert.assertEquals("stat_date between '2026-01-01' and '2026-02-01'", sql);
    }

    @Test
    public void testEqualUsesFirstValueOnly() {
        // 原实现用全量值拼接，多值时得到 `col = 'a','b'` 这种非法语句
        String sql = PARSER.buildQueryStr(leaf("project_name", 1, AdvancedQuery.AND, "a", "b"));
        Assert.assertEquals("project_name = 'a'", sql);
    }

    @Test
    public void testInCleansNestedElement() {
        String sql = PARSER.buildQueryStr(leaf("project_name", 11, AdvancedQuery.AND, "a", Arrays.asList("junk")));
        Assert.assertEquals("project_name in ('a')", sql);
    }

    @Test
    public void testNullRelationNeedsNoValue() {
        Assert.assertEquals("project_name is null",
                PARSER.buildQueryStr(leaf("project_name", 7, AdvancedQuery.AND)));
        Assert.assertEquals("project_name is not null",
                PARSER.buildQueryStr(leaf("project_name", 8, AdvancedQuery.AND)));
    }

    @Test
    public void testUnknownRelationFallsToIdentity() {
        // 未知关系码此前会在 switch(null) 处抛 NPE
        Assert.assertEquals("1=0", PARSER.buildQueryStr(leaf("project_name", 999, AdvancedQuery.OR, "a")));
    }
}
