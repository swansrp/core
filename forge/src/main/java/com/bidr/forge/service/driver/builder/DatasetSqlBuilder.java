package com.bidr.forge.service.driver.builder;

import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dataset模式SQL构建器
 * 支持多表联接、复杂表达式（如CASE WHEN）、聚合查询
 *
 * @author Sharp
 * @since 2025-11-24
 */
@Slf4j
public class DatasetSqlBuilder extends BaseSqlBuilder {

    private final Long datasetId;
    private final List<SysDatasetTable> datasets;
    private final List<SysDatasetColumn> columns;

    public DatasetSqlBuilder(Long datasetId, List<SysDatasetTable> datasets, List<SysDatasetColumn> columns) {
        this.datasetId = datasetId;
        this.datasets = datasets;
        this.columns = columns;
    }

    @Override
    public String buildSelect(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT ");

        // 构建SELECT列
        sql.append(buildSelectColumns(aliasMap));

        // 构建FROM和JOIN
        sql.append(" FROM ").append(buildFromAndJoin());

        // 构建WHERE/GROUP BY/HAVING/ORDER BY（复用）
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM (SELECT 1 ");

        // 构建FROM和JOIN
        sql.append(" FROM ").append(buildFromAndJoin());

        // 构建WHERE/GROUP BY/HAVING（复用，不含ORDER BY）
        sql.append(buildQueryClauses(req, aliasMap, parameters, false));

        sql.append(") AS count_table");

        return sql.toString();
    }

    /**
     * 构建 SELECT 列
     */
    @Override
    protected String buildSelectColumns(Map<String, String> aliasMap) {
        List<String> selectCols = new ArrayList<>();
        for (SysDatasetColumn column : columns) {
            if (CommonConst.YES.equals(column.getIsVisible())) {
                String colSql = column.getColumnSql();
                String colAlias = column.getColumnAlias();
                if (FuncUtil.isNotEmpty(colAlias)) {
                    selectCols.add(colSql + " AS `" + colAlias + "`");
                } else {
                    selectCols.add(colSql);
                }
            }
        }
        return selectCols.isEmpty() ? "*" : String.join(", ", selectCols);
    }

    /**
     * 构建 FROM 子句
     */
    @Override
    protected String buildFromClause() {
        return " FROM " + buildFromAndJoin();
    }

    /**
     * 构建查询条件子句（WHERE/GROUP BY/HAVING/ORDER BY）
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

        // 构建GROUP BY
        String groupByClause = buildGroupBy();
        if (FuncUtil.isNotEmpty(groupByClause)) {
            clause.append(" GROUP BY ").append(groupByClause);
        }

        // 构建HAVING
        String havingClause = buildHaving(req, aliasMap, parameters);
        if (FuncUtil.isNotEmpty(havingClause)) {
            clause.append(" HAVING ").append(havingClause);
        }

        // 构建ORDER BY（仅 SELECT 需要）
        if (includeOrder) {
            String orderByClause = buildOrderBy(req);
            if (FuncUtil.isNotEmpty(orderByClause)) {
                clause.append(" ORDER BY ").append(orderByClause);
            }
        }

        return clause.toString();
    }

    /**
     * 重写 COUNT SQL 构建（Dataset 需要子查询包裹）
     */
    @Override
    protected String buildCountSql(String fromClause, String clausesWithoutOrder) {
        return "SELECT COUNT(*) FROM (SELECT 1" + fromClause + clausesWithoutOrder + ") AS count_table";
    }

    @Override
    public String buildInsert(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Dataset模式不支持INSERT操作");
    }

    @Override
    public String buildUpdate(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Dataset模式不支持UPDATE操作");
    }

    @Override
    public String buildDelete(Object id, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Dataset模式不支持DELETE操作");
    }

    /**
     * 构建FROM和JOIN子句
     */
    private String buildFromAndJoin() {
        // 按table_order排序
        List<SysDatasetTable> sortedDatasets = datasets.stream()
                .sorted(Comparator.comparing(SysDatasetTable::getTableOrder))
                .collect(Collectors.toList());

        StringBuilder fromClause = new StringBuilder();
        SysDatasetTable mainTable = null;

        // 找到主表（JOIN_TYPE为NULL）
        for (SysDatasetTable dataset : sortedDatasets) {
            if (FuncUtil.isEmpty(dataset.getJoinType())) {
                mainTable = dataset;
                break;
            }
        }

        if (mainTable == null && !sortedDatasets.isEmpty()) {
            // 兜底：如果没有显式主表，使用第一个
            mainTable = sortedDatasets.get(0);
        }

        if (mainTable != null) {
            fromClause.append(mainTable.getTableSql());
            if (FuncUtil.isNotEmpty(mainTable.getTableAlias())) {
                fromClause.append(" AS ").append(mainTable.getTableAlias());
            }

            // 构建JOIN
            for (SysDatasetTable dataset : sortedDatasets) {
                if (dataset.getId().equals(mainTable.getId())) {
                    continue;
                }
                if (FuncUtil.isNotEmpty(dataset.getJoinType())) {
                    fromClause.append(" ")
                            .append(dataset.getJoinType().toUpperCase())
                            .append(" JOIN ")
                            .append(dataset.getTableSql());

                    if (FuncUtil.isNotEmpty(dataset.getTableAlias())) {
                        fromClause.append(" AS ").append(dataset.getTableAlias());
                    }

                    if (FuncUtil.isNotEmpty(dataset.getJoinCondition())) {
                        fromClause.append(" ON ").append(dataset.getJoinCondition());
                    }
                }
            }
        }

        return fromClause.toString();
    }

    /**
     * 构建WHERE子句
     */
    private String buildWhere(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        if (FuncUtil.isEmpty(req.getCondition())) {
            return "";
        }

        // 过滤出非聚合字段的条件（聚合字段走HAVING）
        Set<String> aggregateFields = getAggregateFields();
        return buildCondition(req.getCondition(), aliasMap, parameters, aggregateFields, false);
    }

    /**
     * 构建HAVING子句
     */
    private String buildHaving(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        if (FuncUtil.isEmpty(req.getCondition())) {
            return "";
        }

        // 只处理聚合字段的条件
        Set<String> aggregateFields = getAggregateFields();
        return buildCondition(req.getCondition(), aliasMap, parameters, aggregateFields, true);
    }

    /**
     * 递归构建条件表达式
     * Dataset模式特殊处理：区分聚合字段（WHERE）和非聚合字段（HAVING）
     *
     * @param query           查询条件
     * @param aliasMap        别名映射
     * @param parameters      参数Map
     * @param aggregateFields 聚合字段集合
     * @param onlyAggregate   是否只处理聚合字段（true=HAVING, false=WHERE）
     */
    private String buildCondition(AdvancedQuery query, Map<String, String> aliasMap, Map<String, Object> parameters,
                                  Set<String> aggregateFields, boolean onlyAggregate) {
        if (FuncUtil.isEmpty(query)) {
            return "";
        }

        List<String> conditions = new ArrayList<>();

        // 处理子条件列表
        if (FuncUtil.isNotEmpty(query.getConditionList())) {
            for (AdvancedQuery subQuery : query.getConditionList()) {
                String subCondition = buildCondition(subQuery, aliasMap, parameters, aggregateFields, onlyAggregate);
                if (FuncUtil.isNotEmpty(subCondition)) {
                    conditions.add("(" + subCondition + ")");
                }
            }
        }

        // 处理叶子条件
        if (FuncUtil.isNotEmpty(query.getProperty())) {
            boolean isAggregate = aggregateFields.contains(query.getProperty());
            if ((onlyAggregate && isAggregate) || (!onlyAggregate && !isAggregate)) {
                String fieldCondition = buildFieldCondition(query, aliasMap, parameters);
                if (FuncUtil.isNotEmpty(fieldCondition)) {
                    conditions.add(fieldCondition);
                }
            }
        }

        if (conditions.isEmpty()) {
            return "";
        }

        String operator = FuncUtil.isEmpty(query.getAndOr()) ? "AND" :
                (AdvancedQuery.OR.equals(query.getAndOr()) ? "OR" : "AND");
        return String.join(" " + operator + " ", conditions);
    }


    /**
     * 构建GROUP BY子句
     */
    private String buildGroupBy() {
        List<String> groupByCols = new ArrayList<>();
        Set<String> aggregateFields = getAggregateFields();

        for (SysDatasetColumn column : columns) {
            if (CommonConst.YES.equals(column.getIsVisible())) {
                // 非聚合字段需要group by
                if (!CommonConst.YES.equals(column.getIsAggregate())) {
                    // 如果所有列都是非聚合的，则不需要group by
                    if (!aggregateFields.isEmpty()) {
                        groupByCols.add(column.getColumnSql());
                    }
                }
            }
        }

        return groupByCols.isEmpty() ? "" : String.join(", ", groupByCols);
    }

    /**
     * 构建ORDER BY子句
     */
    private String buildOrderBy(AdvancedQueryReq req) {
        // 优先使用请求中的排序
        if (FuncUtil.isNotEmpty(req.getSortList())) {
            return req.getSortList().stream()
                    .map(sort -> {
                        String field = sort.getProperty();
                        String direction = sort.getType() != null && sort.getType() == 2 ? "DESC" : "ASC";
                        return field + " " + direction;
                    })
                    .collect(Collectors.joining(", "));
        }

        // 默认按displayOrder排序
        List<String> orderCols = columns.stream()
                .filter(col -> CommonConst.YES.equals(col.getIsVisible()))
                .sorted(Comparator.comparing(SysDatasetColumn::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(1)
                .map(col -> {
                    String colAlias = col.getColumnAlias();
                    return FuncUtil.isNotEmpty(colAlias) ? colAlias : col.getColumnSql();
                })
                .collect(Collectors.toList());

        return orderCols.isEmpty() ? "" : orderCols.get(0);
    }

    /**
     * 获取聚合字段集合
     */
    private Set<String> getAggregateFields() {
        return columns.stream()
                .filter(col -> CommonConst.YES.equals(col.getIsAggregate()))
                .map(SysDatasetColumn::getColumnAlias)
                .filter(FuncUtil::isNotEmpty)
                .collect(Collectors.toSet());
    }
}
