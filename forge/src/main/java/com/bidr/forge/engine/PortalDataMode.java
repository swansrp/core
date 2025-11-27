package com.bidr.forge.engine;

/**
 * Portal数据模式枚举
 *
 * @author Sharp
 * @since 2025-11-24
 */
public enum PortalDataMode {
    /**
     * 实体模式（通过MPJ Wrapper访问）
     */
    ENTITY,

    /**
     * 矩阵模式（通过JDBC访问单表）
     */
    MATRIX,

    /**
     * 数据集模式（通过JDBC访问多表联接视图）
     */
    DATASET
}
