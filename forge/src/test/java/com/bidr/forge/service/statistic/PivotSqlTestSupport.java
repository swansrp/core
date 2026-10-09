package com.bidr.forge.service.statistic;

import com.bidr.forge.engine.builder.BaseSqlBuilder;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: PivotSqlTestSupport
 * Description: {@link DriverStatisticSupportService#pivot} 的产物 SQL 断言工装。
 * <p>
 * 透视的排序/聚合语义全靠拼字符串落地，只看方法返回值无法证伪（拼错别名照样返回 0 行）。
 * 这里把执行层换成「把 SQL 留下、返回空集」的捕获桩，条件拼装与别名解析走真实实现，
 * 各形态单测据此断言最终交给 Doris 的那句话长什么样。
 * </p>
 *
 * @author Sharp
 * @since 2026-10-09
 */
final class PivotSqlTestSupport {

    /**
     * 字段别名映射（未收窄，即不启用列权限白名单）
     */
    static final Map<String, String> ALIAS_MAP = new HashMap<>();

    static {
        ALIAS_MAP.put("assessName", "t.assess_name");
        ALIAS_MAP.put("gradeLevel", "t.grade_level");
        ALIAS_MAP.put("propStdScore", "t.prop_std_score");
        ALIAS_MAP.put("phaseProp", "t.phase_prop");
        ALIAS_MAP.put("score", "t.score");
    }

    private PivotSqlTestSupport() {
    }

    /**
     * 只关心透视 SQL 拼装，执行层拦下来把 SQL 留下
     */
    static class CapturingJdbc extends JdbcConnectService {
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
     * 条件拼装用真实实现，其余 SQL 构建能力给最小实现（与 BaseSqlBuilderConditionValueTest 同形）
     */
    static class TestSqlBuilder extends BaseSqlBuilder {
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

    /**
     * 数据集式上下文：列引用即 别名.列，不加反引号
     */
    static class TestContext implements StatisticQueryContext {
        @Override
        public String getFromSql() {
            return "v_assess_detail AS t";
        }

        @Override
        public BaseSqlBuilder getConditionBuilder() {
            return BUILDER;
        }

        @Override
        public String formatColumnExpression(String dbColumnOrAlias) {
            return dbColumnOrAlias;
        }
    }

    private static final DriverStatisticSupportService SUPPORT = new DriverStatisticSupportService(null, null);

    static String pivot(AdvancedPivotReq req) {
        return pivot(req, ALIAS_MAP);
    }

    static String pivot(AdvancedPivotReq req, Map<String, String> aliasMap) {
        CapturingJdbc jdbc = new CapturingJdbc();
        SUPPORT.pivot(jdbc, req, new TestContext(), aliasMap);
        return jdbc.sql;
    }

    static AdvancedQuery leaf(String property, Integer relation, Object... values) {
        AdvancedQuery query = new AdvancedQuery();
        query.setProperty(property);
        query.setRelation(relation);
        query.setValue(new ArrayList<>(Arrays.asList(values)));
        return query;
    }

    static PivotMeasure sumMeasure(String field) {
        PivotMeasure measure = new PivotMeasure();
        measure.setField(field);
        measure.setAgg("sum");
        return measure;
    }

    static PivotMeasure ratioMeasure(String field, String numerator, String denominator) {
        PivotMeasure measure = new PivotMeasure();
        measure.setField(field);
        measure.setAgg("ratio");
        measure.setNumerator(numerator);
        measure.setDenominator(denominator);
        return measure;
    }

    /**
     * 常用形态：行维度 assessName + 单个透视列 + 若干度量
     */
    static AdvancedPivotReq req(MetricCondition pivot, PivotMeasure... measures) {
        return req(Collections.singletonList(new KeyValueResVO("assessName", "被考核人")),
                Collections.singletonList(pivot), new ArrayList<>(Arrays.asList(measures)), null);
    }

    static AdvancedPivotReq req(List<KeyValueResVO> groupColumns, List<MetricCondition> pivotColumns,
                                List<PivotMeasure> measures, List<SortVO> sortList) {
        AdvancedPivotReq req = new AdvancedPivotReq();
        req.setGroupColumns(groupColumns);
        req.setPivotColumns(pivotColumns);
        req.setMeasures(measures);
        req.setSortList(sortList);
        return req;
    }

    static SortVO sort(String property, Integer type) {
        return new SortVO(property, type);
    }
}
