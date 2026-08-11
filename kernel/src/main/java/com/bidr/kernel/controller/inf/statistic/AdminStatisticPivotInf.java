package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.Query;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.MetricCondition;
import com.bidr.kernel.vo.portal.statistic.PivotMeasure;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    String PIVOT_ALIAS_SPLITTER = "__";

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
                wrapper.getSelectColum().add(new SelectString(String.format("%s as '%s'",
                        buildPivotAgg(pivot, measure),
                        pivot.getValue() + PIVOT_ALIAS_SPLITTER + measure.getField()), wrapper.getAlias()));
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
     * @param measure 度量列
     * @return 聚合 SQL 片段
     */
    default String buildPivotAgg(MetricCondition pivot, PivotMeasure measure) {
        String agg = FuncUtil.isEmpty(measure.getAgg()) ? AGG_SUM : measure.getAgg();
        if (FuncUtil.equals(agg, AGG_COUNT)) {
            return String.format("count(%s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_AVG)) {
            // ELSE NULL 保证均值分母只计算命中行
            return String.format("avg(%s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_MIN)) {
            // ELSE NULL: min 自动忽略 NULL, 未命中行不影响最小值
            return String.format("min(%s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_MAX)) {
            // ELSE NULL: max 自动忽略 NULL, 未命中行不影响最大值
            return String.format("max(%s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), true));
        } else if (FuncUtil.equals(agg, AGG_COUNT_DISTINCT)) {
            // ELSE NULL: DISTINCT 自动忽略 NULL, 只统计命中行的去重值
            return String.format("count(distinct %s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), true));
        } else {
            return String.format("sum(%s)", parseStatisticSelect(pivot.getCondition(), measure.getField(), false));
        }
    }
}
