package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;

import java.util.List;
import java.util.Map;

/**
 * Title: EasyExcelHandler
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:43
 */

public interface EasyExcelHandler<T> {
    /**
     * 解析转换实体
     *
     * @param data            excel数据
     * @param context         处理数据上下文
     * @param analysisContext excel上下文
     * @return 实体
     */
    T parse(Map<Integer, String> data, Map<String, Object> context, AnalysisContext analysisContext);

    /**
     * 存储实体列表
     *
     * @param entityList 实体列表
     * @param context    处理数据上下文
     */
    void save(List<T> entityList, Map<String, Object> context);

    /**
     * 校验实体是否进行缓存
     *
     * @param entity  本实体
     * @param cache   缓存
     * @param context 处理数据上下文
     * @return 是否进行缓存
     */
    boolean validate(T entity, List<T> cache, Map<String, Object> context);

    /**
     * 是否进行存储
     *
     * @param entity  本实体
     * @param cache   缓存
     * @param context 处理数据上下文
     * @return 是否进行存储
     */
    boolean save(T entity, List<T> cache, Map<String, Object> context);
}
