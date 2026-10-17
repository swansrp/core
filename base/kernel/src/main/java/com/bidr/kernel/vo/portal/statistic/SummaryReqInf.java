package com.bidr.kernel.vo.portal.statistic;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: StatisticReqInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/5/7 20:22
 */

public interface SummaryReqInf {

    /**
     * 获取统计列
     *
     * @return 统计列
     */
    List<String> getColumns();

    /**
     * 设置统计列
     *
     * @param columns 统计列
     */
    void setColumns(List<String> columns);

    /**
     * 添加总计列
     *
     * @param field 自定义指标列
     * @param <T>   类型
     * @param <R>   类型
     */
    default <T, R> void addColumn(GetFunc<T, R> field) {
        if (FuncUtil.isEmpty(getColumns())) {
            setColumns(new ArrayList<>());
        }
        String key = LambdaUtil.getFieldNameByGetFunc(field);
        getColumns().add(key);
    }

    /**
     * 添加总计列
     *
     * @param column 指标字段
     */
    default <T, R> void addColumn(String column) {
        if (FuncUtil.isEmpty(getColumns())) {
            setColumns(new ArrayList<>());
        }
        getColumns().add(column);
    }
}