package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.Query;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Title: AdminStatisticPivotInf
 * Description: 透视报表聚合查询
 * 按行维度列 GROUP BY，每个父表头列 × 每个度量列生成一个条件聚合表达式
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/8/10
 */
public interface AdminStatisticPivotInf<ENTITY, VO> extends AdminStatisticBaseInf<ENTITY, VO>, AdminStatisticParseInf {

    String AGG_SUM = "sum";
    String AGG_COUNT = "count";
    String AGG_AVG = "avg";
    String AGG_MIN = "min";
    String AGG_MAX = "max";
    String AGG_COUNT_DISTINCT = "countDistinct";
    String AGG_RATIO = "ratio";
    String PIVOT_ALIAS_SPLITTER = "__";

    /**
     * 列输出键安全字符集：与 forge 侧 SqlIdentifierUtil 同口径（kernel 不能依赖 forge）。
     * 输出键会被拼进 SELECT 别名的引号位置，必须排除引号/反引号/空白等可改写语句结构的字符。
     */
    Pattern SAFE_MEASURE_KEY = Pattern.compile("^[A-Za-z0-9_$.\\-\\u4e00-\\u9fa5]+$");

    /**
     * 度量输出键：config 未配 key 时回落到字段名（老配置零行为变更）
     */
    static String measureKey(PivotMeasure measure) {
        return FuncUtil.isEmpty(measure.getKey()) ? measure.getField() : measure.getKey();
    }

    /**
     * 输出键是否可拼进反引号别名（同字段多条件时前端会生成 field_2 这类后缀）
     */
    static boolean isSafeMeasureKey(String key) {
        return key != null && !key.contains("--") && SAFE_MEASURE_KEY.matcher(key).matches();
    }

