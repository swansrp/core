package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: GeneralStatisticReq
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/21 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GeneralStatisticReq extends QueryConditionReq {
    private List<Metric> metricColumn;
    private List<MetricCondition> metricCondition;
    private String majorCondition;
    private List<KeyValueResVO> statisticColumn;
    private Integer sort;

    public <M, N, T, R> void addStatisticColumn(GetFunc<T, R> keyField, GetFunc<M, N> valueField) {
        if (FuncUtil.isEmpty(statisticColumn)) {
            this.statisticColumn = new ArrayList<>();
        }
        String key = LambdaUtil.getFieldNameByGetFunc(keyField);
        String label = LambdaUtil.getFieldNameByGetFunc(valueField);
        statisticColumn.add(new KeyValueResVO(key, key));
    }

    public <T, R> void addStatisticColumn(GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(statisticColumn)) {
            this.statisticColumn = new ArrayList<>();
        }
        String key = LambdaUtil.getFieldNameByGetFunc(field);
        statisticColumn.add(new KeyValueResVO(key, key));
    }
}
