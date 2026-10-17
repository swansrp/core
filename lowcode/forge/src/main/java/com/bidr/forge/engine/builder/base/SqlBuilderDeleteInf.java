package com.bidr.forge.engine.builder.base;

import java.util.Map;

/**
 * SQL构建器删除接口
 * 定义删除SQL构建方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface SqlBuilderDeleteInf extends SqlBuilderBaseInf {

    /**
     * 构建DELETE SQL
     *
     * @param id         主键ID
     * @param parameters 参数Map（输出参数）
     * @return DELETE SQL
     */
    String buildDelete(Object id, Map<String, Object> parameters);
}
