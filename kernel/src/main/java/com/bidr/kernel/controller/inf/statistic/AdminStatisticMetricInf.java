package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.statistic.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.math.BigDecimal;
import java.util.*;

/**
 * Title: AdminStatisticMetricInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:43
 */

public interface AdminStatisticMetricInf<ENTITY, VO> extends AdminStatisticBaseInf<ENTITY, VO>, AdminStatisticParseInf {
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
            wrapper.from(from -> buildGeneralFromWrapper(req, from));
            if (StringUtil.convertSwitch(req.getMajorCondition())) {
                return getConditionMajorStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
            } else {
                return getMetricMajorStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
            }

        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildGeneralFromWrapper(req, from));
            return getStatisticRes(wrapper, req.getMetricColumn());
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
            wrapper.from(from -> buildAdvancedFromWrapper(req, from));
            if (StringUtil.convertSwitch(req.getMajorCondition())) {
                return getConditionMajorStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
            } else {
                return getMetricMajorStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
            }
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildAdvancedFromWrapper(req, from));
            return getStatisticRes(wrapper, req.getMetricColumn());
        }
    }

    /**
     * 自定义指标
     *
     * @param metricColumn        字典分级指标
     * @param metricConditionList 自定义指标条件
     * @param statisticColumn     统计目标
     * @return 自定义指标
     */
    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<Metric> metricColumn,
                                                           List<MetricCondition> metricConditionList,
                                                           String statisticColumn) {
        Validator.assertNotEmpty(metricConditionList, ErrCodeSys.PA_DATA_NOT_EXIST, "自定义指标");
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        for (MetricCondition metricCondition : metricConditionList) {
            if (FuncUtil.isNotEmpty(statisticColumn)) {
                wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as '%s'",
                        parseStatisticSelect(metricCondition.getCondition(), statisticColumn),
                        metricCondition.getLabel()), wrapper.getAlias()));
            } else {
                wrapper.getSelectColum().add(new SelectString(String.format("count(%s) as '%s'",
                        parseStatisticSelect(metricCondition.getCondition(), statisticColumn),
                        metricCondition.getLabel()), wrapper.getAlias()));
            }
        }
        if (FuncUtil.isNotEmpty(metricColumn)) {
            buildMetricWrapperByMetricColumn(metricColumn, wrapper);
        }
        return wrapper;
    }

    /**
     * 字典分级指标
     *
     * @param metricColumn    字典分级指标
     * @param statisticColumn 统计目标
     * @param sort            排序
     * @return 字典分级
     */
    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<Metric> metricColumn, String statisticColumn,
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
        buildMetricWrapperByMetricColumn(metricColumn, wrapper);
        return wrapper;
    }
