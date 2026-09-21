package com.bidr.forge.engine.builder;

import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: BaseSqlBuilderConditionValueTest
 * Description: {@link BaseSqlBuilder} 高级查询条件值清洗单测（Dataset/Matrix 命名参数路径）。
 * <p>
 * 线上故障形态：筛选项脏配置 value:[[]] 被当作命名参数绑定，NamedParameterJdbcTemplate 会把集合值
 * 展开成逗号分隔占位符列表，空集合展开后一个占位符都不剩，于是得到 `t.project_lead_dept = ` ，
 * Doris 直接报 1105 Syntax error，整张报表 500。
 * 期望口径：值先清洗再按关系类型所需个数校验，不足则整条条件跳过（返回空片段，由调用方丢弃）。
 *
 * @author Sharp
 * @since 2026-09-20
 */
public class BaseSqlBuilderConditionValueTest {

    private static final Map<String, String> ALIAS_MAP = new HashMap<>();

    static {
        ALIAS_MAP.put("projectName", "t.project_name");
        ALIAS_MAP.put("projectLeadDept", "t.project_lead_dept");
        ALIAS_MAP.put("pmpState", "t.pmp_state");
        ALIAS_MAP.put("statDate", "t.stat_date");
    }

    /**
     * 只验证条件拼装，其余 SQL 构建能力给最小实现
     */
    private static class TestSqlBuilder extends BaseSqlBuilder {
        @Override
        public String buildSelect(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
            return "";
        }

        @Override
        public String buildCount(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
            return "";
        }

        @Override
        public String buildInsert(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
            return "";
        }

        @Override
        public String buildUpdate(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
            return "";
        }

        @Override
        public String buildDelete(Object id, Map<String, Object> parameters) {
            return "";
        }

        @Override
        protected String buildSelectColumns(Map<String, String> aliasMap) {
            return "*";
        }

        @Override
        protected String buildFromClause() {
            return " FROM t";
        }

        @Override
        public String buildQueryClauses(AdvancedQueryReq req, Map<String, String> aliasMap,
                                        Map<String, Object> parameters, boolean includeOrder) {
            return "";
        }
    }

    private static final TestSqlBuilder BUILDER = new TestSqlBuilder();

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

    @Test
    public void testDirtyNestedValueSkipped() {
        // 复现线上请求：projectName=特斯拉 + projectLeadDept=[[]] + pmpState=1
        AdvancedQuery where = group(AdvancedQuery.AND,
                leaf("projectName", 1, "特斯拉"),
                leaf("projectLeadDept", 1, Collections.emptyList()),
                leaf("pmpState", 1, "1"));
        Map<String, Object> parameters = new HashMap<>();

        String sql = BUILDER.buildWhereCondition(where, ALIAS_MAP, parameters);

        Assert.assertEquals(sql, "(t.project_name = :param_0) AND (t.pmp_state = :param_1)");
        Assert.assertEquals(parameters.size(), 2);
        Assert.assertEquals(parameters.get("param_0"), "特斯拉");
        Assert.assertEquals(parameters.get("param_1"), "1");
    }

    @Test
    public void testEmptyStringIsLegalBindValue() {
        Map<String, Object> parameters = new HashMap<>();
        String sql = BUILDER.buildWhereCondition(leaf("projectName", 1, ""), ALIAS_MAP, parameters);

        Assert.assertEquals(sql, "t.project_name = :param_0");
        Assert.assertEquals(parameters.get("param_0"), "");
    }

    @Test
    public void testLikeBindsSinglePatternValue() {
        Map<String, Object> parameters = new HashMap<>();
        String sql = BUILDER.buildWhereCondition(leaf("projectName", 9, "特斯拉"), ALIAS_MAP, parameters);

        Assert.assertEquals(sql, "t.project_name LIKE :param_0");
        Assert.assertEquals(parameters.get("param_0"), "%特斯拉%");
    }

    @Test
    public void testInCleansNestedElement() {
        Map<String, Object> parameters = new HashMap<>();
        String sql = BUILDER.buildWhereCondition(leaf("projectName", 11, "a", Arrays.asList("junk"), "b"),
                ALIAS_MAP, parameters);

        Assert.assertEquals(sql, "t.project_name IN (:param_0)");
        Assert.assertEquals((List<?>) parameters.get("param_0"), Arrays.asList("a", "b"));
    }

    @Test
    public void testInWithoutUsableValueSkipped() {
        Map<String, Object> parameters = new HashMap<>();
        String sql = BUILDER.buildWhereCondition(leaf("projectName", 11, Collections.emptyList()), ALIAS_MAP, parameters);

        Assert.assertEquals(sql, "");
        Assert.assertTrue(parameters.isEmpty());
    }

    @Test
    public void testBetweenNeedsTwoValues() {
        Map<String, Object> skipped = new HashMap<>();
        Assert.assertEquals(BUILDER.buildWhereCondition(leaf("statDate", 13, "2026-01-01"), ALIAS_MAP, skipped), "");
        Assert.assertTrue(skipped.isEmpty());

        Map<String, Object> parameters = new HashMap<>();
        String sql = BUILDER.buildWhereCondition(leaf("statDate", 13, "2026-01-01", "2026-02-01"), ALIAS_MAP, parameters);
        Assert.assertEquals(sql, "t.stat_date BETWEEN :param_0_start AND :param_0_end");
        Assert.assertEquals(parameters.get("param_0_start"), "2026-01-01");
        Assert.assertEquals(parameters.get("param_0_end"), "2026-02-01");
    }

    @Test
    public void testNullAndNotNullNeedNoValue() {
        Map<String, Object> parameters = new HashMap<>();
        Assert.assertEquals(BUILDER.buildWhereCondition(leaf("projectName", 7), ALIAS_MAP, parameters),
                "t.project_name IS NULL");
        Assert.assertEquals(BUILDER.buildWhereCondition(leaf("projectName", 8), ALIAS_MAP, parameters),
                "t.project_name IS NOT NULL");
        Assert.assertTrue(parameters.isEmpty());
    }

    @Test
    public void testUnknownRelationAndUnknownFieldSkipped() {
        Map<String, Object> parameters = new HashMap<>();
        Assert.assertEquals(BUILDER.buildWhereCondition(leaf("projectName", 999, "a"), ALIAS_MAP, parameters), "");
        Assert.assertTrue(parameters.isEmpty());
    }

    @Test
    public void testEveryBindValueIsUsableAsSingleValue() {
        // 核心不变量：绑定值只能是标量，或"全为标量的非空集合"（IN/NOT_IN 才允许集合参数）
        AdvancedQuery where = group(AdvancedQuery.AND,
                leaf("projectName", 1, "特斯拉"),
                leaf("projectLeadDept", 1, Collections.emptyList()),
                leaf("pmpState", 15, "1", Collections.singletonList("junk"), "2"),
                leaf("statDate", 13, "2026-01-01"));
        Map<String, Object> parameters = new HashMap<>();

        String sql = BUILDER.buildWhereCondition(where, ALIAS_MAP, parameters);
        Assert.assertFalse(sql.contains("()"), "不应生成空片段: " + sql);

        parameters.forEach((key, value) -> Assert.assertTrue(isBindable(value),
                key + " 的绑定值不可用于单个 SQL 值: " + value));
    }

    private static boolean isBindable(Object value) {
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                return false;
            }
            for (Object item : collection) {
                if (item instanceof Collection || item == null) {
                    return false;
                }
            }
            return true;
        }
        return !(value instanceof Map) && !(value instanceof Collection) && value != null;
    }
}
