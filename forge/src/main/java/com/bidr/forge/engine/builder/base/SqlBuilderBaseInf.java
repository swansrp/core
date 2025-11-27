package com.bidr.forge.engine.builder.base;

import java.util.Map;

/**
 * SQL构建器基础接口
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface SqlBuilderBaseInf {

    /**
     * 构建别名映射（用于字段名到列名的转换）
     * 子类可以重写以提供自定义映射逻辑
     *
     * @return 字段别名映射
     */
    default Map<String, String> getDefaultAliasMap() {
        return null;
    }
}