/*
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
*/

    /**
     * 构建多指标wrapper
     *
     * @param metricColumn 指标
     * @param wrapper      wrapper
     */
    default void buildMetricWrapperByMetricColumn(List<Metric> metricColumn, MPJLambdaWrapper<ENTITY> wrapper) {
        for (Metric metric : metricColumn) {
            String column = metric.getColumn();
            if (FuncUtil.isNotEmpty(column)) {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("IFNULL(%s, '%s') as %s", column, StatisticRes.NULL, column),
                        wrapper.getAlias()));
                wrapper.groupBy(column);
                wrapper.orderByAsc(column);
            }
        }
    }

    /**
     * 解析自定义指标统计结果(自定义条件为主)
     *
     * @param wrapper wrapper
     * @param metrics 分类指标
     * @param sort    排序
     * @return 自定义指标统计结果
     */
    default List<StatisticRes> getConditionMajorStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metrics,
                                                             Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        Metric metric = null;
        if (FuncUtil.isNotEmpty(metrics)) {
            metric = metrics.get(0);
        }
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(maps.get(0))) {
                if (FuncUtil.isNotEmpty(metric)) {
                    for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                        if (!entry.getKey().equals(metric.getColumn())) {
                            resMap.put(entry.getKey(), new StatisticRes(null, entry.getKey(), entry.getKey()));
                        }
                    }
                    for (Map<String, Object> map : maps) {
                        Object metricObj = map.get(metric.getColumn());
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            StatisticRes res = resMap.get(entry.getKey());
                            if (FuncUtil.isNotEmpty(res) && !entry.getKey().equals(metric.getColumn())) {
                                BigDecimal statistic = FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                        entry.getValue().toString()) : BigDecimal.ZERO;
                                if (FuncUtil.isNotEmpty(metricObj)) {
                                    res.getChildren()
                                            .add(new StatisticRes(metric.getColumn(), metricObj.toString(), null,
                                                    statistic));
                                }
                                res.setStatistic(res.getStatistic().add(statistic));
                            }
                        }
                    }
                    for (Map.Entry<String, StatisticRes> entry : resMap.entrySet()) {
                        entry.getValue().setChildren(
                                new ArrayList<>(fillDictMetric(metric, entry.getValue().getChildren()).values()));
                    }
                } else {
                    for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                        resMap.put(entry.getKey(),
                                new StatisticRes(null, entry.getKey(), entry.getKey(), entry.getValue()));
                    }
                }
            }
        }
        return getConditionStatisticRes(resMap, sort);
    }

    /**
     * 解析自定义指标统计结果(分类指标为主)
     *
     * @param wrapper wrapper
     * @param metrics 分类指标
     * @param sort    排序
     * @return 自定义指标统计结果
     */
    default List<StatisticRes> getMetricMajorStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metrics,
                                                          Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        Metric metric = null;
        if (FuncUtil.isNotEmpty(metrics)) {
            metric = metrics.get(0);
        }
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(maps.get(0))) {
                if (FuncUtil.isNotEmpty(metric)) {
                    if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                        for (Map.Entry<String, String> entry : metric.getDictMap().entrySet()) {
                            resMap.put(entry.getKey(),
                                    new StatisticRes(metric.getColumn(), entry.getKey(), entry.getValue(),
                                            BigDecimal.ZERO));
                        }
                    }
                    for (Map<String, Object> map : maps) {
                        String metricObj = map.get(metric.getColumn()).toString();
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            StatisticRes res = resMap.get(metricObj);
                            if (FuncUtil.isEmpty(res)) {
                                res = new StatisticRes(metric.getColumn(), metricObj, null, BigDecimal.ZERO);
                                resMap.put(metricObj, res);
                            }
                            if (!entry.getKey().equals(metric.getColumn())) {
                                BigDecimal statistic = FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                        entry.getValue().toString()) : BigDecimal.ZERO;
                                res.getChildren()
                                        .add(new StatisticRes(null, entry.getKey(), entry.getKey(), statistic));
                                res.setStatistic(res.getStatistic().add(statistic));
                            }
                        }
                    }
                } else {
                    for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                        resMap.put(entry.getKey(),
                                new StatisticRes(null, entry.getKey(), entry.getKey(), entry.getValue()));
                    }
                }

            }
        }
        return getConditionStatisticRes(resMap, sort);
    }

    /**
     * 对自定义条件结果进行排序
     *
     * @param resMap 结果
     * @param sort   排序
     * @return 结果
     */
    default List<StatisticRes> getConditionStatisticRes(Map<String, StatisticRes> resMap, Integer sort) {
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

    /**
     * 解析字典分类指标统计结果
     *
     * @param wrapper       wrapper
     * @param metricColumns 分类指标
     * @return 字典分类指标统计结果
     */
    default List<StatisticRes> getStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metricColumns) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        List<StatisticRes> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(metricColumns)) {
                Map<String, List<StatisticRes>> data = new LinkedHashMap<>();
                fillMetricMap(maps, metricColumns.get(0), null, data);
                for (int i = 1; i < metricColumns.size(); i++) {
                    for (StatisticRes statisticRes : data.get(metricColumns.get(i - 1).getColumn())) {
                        ArrayList<StatisticRes> children = new ArrayList<>(
                                fillMetricMap(maps, metricColumns.get(i), statisticRes, data));
                        statisticRes.setChildren(children);
                    }
                }
                resList = data.get(metricColumns.get(0).getColumn());
            }
        }
        return resList;
    }

    /**
     * 数据解析
     *
     * @param maps      数据
     * @param metric    指标
     * @param parentRes 父指标数据
     * @param data      各指标map
     * @return 指标列表
     */
    default List<StatisticRes> fillMetricMap(List<Map<String, Object>> maps, Metric metric, StatisticRes parentRes,
                                             Map<String, List<StatisticRes>> data) {
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        if (FuncUtil.isNotEmpty(metric.getColumn())) {
            for (Map<String, Object> map : maps) {
                if (FuncUtil.isNotEmpty(parentRes)) {
                    if (!FuncUtil.equals(map.get(parentRes.getMetricColumn()), parentRes.getMetric())) {
                        continue;
                    }
                }
                String metricStr = map.get(metric.getColumn()).toString();
                Object statisticObj = map.get(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                BigDecimal statistic = FuncUtil.isNotEmpty(statisticObj) ? new BigDecimal(
                        statisticObj.toString()) : BigDecimal.ZERO;
                StatisticRes res;
                if (resMap.containsKey(metricStr)) {
                    res = resMap.get(metricStr);
                    res.setStatistic(res.getStatistic().add(statistic));
                } else {
                    res = new StatisticRes(metric.getColumn(), metricStr, statistic);
                }
                if (StatisticRes.NULL.equals(res.getMetric())) {
                    res.setMetricLabel(StatisticRes.UNKNOWN);
                }
                resMap.put(res.getMetric(), res);
            }
            if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                resMap = fillDictMetric(metric, new ArrayList<>(resMap.values()));
            }
        }
        data.put(metric.getColumn(), new ArrayList<>(resMap.values()));
        return new ArrayList<>(resMap.values());
    }

    /**
     * 根据传入所有指标类型 补充有没有数据的统计值
     *
     * @param metric  指标
     * @param resList 结果
     * @return 结果
     */
    default Map<String, StatisticRes> fillDictMetric(Metric metric, List<StatisticRes> resList) {
        Map<String, String> dictMap = metric.getDictMap();
        Map<String, StatisticRes> map = new LinkedHashMap<>(dictMap.size());
        for (Map.Entry<String, String> entry : dictMap.entrySet()) {
            map.put(entry.getKey(),
                    new StatisticRes(metric.getColumn(), entry.getKey(), entry.getValue(), BigDecimal.ZERO));
        }
        for (StatisticRes res : resList) {
            StatisticRes statistic = map.get(res.getMetric());
            if (FuncUtil.isNotEmpty(statistic)) {
                statistic.setStatistic(res.getStatistic());
            } else {
                map.put(res.getMetric(), res);
            }
        }
        return map;
    }
}
