package com.bidr.forge.engine.builder.base;

import java.util.Map;

/**
 * SQL构建器更新接口
 * 定义更新SQL构建方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface SqlBuilderUpdateInf extends SqlBuilderBaseInf {

    /**
     * 构建UPDATE SQL
     *
     * @param data       数据对象
     * @param aliasMap   字段别名映射
     * @param parameters 参数Map（输出参数）
     * @return UPDATE SQL
     */
    String buildUpdate(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters);
}
