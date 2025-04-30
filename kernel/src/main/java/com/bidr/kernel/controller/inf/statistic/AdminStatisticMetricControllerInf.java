package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.math.BigDecimal;
import java.util.*;

/**
 * Title: AdminStatisticCountControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:43
 */

public interface AdminStatisticMetricControllerInf<ENTITY, VO> extends AdminStatisticBaseControllerInf<ENTITY, VO> {
    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByGeneralReq(GeneralStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        }
    }

    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByAdvancedReq(AdvancedStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        }
    }

    default String parseStatisticSelect(AdvancedQuery query, String statisticColumn) {
        // todo 解析 case when
        return null;
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<String> metricColumn,
                                                           List<MetricCondition> metricConditionList,
                                                           String statisticColumn) {
        Validator.assertNotEmpty(metricConditionList, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        for (MetricCondition metricCondition : metricConditionList) {
            if (FuncUtil.isNotEmpty(statisticColumn)) {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("sum(%s) as '%s'",
                                parseStatisticSelect(metricCondition.getCondition(), statisticColumn),
                                metricCondition.getLabel()), wrapper.getAlias()));
            } else {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("count(%s) as '%s'",
                                parseStatisticSelect(metricCondition.getCondition(), statisticColumn),
                                metricCondition.getLabel()), wrapper.getAlias()));
            }
        }
        if (FuncUtil.isNotEmpty(metricColumn)) {
            buildMetircWrapperByMetricColumn(metricColumn, wrapper);
        }
        return wrapper;
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<String> metricColumn, String statisticColumn,
                                                           Integer sort) {

        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(statisticColumn)) {
            wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as %s", statisticColumn,
                    LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)), wrapper.getAlias()));
        } else {
            wrapper.getSelectColum().add(new SelectString(
                    String.format("count(1) as %s", LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)),
                    wrapper.getAlias()));
        }
        Validator.assertNotEmpty(metricColumn, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");
        if (FuncUtil.isNotEmpty(sort)) {
            switch (PortalSortDict.of(sort)) {
                case ASC:
                    wrapper.orderByAsc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                    break;
                case DESC:
                    wrapper.orderByDesc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                    break;
                default:
                    break;
            }
        }
        buildMetircWrapperByMetricColumn(metricColumn, wrapper);
        return wrapper;
    }

    default void buildMetircWrapperByMetricColumn(List<String> metricColumn, MPJLambdaWrapper<ENTITY> wrapper) {
        String concatJoinStr = ", ',', ";
        StringBuilder sql = new StringBuilder("CONCAT(");

        for (String column : metricColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                sql.append("IFNULL(").append(column).append(", 'NULL')").append(concatJoinStr);
            }
        }
        String s = sql.substring(0, sql.length() - concatJoinStr.length()) + ")";
        wrapper.getSelectColum().add(new SelectString(
                String.format("%s as %s", s, LambdaUtil.getFieldNameByGetFunc(StatisticRes::getMetric)),
                wrapper.getAlias()));
        for (String column : metricColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                wrapper.groupBy(column);
                wrapper.orderByAsc(column);
            }
        }
    }

    default List<StatisticRes> getStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(maps.get(0))) {
                for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                    if (!entry.getKey().equals(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getMetric))) {
                        resMap.put(entry.getKey(), new StatisticRes(entry.getKey()));
                    }
                }
            }
            for (Map<String, Object> map : maps) {
                Object metric = map.get(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getMetric));
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    StatisticRes res = resMap.get(entry.getKey());
                    if (FuncUtil.isNotEmpty(res)) {
                        BigDecimal statistic = FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                entry.getValue().toString()) : BigDecimal.ZERO;
                        res.getChildren().add(new StatisticRes(metric.toString(), statistic));
                        res.setStatistic(res.getStatistic().add(statistic));
                    }
                }
            }
        }
        List<StatisticRes> resList = new ArrayList<>(resMap.values());
        if (FuncUtil.isNotEmpty(sort)) {
            switch (PortalSortDict.of(sort)) {
                case ASC:
                    resList.sort(Comparator.comparing(StatisticRes::getStatistic));
                    break;
                case DESC:
                    resList.sort((o1, o2) -> o2.getStatistic().compareTo(o1.getStatistic()));
                    break;
                default:
                    break;
            }
        }
        return resList;
    }
}
