package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.bidr.platform.constant.upload.UploadProgressStep;

import java.util.List;
import java.util.Map;

/**
 * Title: EasyExcelHandler
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:43
 */

public interface EasyExcelHandler<T, VO> {
    /**
     * 解析转换实体
     *
     * @param data            excel数据
     * @param context         处理数据上下文
     * @param analysisContext excel上下文
     * @return 实体
     */
    T parse(VO data, Map<String, Object> context, AnalysisContext analysisContext);

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

    /**
     * 设置当前进度
     *
     * @param step     阶段
     * @param total    总数
     * @param loaded   已处理
     * @param comments 异常信息
     */
    void setProgress(UploadProgressStep step, Integer total, Integer loaded, String comments);
}
