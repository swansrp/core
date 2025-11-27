package com.bidr.forge.engine.builder;

import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matrix模式SQL构建器
 * 支持单表完整CRUD、树结构查询
 *
 * @author Sharp
 * @since 2025-11-24
 */
@Slf4j
public class MatrixSqlBuilder extends BaseSqlBuilder {

    private final SysMatrix matrix;
    private final List<SysMatrixColumn> columns;
    private final Map<String, SysMatrixColumn> columnMap;
    private final List<SysMatrixColumn> primaryKeys;

    public MatrixSqlBuilder(SysMatrix matrix, List<SysMatrixColumn> columns) {
        this.matrix = matrix;
        this.columns = columns;
        this.columnMap = columns.stream()
                .collect(Collectors.toMap(SysMatrixColumn::getColumnName, col -> col));
        this.primaryKeys = columns.stream()
                .filter(col -> CommonConst.YES.equals(col.getIsPrimaryKey()))
                .collect(Collectors.toList());
    }

    @Override
    public String buildSelect(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT ");

        // 构建SELECT列
        sql.append(buildSelectColumns(aliasMap));

        // 构建FROM
        sql.append(" FROM ").append(matrix.getTableName());

        // 构建WHERE/ORDER BY（复用）
        sql.append(buildQueryClauses(req, aliasMap, parameters, true));

        // 构建LIMIT（如果需要分页）
        if (FuncUtil.isNotEmpty(req.getCurrentPage()) && FuncUtil.isNotEmpty(req.getPageSize())) {
            long offset = (req.getCurrentPage() - 1) * req.getPageSize();
            sql.append(" LIMIT ").append(offset).append(", ").append(req.getPageSize());
        }

        return sql.toString();
    }

    @Override
    public String buildCount(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(matrix.getTableName());

        // 构建WHERE（复用，不含ORDER BY）
        sql.append(buildQueryClauses(req, aliasMap, parameters, false));

        return sql.toString();
    }

    /**
     * 构建 SELECT 列
     */
    @Override
    protected String buildSelectColumns(Map<String, String> aliasMap) {
        if (aliasMap.isEmpty()) {
            return "*";
        }

        List<String> selectCols = new ArrayList<>();
        for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            selectCols.add("`" + columnName + "` AS `" + fieldName + "`");
        }

