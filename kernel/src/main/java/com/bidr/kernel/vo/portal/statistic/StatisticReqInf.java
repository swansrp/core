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

    List<KeyValueResVO> getStatisticColumn();

    void setStatisticColumn(List<KeyValueResVO> statisticColumn);

    List<MetricCondition> getMetricCondition();

    void setMetricCondition(List<MetricCondition> statisticColumn);

    default <M, N, T, R> void addStatisticColumn(GetFunc<T, R> keyField, GetFunc<M, N> valueField) {
        if (FuncUtil.isEmpty(getStatisticColumn())) {
            setStatisticColumn(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(keyField);
        String label = LambdaUtil.getFieldNameByGetFunc(valueField);
        getStatisticColumn().add(new KeyValueResVO(key, label));
    }

    default <T, R> void addStatisticColumn(GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getStatisticColumn())) {
            setStatisticColumn(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(field);
        getStatisticColumn().add(new KeyValueResVO(key, key));
    }

    default <T, R> void addMetricCondition(AdvancedQuery query, GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getMetricCondition())) {
            setMetricCondition(new ArrayList<>());
        }
        String fieldName = LambdaUtil.getFieldNameByGetFunc(field);
        getMetricCondition().add(new MetricCondition(fieldName, fieldName, query));
    }
}