package com.bidr.forge.engine.builder.base;

import java.util.Map;

/**
 * SQL构建器插入接口
 * 定义插入SQL构建方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface SqlBuilderInsertInf extends SqlBuilderBaseInf {

    /**
     * 构建INSERT SQL
     *
     * @param data       数据对象
     * @param aliasMap   字段别名映射
     * @param parameters 参数Map（输出参数）
     * @return INSERT SQL
     */
    String buildInsert(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters);
}