    /**
     * 条件树里是否存在至少一条真正生效的叶子条件（空对象 {}、空 conditionList 均视为无）
     */
    static boolean hasCondition(AdvancedQuery query) {
        if (query == null) {
            return false;
        }
        if (FuncUtil.isNotEmpty(query.getProperty())) {
            return true;
        }
        if (FuncUtil.isEmpty(query.getConditionList())) {
            return false;
        }
        for (AdvancedQuery sub : query.getConditionList()) {
            if (hasCondition(sub)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 透视列条件与度量自身条件按 AND 叠加：列条件圈定父表头的切片，度量条件再圈定该度量自己的口径。
     * 任一侧无有效条件时原样返回另一侧，避免多包一层改变既有 SQL 形状。
     */
    static AdvancedQuery mergeCondition(AdvancedQuery pivotCondition, AdvancedQuery measureCondition) {
        boolean pivotHas = hasCondition(pivotCondition);
        boolean measureHas = hasCondition(measureCondition);
        if (!measureHas) {
            return pivotCondition;
        }
        if (!pivotHas) {
            return measureCondition;
        }
        AdvancedQuery merged = new AdvancedQuery();
        merged.setAndOr(AdvancedQuery.AND);
        merged.getConditionList().add(pivotCondition);
        merged.getConditionList().add(measureCondition);
        return merged;
    }

    /**
     * 透视聚合查询
     * sortList 语义为聚合后的外层行排序(区别于普通查询的内层数据排序)
     *
     * @param req 透视请求
     * @return 聚合后的平铺行数据 列别名为 ${父表头列标识}__${度量字段}
     */
    default List<Map<String, Object>> pivotByAdvancedReq(AdvancedPivotReq req) {
        // 先剥离排序: 避免被 Query 构造器带入 FROM 子查询(对聚合结果无效)
        List<SortVO> outerSort = req.getSortList();
        req.setSortList(null);
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Validator.assertNotEmpty(req.getGroupColumns(), ErrCodeSys.PA_DATA_NOT_EXIST, "行维度列");
        Validator.assertNotEmpty(req.getPivotColumns(), ErrCodeSys.PA_DATA_NOT_EXIST, "透视列");
        Validator.assertNotEmpty(req.getMeasures(), ErrCodeSys.PA_DATA_NOT_EXIST, "度量列");
        // 实体数据源这条腿用 MP wrapper 拼 SQL，尚未接 HAVING：静默丢掉条件会返回未过滤的全量行，
        // 表现成"筛了没反应"的假象，故显式拒绝（动态 Dataset/Matrix 走 DriverStatisticSupportService，已支持）
        Validator.assertTrue(FuncUtil.isEmpty(req.getHavingCondition()), ErrCodeSys.PA_DATA_NOT_SUPPORT,
                "实体数据源的透视报表暂不支持统计列过滤");
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        // 行维度列：select + groupBy
        for (KeyValueResVO group : req.getGroupColumns()) {
            Validator.assertNotBlank(group.getValue(), ErrCodeSys.PA_DATA_NOT_EXIST, "行维度字段");
            wrapper.getSelectColum().add(new SelectString(
                    String.format("%s as '%s'", group.getValue(), group.getValue()), wrapper.getAlias()));
            wrapper.groupBy(group.getValue());
        }
        // 透视列 × 度量列：条件聚合
        for (MetricCondition pivot : req.getPivotColumns()) {
            Validator.assertNotBlank(pivot.getValue(), ErrCodeSys.PA_DATA_NOT_EXIST, "透视列标识");
            Validator.assertNotNull(pivot.getCondition(), ErrCodeSys.PA_DATA_NOT_EXIST, "透视列条件");
            for (PivotMeasure measure : req.getMeasures()) {
                Validator.assertNotBlank(measure.getField(), ErrCodeSys.PA_DATA_NOT_EXIST, "度量字段");
                // 输出键会拼进别名，同字段多条件时不再是字段名本身，单独按字符集校验
                String aggKey = measureKey(measure);
                Validator.assertTrue(isSafeMeasureKey(aggKey), ErrCodeSys.SYS_ERR_MSG, "度量列标识非法：" + aggKey);
                if (FuncUtil.equals(measure.getAgg(), AGG_RATIO)) {
                    Validator.assertNotBlank(measure.getNumerator(), ErrCodeSys.PA_DATA_NOT_EXIST, "比率度量分子字段");
                    Validator.assertNotBlank(measure.getDenominator(), ErrCodeSys.PA_DATA_NOT_EXIST, "比率度量分母字段");
                }
                wrapper.getSelectColum().add(new SelectString(String.format("%s as '%s'",
                        buildPivotAgg(pivot, measure),
                        pivot.getValue() + PIVOT_ALIAS_SPLITTER + aggKey), wrapper.getAlias()));
            }
        }
        wrapper.from(from -> buildSubFromWrapper(query, from));
        // 复用 parseSort 对聚合结果追加外层 ORDER BY:
        // 外层 FROM 为子查询, 排序列即子查询输出列名(行维度字段原名或 ${列标识}__${度量字段} 别名),
        // 不能用实体列名反射(字段可能不在实体上), 用直通别名映射并加反引号兼容数字开头的别名
        if (FuncUtil.isNotEmpty(outerSort)) {
            Map<String, String> sortAliasMap = new HashMap<>();
            for (SortVO sort : outerSort) {
                if (FuncUtil.isNotEmpty(sort.getProperty())) {
                    sortAliasMap.put(sort.getProperty(), String.format("`%s`", sort.getProperty()));
                }
            }
            getRepo().parseSort(outerSort, sortAliasMap, wrapper);
        }
        return getRepo().selectJoinMaps(wrapper);
    }

    /**
     * 构建单个透视单元格的条件聚合表达式
     *
     * @param pivot   父表头列（携带列条件）
     * @param measure 度量列（可携带自己的条件，与列条件按 AND 叠加）
     * @return 聚合 SQL 片段
     */
    default String buildPivotAgg(MetricCondition pivot, PivotMeasure measure) {
        String agg = FuncUtil.isEmpty(measure.getAgg()) ? AGG_SUM : measure.getAgg();
        AdvancedQuery condition = mergeCondition(pivot.getCondition(), measure.getCondition());
        if (FuncUtil.equals(agg, AGG_COUNT)) {
            return String.format("count(%s)", parseStatisticSelect(condition, measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_AVG)) {
            // ELSE NULL 保证均值分母只计算命中行
            return String.format("avg(%s)", parseStatisticSelect(condition, measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_MIN)) {
            // ELSE NULL: min 自动忽略 NULL, 未命中行不影响最小值
            return String.format("min(%s)", parseStatisticSelect(condition, measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_MAX)) {
            // ELSE NULL: max 自动忽略 NULL, 未命中行不影响最大值
            return String.format("max(%s)", parseStatisticSelect(condition, measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_COUNT_DISTINCT)) {
            // ELSE NULL: DISTINCT 自动忽略 NULL, 只统计命中行的去重值
            return String.format("count(distinct %s)", parseStatisticSelect(condition, measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_RATIO)) {
            // 比率度量: 分子/分母各自按同一透视条件、按各自配置的聚合(缺省 sum)聚合后再相除(条件只包一边会把另一腿算进错误分母),
            // 除零或空组回落 NULL, 前端按空单元格渲染; 分母腿配 max 服务组内恒定的目标值口径(达成率=sum(实际)/max(目标))
            return buildRatioExpr(measure,
                    parseStatisticSelect(condition, measure.getNumerator(), true),
                    parseStatisticSelect(condition, measure.getDenominator(), true));
        } else {
            return String.format("sum(%s)", parseStatisticSelect(condition, measure.getField(), false));
        }
    }

    /**
     * 度量聚合方式 → SQL 聚合表达式（未知值回落 sum；kernel 实体模式与 forge 数据集模式两个生成点共用）
     */
    static String buildAggExpr(String agg, String innerExpr) {
        if (FuncUtil.equals(agg, AGG_COUNT)) {
            return "count(" + innerExpr + ")";
        } else if (FuncUtil.equals(agg, AGG_AVG)) {
            return "avg(" + innerExpr + ")";
        } else if (FuncUtil.equals(agg, AGG_MIN)) {
            return "min(" + innerExpr + ")";
        } else if (FuncUtil.equals(agg, AGG_MAX)) {
            return "max(" + innerExpr + ")";
        } else if (FuncUtil.equals(agg, AGG_COUNT_DISTINCT)) {
            return "count(distinct " + innerExpr + ")";
        }
        return "sum(" + innerExpr + ")";
    }

    /**
     * ratio 腿聚合解析：不配=sum；白名单外拒收——拼 SQL 层静默回落会把错数伪装成正常数字
     */
    static String resolveRatioLegAgg(String legAgg, String legLabel) {
        if (FuncUtil.isEmpty(legAgg)) {
            return AGG_SUM;
        }
        Validator.assertTrue(Arrays.asList(AGG_SUM, AGG_COUNT, AGG_AVG, AGG_MIN, AGG_MAX, AGG_COUNT_DISTINCT).contains(legAgg),
                ErrCodeSys.SYS_ERR_MSG, "比率度量" + legLabel + "聚合方式非法：" + legAgg);
        return legAgg;
    }

    /**
     * ratio 除法壳：两腿各按配置聚合（缺省 sum）后相除，除零回落 NULL。
     * 分母腿配 max 用于组内恒定值口径（如目标列在明细粒度重复出现，sum 分母会乘以组内行数）。
     */
    static String buildRatioExpr(PivotMeasure measure, String numeratorExpr, String denominatorExpr) {
        return buildAggExpr(resolveRatioLegAgg(measure.getNumeratorAgg(), "分子"), numeratorExpr)
                + " / nullif(" + buildAggExpr(resolveRatioLegAgg(measure.getDenominatorAgg(), "分母"), denominatorExpr) + ", 0)";
    }
}