        return selectCols.isEmpty() ? "*" : String.join(", ", selectCols);
    }

    /**
     * 构建 FROM 子句
     */
    @Override
    protected String buildFromClause() {
        return " FROM " + matrix.getTableName();
    }

    /**
     * 构建查询条件子句（WHERE/ORDER BY）
     */
    @Override
    protected String buildQueryClauses(AdvancedQueryReq req, Map<String, String> aliasMap,
                                      Map<String, Object> parameters, boolean includeOrder) {
        StringBuilder clause = new StringBuilder();

        // 构建WHERE
        String whereClause = buildWhere(req, aliasMap, parameters);
        if (FuncUtil.isNotEmpty(whereClause)) {
            clause.append(" WHERE ").append(whereClause);
        }

        // 构建ORDER BY（仅 SELECT 需要）
        if (includeOrder) {
            String orderByClause = buildOrderBy(req, aliasMap);
            if (FuncUtil.isNotEmpty(orderByClause)) {
                clause.append(" ORDER BY ").append(orderByClause);
            }
        }

        return clause.toString();
    }

    @Override
    public String buildInsert(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
        List<String> columnNames = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            // 通过aliasMap反向查找数据库列名
            String columnName = findColumnName(fieldName, aliasMap);
            if (FuncUtil.isNotEmpty(columnName) && columnMap.containsKey(columnName)) {
                SysMatrixColumn column = columnMap.get(columnName);

                // 跳过自增主键（除非是序列主键）
                if (CommonConst.YES.equals(column.getIsPrimaryKey())
                        && isNumericType(column.getColumnType())
                        && FuncUtil.isEmpty(column.getSequence())) {
                    continue;
                }

                String paramKey = "param_" + columnName;
                columnNames.add("`" + columnName + "`");
                paramNames.add(":" + paramKey);
                parameters.put(paramKey, value);
            }
        }

        if (columnNames.isEmpty()) {
            throw new IllegalArgumentException("没有可插入的字段");
        }

        return "INSERT INTO " + matrix.getTableName()
                + " (" + String.join(", ", columnNames) + ")"
                + " VALUES (" + String.join(", ", paramNames) + ")";
    }

    @Override
    public String buildUpdate(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
        List<String> setClauses = new ArrayList<>();
        Map<String, Object> pkValues = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            String columnName = findColumnName(fieldName, aliasMap);
            if (FuncUtil.isNotEmpty(columnName) && columnMap.containsKey(columnName)) {
                SysMatrixColumn column = columnMap.get(columnName);

                if (CommonConst.YES.equals(column.getIsPrimaryKey())) {
                    // 主键用于WHERE条件
                    pkValues.put(columnName, value);
                } else {
                    // 非主键用于SET
                    String paramKey = "param_" + columnName;
                    setClauses.add("`" + columnName + "` = :" + paramKey);
                    parameters.put(paramKey, value);
                }
            }
        }

        if (setClauses.isEmpty()) {
            throw new IllegalArgumentException("没有可更新的字段");
        }

        if (pkValues.isEmpty()) {
            throw new IllegalArgumentException("缺少主键值");
        }

        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(matrix.getTableName())
                .append(" SET ")
                .append(String.join(", ", setClauses))
                .append(" WHERE ");

        // 构建WHERE条件（主键）
        List<String> pkConditions = new ArrayList<>();
        for (Map.Entry<String, Object> pkEntry : pkValues.entrySet()) {
            String pkParamKey = "pk_" + pkEntry.getKey();
            pkConditions.add("`" + pkEntry.getKey() + "` = :" + pkParamKey);
            parameters.put(pkParamKey, pkEntry.getValue());
        }
        sql.append(String.join(" AND ", pkConditions));

        return sql.toString();
    }

    @Override
    public String buildDelete(Object id, Map<String, Object> parameters) {
        if (primaryKeys.isEmpty()) {
            throw new IllegalArgumentException("未定义主键，无法执行DELETE");
        }

        if (primaryKeys.size() == 1) {
            // 单主键
            SysMatrixColumn pkColumn = primaryKeys.get(0);
            String paramKey = "pk_" + pkColumn.getColumnName();
            parameters.put(paramKey, id);
            return "DELETE FROM " + matrix.getTableName()
                    + " WHERE `" + pkColumn.getColumnName() + "` = :" + paramKey;
        } else {
            // 联合主键（需要传入Map）
            if (!(id instanceof Map)) {
                throw new IllegalArgumentException("联合主键需要传入Map类型的ID");
            }
            Map<String, Object> pkMap = (Map<String, Object>) id;
            List<String> conditions = new ArrayList<>();
            for (SysMatrixColumn pkColumn : primaryKeys) {
                String pkName = pkColumn.getColumnName();
                String paramKey = "pk_" + pkName;
                parameters.put(paramKey, pkMap.get(pkName));
                conditions.add("`" + pkName + "` = :" + paramKey);
            }
            return "DELETE FROM " + matrix.getTableName()
                    + " WHERE " + String.join(" AND ", conditions);
        }
    }

    /**
     * 构建WHERE子句
     */
    private String buildWhere(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        if (FuncUtil.isEmpty(req.getCondition())) {
            return "";
        }

        return buildCondition(req.getCondition(), aliasMap, parameters);
    }

    /**
     * 格式化列名 - Matrix模式使用反引号
     */
    @Override
    protected String formatColumnName(String columnName) {
        return "`" + columnName + "`";
    }

    /**
     * 构建ORDER BY子句
     */
    private String buildOrderBy(AdvancedQueryReq req, Map<String, String> aliasMap) {
        // 优先使用请求中的排序
        if (FuncUtil.isNotEmpty(req.getSortList())) {
            return req.getSortList().stream()
                    .map(sort -> {
                        String field = sort.getProperty();
                        String columnName = aliasMap.getOrDefault(field, field);
                        String direction = sort.getType() != null && sort.getType() == 2 ? "DESC" : "ASC";
                        return "`" + columnName + "` " + direction;
                    })
                    .collect(Collectors.joining(", "));
        }

        // 查找order字段
        SysMatrixColumn orderColumn = columns.stream()
                .filter(col -> CommonConst.YES.equals(col.getIsOrderField()))
                .findFirst()
                .orElse(null);

        if (orderColumn != null) {
            return "`" + orderColumn.getColumnName() + "` ASC";
        }

        return "";
    }

    /**
     * 根据VO字段名查找数据库列名
     */
    private String findColumnName(String fieldName, Map<String, String> aliasMap) {
        // 直接从aliasMap查找
        String columnName = aliasMap.get(fieldName);
        if (FuncUtil.isNotEmpty(columnName)) {
            return columnName;
        }

        // 兜底：假设字段名就是列名
        return fieldName;
    }

    /**
     * 判断是否为数字类型
     */
    private boolean isNumericType(String columnType) {
        if (FuncUtil.isEmpty(columnType)) {
            return false;
        }
        String type = columnType.toUpperCase();
        return type.contains("INT") || type.contains("BIGINT") || type.contains("LONG");
    }

    /**
     * 获取主键列
     */
    public List<SysMatrixColumn> getPrimaryKeys() {
        return primaryKeys;
    }
}
