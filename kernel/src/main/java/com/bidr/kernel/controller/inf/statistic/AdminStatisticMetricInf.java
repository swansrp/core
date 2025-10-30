package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.Query;
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
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Validator.assertNotEmpty(req.getStatisticColumn(), ErrCodeSys.PA_DATA_NOT_EXIST, "统计数据");
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildSubFromWrapper(query, from));
            if (StringUtil.convertSwitch(req.getMajorCondition())) {
                return getConditionMajorStatisticRes(wrapper, req.getMetricColumn(), req.getMetricCondition(),
                        req.getStatisticColumn(), req.getSort());
            } else {
                return getMetricMajorStatisticRes(wrapper, req.getMetricColumn(), req.getMetricCondition(),
                        req.getStatisticColumn(), req.getSort());
            }
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildSubFromWrapper(query, from));
            return getStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
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
                                                           List<KeyValueResVO> statisticColumn) {
        Validator.assertNotEmpty(metricConditionList, ErrCodeSys.PA_DATA_NOT_EXIST, "自定义指标");
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        for (MetricCondition metricCondition : metricConditionList) {
            for (KeyValueResVO statistic : statisticColumn) {
                if (FuncUtil.isNotEmpty(statistic.getValue())) {
                    wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as '%s'",
                            parseStatisticSelect(metricCondition.getCondition(), statistic.getValue()),
                            StringUtil.join(metricCondition.getLabel(), statistic.getLabel())), wrapper.getAlias()));
                } else {
                    wrapper.getSelectColum().add(new SelectString(String.format("count(%s) as '%s'",
                            parseStatisticSelect(metricCondition.getCondition(), StringUtil.EMPTY),
                            StringUtil.join(metricCondition.getLabel(), statistic.getLabel())), wrapper.getAlias()));
                }
            }
        }
        if (FuncUtil.isNotEmpty(metricColumn)) {
            buildMetricWrapperByMetricColumn(metricColumn, wrapper);
        }
        return wrapper;
    }

    /**
     * 解析自定义指标统计结果(自定义条件为主)
     *
     * @param wrapper         wrapper
     * @param metrics         分类指标
     * @param metricCondition 自定义条件
     * @param statisticColumn 统计字段
     * @param sort            排序
     * @return 自定义指标统计结果
     */
    default List<StatisticRes> getConditionMajorStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metrics,
                                                             List<MetricCondition> metricCondition,
                                                             List<KeyValueResVO> statisticColumn, Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        Metric metric = null;
        if (FuncUtil.isNotEmpty(metrics)) {
            metric = metrics.get(0);
        }
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(maps.get(0))) {
                if (FuncUtil.isNotEmpty(metric)) {
                    // 根据第一个组数据初始化各个统计指标数组初始值
                    for (MetricCondition condition : metricCondition) {
                        for (KeyValueResVO column : statisticColumn) {
                            String key = StringUtil.join(condition.getLabel(), column.getLabel());
                            if (FuncUtil.isNotEmpty(maps.get(0).get(key))) {
                                resMap.put(key, new StatisticRes(null, key, key));
                            }
                        }
                    }
                    // 解析数据
                    for (Map<String, Object> map : maps) {
                        Object metricObj = map.get(metric.getColumn());
                        // 剔除groupBy出来的 不在字典中的条目
                        if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                            if (!metric.getDictMap().containsKey(metricObj)) {
                                continue;
                            }
                        }
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            StatisticRes res = resMap.get(entry.getKey());
                            if (FuncUtil.isNotEmpty(res) && !entry.getKey().equals(metric.getColumn())) {
                                BigDecimal statistic = FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                        entry.getValue().toString()) : BigDecimal.ZERO;
                                // 下属分类指标数据
                                if (FuncUtil.isNotEmpty(metricObj)) {
                                    res.getChildren()
                                            .add(new StatisticRes(metric.getColumn(), metricObj.toString(), null,
                                                    statistic));
                                }
                                // 分类指标数据合计
                                res.setStatistic(res.getStatistic().add(statistic));
                            }
                        }
                    }
                    // 填充分类指标字典
                    if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                        for (Map.Entry<String, StatisticRes> entry : resMap.entrySet()) {
                            entry.getValue().setChildren(fillDictMetric(metric, entry.getValue().getChildren(), null));
                        }
                    }
                } else {
                    // 无分类指标
                    for (MetricCondition condition : metricCondition) {
                        for (KeyValueResVO statistic : statisticColumn) {
                            String value = StringUtil.join(condition.getLabel(), statistic.getLabel());
                            resMap.put(value, new StatisticRes(null, value, statistic.getLabel(), BigDecimal.ZERO));
                        }
                    }
                    for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                        StatisticRes statisticRes = resMap.get(entry.getKey());
                        statisticRes.setStatistic(FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                entry.getValue().toString()) : BigDecimal.ZERO);
                    }
                }
            }
        }
        // 多统计指标按照自定义指标条件分组并排序
        return groupByCondition(metricCondition, resMap, sort);
    }

    /**
     * 解析自定义指标统计结果(分类指标为主)
     *
     * @param wrapper         wrapper
     * @param metrics         分类指标
     * @param metricCondition 自定义条件
     * @param statisticColumn 统计字段
     * @param sort            排序
     * @return 自定义指标统计结果
     */
    default List<StatisticRes> getMetricMajorStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metrics,
                                                          List<MetricCondition> metricCondition,
                                                          List<KeyValueResVO> statisticColumn, Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        Metric metric = null;
        if (FuncUtil.isNotEmpty(metrics)) {
            metric = metrics.get(0);
        }
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(maps.get(0))) {
                if (FuncUtil.isNotEmpty(metric)) {
                    // 填充分类指标字典项
                    if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                        for (Map.Entry<String, String> entry : metric.getDictMap().entrySet()) {
                            resMap.put(entry.getKey(),
                                    new StatisticRes(metric.getColumn(), entry.getKey(), entry.getValue(),
                                            BigDecimal.ZERO));
                        }
                    }
                    // 解析数据
                    for (Map<String, Object> map : maps) {
                        String metricObj = map.get(metric.getColumn()).toString();
                        StatisticRes res = resMap.get(metricObj);
                        // 按分类指标初始化结果
                        if (FuncUtil.isEmpty(res)) {
                            // 剔除不属于字典的数据
                            if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                                continue;
                            }
                            res = new StatisticRes(metric.getColumn(), metricObj, null, BigDecimal.ZERO);
                            resMap.put(metricObj, res);
                        }
                        // 按条件顺序填入分类指标下属数据
                        for (MetricCondition condition : metricCondition) {
                            for (KeyValueResVO column : statisticColumn) {
                                String key = StringUtil.join(condition.getLabel(), column.getLabel());
                                BigDecimal statistic = FuncUtil.isNotEmpty(map.get(key)) ? new BigDecimal(
                                        map.get(key).toString()) : BigDecimal.ZERO;
                                res.getChildren().add(new StatisticRes(null, key, key, statistic));
                                res.setStatistic(res.getStatistic().add(statistic));
                            }
                        }
                    }
                    // 填充sql没有的分类
                    for (Map.Entry<String, StatisticRes> entry : resMap.entrySet()) {
                        if (FuncUtil.isEmpty(entry.getValue().getChildren())) {
                            for (MetricCondition condition : metricCondition) {
                                for (KeyValueResVO statistic : statisticColumn) {
                                    String value = StringUtil.join(condition.getLabel(), statistic.getLabel());
                                    entry.getValue().getChildren()
                                            .add(new StatisticRes(null, value, value, BigDecimal.ZERO));
                                }
                            }
                        }
                    }
                } else {
                    for (MetricCondition condition : metricCondition) {
                        for (KeyValueResVO statistic : statisticColumn) {
                            String value = StringUtil.join(condition.getLabel(), statistic.getLabel());
                            resMap.put(value, new StatisticRes(null, value, statistic.getLabel(), BigDecimal.ZERO));
                        }
                    }
                    for (Map.Entry<String, Object> entry : maps.get(0).entrySet()) {
                        StatisticRes statisticRes = resMap.get(entry.getKey());
                        statisticRes.setStatistic(FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(
                                entry.getValue().toString()) : BigDecimal.ZERO);
                    }
                }
            }
        }
        // 多统计指标按照自定义指标条件分组并排序
        if (FuncUtil.isNotEmpty(metric)) {
            return groupChildrenByCondition(statisticColumn, resMap, sort);
        } else {
            return groupByCondition(metricCondition, resMap, sort);
        }
    }

    /**
     * 字典分级指标
     *
     * @param metricColumn    字典分级指标
     * @param statisticColumn 统计目标
     * @param sort            排序
     * @return 字典分级
     */
    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<Metric> metricColumn,
                                                           List<KeyValueResVO> statisticColumn, Integer sort) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        for (KeyValueResVO statistic : statisticColumn) {
            if (FuncUtil.isNotEmpty(statistic.getValue())) {
                wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as %s", statistic.getValue(),
                        LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)), wrapper.getAlias()));
            } else {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("count(1) as %s", LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)),
                        wrapper.getAlias()));
            }
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

    /**
     * 解析字典分类指标统计结果
     *
     * @param wrapper       wrapper
     * @param metricColumns 分类指标
     * @param sort          排序
     * @return 字典分类指标统计结果
     */
    default List<StatisticRes> getStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, List<Metric> metricColumns,
                                               Integer sort) {
        List<Map<String, Object>> maps = getRepo().selectJoinMaps(wrapper);
        List<StatisticRes> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(maps)) {
            if (FuncUtil.isNotEmpty(metricColumns)) {
                Map<String, List<StatisticRes>> data = new LinkedHashMap<>();
                fillMetricMap(maps, metricColumns.get(0), null, data, sort);
                for (int i = 1; i < metricColumns.size(); i++) {
                    for (StatisticRes statisticRes : data.get(metricColumns.get(i - 1).getColumn())) {
                        ArrayList<StatisticRes> children = new ArrayList<>(
                                fillMetricMap(maps, metricColumns.get(i), statisticRes, data, sort));
                        statisticRes.setChildren(children);
                    }
                }
                resList = data.get(metricColumns.get(0).getColumn());
            }
        }
        return resList;
    }

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
            }
        }
    }

    /**
     * 根据传入所有指标类型 补充有没有数据的统计值
     *
     * @param metric  指标
     * @param resList 结果
     * @param sort    排序
     * @return 结果
     */
    default List<StatisticRes> fillDictMetric(Metric metric, Collection<StatisticRes> resList, Integer sort) {
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
            }
        }
        return getConditionStatisticRes(map.values(), sort);
    }

    /**
     * 对自定义条件结果进行排序
     *
     * @param dataList 结果
     * @param sort     排序
     * @return 结果
     */
    default List<StatisticRes> getConditionStatisticRes(Collection<StatisticRes> dataList, Integer sort) {
        List<StatisticRes> resList = new ArrayList<>(dataList);
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
     * 根据自定义指标 对多指标统计进行分组
     *
     * @param metricCondition 自定义指标
     * @param resMap          数据
     * @param sort            排序
     * @return 分组后数据
     */
    default List<StatisticRes> groupByCondition(List<MetricCondition> metricCondition, Map<String, StatisticRes> resMap,
                                                Integer sort) {
        Map<String, StatisticRes> resultMap = new LinkedHashMap<>();
        for (MetricCondition condition : metricCondition) {
            resultMap.put(condition.getLabel(),
                    new StatisticRes(null, condition.getValue(), condition.getLabel(), null));
        }
        for (StatisticRes re : resMap.values()) {
            String[] metricArray = re.getMetric().split(StringUtil.SPLITTER);
            BigDecimal summary = resultMap.get(metricArray[0]).getStatistic().add(re.getStatistic());
            resultMap.get(metricArray[0]).setStatistic(summary);
            resultMap.get(metricArray[0]).getChildren().add(re);
            re.setMetricLabel(re.getMetric());
            re.setMetric(metricArray[1]);
        }
        return getConditionStatisticRes(resultMap.values(), sort);
    }

    /**
     * 根据自定义指标 对多指标统计子项进行分组并排序
     *
     * @param statisticColumn 指标列
     * @param res             数据
     * @param sort            排序
     * @return 分组并排序后数据
     */
    default List<StatisticRes> groupChildrenByCondition(List<KeyValueResVO> statisticColumn,
                                                        Map<String, StatisticRes> res, Integer sort) {
        List<StatisticRes> resultList = new ArrayList<>();
        for (StatisticRes re : res.values()) {
            resultList.add(re);
            Map<String, StatisticRes> resultMap = new LinkedHashMap<>();
            for (KeyValueResVO column : statisticColumn) {
                resultMap.put(column.getLabel(),
                        new StatisticRes(re.getMetric(), column.getValue(), column.getLabel(), re.getStatistic()));
            }
            BigDecimal summary = BigDecimal.ZERO;
            for (StatisticRes child : re.getChildren()) {
                String[] metricArray = child.getMetric().split(StringUtil.SPLITTER);
                summary = child.getStatistic().add(summary);
                resultMap.get(metricArray[1]).getChildren().add(child);
            }
            re.setStatistic(summary);
            re.setChildren(new ArrayList<>(resultMap.values()));
        }
        return getConditionStatisticRes(resultList, sort);
    }

    /**
     * 数据解析
     *
     * @param maps      数据
     * @param metric    指标
     * @param parentRes 父指标数据
     * @param data      各指标map
     * @param sort      排序
     * @return 指标列表
     */
    default List<StatisticRes> fillMetricMap(List<Map<String, Object>> maps, Metric metric, StatisticRes parentRes,
                                             Map<String, List<StatisticRes>> data, Integer sort) {
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();
        List<StatisticRes> resList = new ArrayList<>();
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
                resList = fillDictMetric(metric, resMap.values(), sort);
            } else {
                resList = new ArrayList<>(resMap.values());
            }
            data.put(metric.getColumn(), resList);
        }
        return resList;
    }

    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByAdvancedReq(AdvancedStatisticReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Validator.assertNotEmpty(req.getStatisticColumn(), ErrCodeSys.PA_DATA_NOT_EXIST, "统计数据");
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildSubFromWrapper(query, from));
            if (StringUtil.convertSwitch(req.getMajorCondition()) || FuncUtil.isEmpty(req.getMajorCondition())) {
                return getConditionMajorStatisticRes(wrapper, req.getMetricColumn(), req.getMetricCondition(),
                        req.getStatisticColumn(), req.getSort());
            } else {
                return getMetricMajorStatisticRes(wrapper, req.getMetricColumn(), req.getMetricCondition(),
                        req.getStatisticColumn(), req.getSort());
            }
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildSubFromWrapper(query, from));
            return getStatisticRes(wrapper, req.getMetricColumn(), req.getSort());
        }
    }
}
