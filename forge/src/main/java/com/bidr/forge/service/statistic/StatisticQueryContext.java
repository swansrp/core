package com.bidr.forge.service.statistic;

import com.bidr.forge.engine.builder.BaseSqlBuilder;

/**
 * 统计SQL上下文抽象：屏蔽 Matrix/Dataset 在 FROM 片段、列引用格式、条件构建器上的差异。
 *
 * @author ZhangFeihao
 * @since 2025/12/25 15:45
 */
public interface StatisticQueryContext {

    /**
     * FROM 片段（不含 "FROM" 前缀），例如：
     * Matrix: tableName
     * Dataset: tableSql [AS alias] JOIN ...
     */
    String getFromSql();

    /**
     * 构建 WHERE / HAVING 条件所用的 Builder。
     */
    BaseSqlBuilder getConditionBuilder();

    /**
     * 按当前模式格式化“可用于表达式里的列引用”。
     * Matrix: `col`
     * Dataset: colAlias / 表别名.列 等（由 aliasMap 提供），不加反引号
     */
    String formatColumnExpression(String dbColumnOrAlias);
}

