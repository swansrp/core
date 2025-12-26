package com.bidr.forge.service.statistic;

import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.engine.builder.BaseSqlBuilder;
import com.bidr.forge.engine.builder.MatrixSqlBuilder;

/**
 * Matrix 统计上下文适配器。
 *
 * @author ZhangFeihao
 * @since 2025/12/25 15:45
 */
public class MatrixStatisticQueryContext implements StatisticQueryContext {

    private final MatrixColumns matrixColumns;
    private final MatrixSqlBuilder builder;

    public MatrixStatisticQueryContext(MatrixColumns matrixColumns) {
        this.matrixColumns = matrixColumns;
        this.builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
    }

    @Override
    public String getFromSql() {
        return matrixColumns.getTableName();
    }

    @Override
    public BaseSqlBuilder getConditionBuilder() {
        return builder;
    }

    @Override
    public String formatColumnExpression(String dbColumnOrAlias) {
        return "`" + dbColumnOrAlias + "`";
    }
}

