package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: StatisticReqInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/5/7 20:22
 */

public interface StatisticReqInf {

    /**
     * 获取统计列
     *
     * @return 统计列
     */
    List<KeyValueResVO> getStatisticColumn();

    /**
     * 设置统计列
     *
     * @param statisticColumn 统计列
     */
    void setStatisticColumn(List<KeyValueResVO> statisticColumn);

    /**
     * 获取统计自定义指标
     *
     * @return 自定义指标
     */
    List<MetricCondition> getMetricCondition();

    /**
     * 设置统计自定义指标
     *
     * @param statisticColumn 自定义指标
     */
    void setMetricCondition(List<MetricCondition> statisticColumn);

    /**
     * 添加自定义指标列
     *
     * @param keyField   自定义指标列
     * @param valueField 自定义指标名
     * @param <M>        类型
     * @param <N>        类型
     * @param <T>        类型
     * @param <R>        类型
     */
    default <M, N, T, R> void addStatisticColumn(GetFunc<T, R> keyField, GetFunc<M, N> valueField) {
        if (FuncUtil.isEmpty(getStatisticColumn())) {
            setStatisticColumn(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(keyField);
        String label = LambdaUtil.getFieldNameByGetFunc(valueField);
        getStatisticColumn().add(new KeyValueResVO(key, label));
    }

    /**
     * 添加自定义指标列
     *
     * @param field 自定义指标列
     * @param <T>   类型
     * @param <R>   类型
     */
    default <T, R> void addStatisticColumn(GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getStatisticColumn())) {
            setStatisticColumn(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(field);
        getStatisticColumn().add(new KeyValueResVO(key, key));
    }

    /**
     * 添加自定义指标列
     *
     * @param column 指标字段
     * @param field  自定义指标列
     * @param <T>    类型
     * @param <R>    类型
     */
    default <T, R> void addStatisticColumn(String column, GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getStatisticColumn())) {
            setStatisticColumn(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(field);
        getStatisticColumn().add(new KeyValueResVO(column, key));
    }

    /**
     * 添加自定义指标条件
     *
     * @param query 条件
     * @param field 自定义指标名
     * @param <T>   类型
     * @param <R>   类型
     */
    default <T, R> void addMetricCondition(AdvancedQuery query, GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getMetricCondition())) {
            setMetricCondition(new ArrayList<>());
        }
        String fieldName = LambdaUtil.getFieldNameByGetFunc(field);
        getMetricCondition().add(new MetricCondition(fieldName, fieldName, query));
    }
}