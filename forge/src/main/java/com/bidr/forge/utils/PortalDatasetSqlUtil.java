package com.bidr.forge.utils;

import com.bidr.forge.constant.dict.JoinTypeDict;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.bo.SqlColumn;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Title: PortalDatasetSqlUtil
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 13:45
 */

public class PortalDatasetSqlUtil {
    public static String buildSumSql(String querySql, List<String> columns) {
        // 移除ORDER BY子句以提高性能
        String sql = querySql.replaceAll("(?i)\\s+ORDER\\s+BY\\s+[^)]+", "");

        // 根据columns拼接sum的sql
        StringBuilder sumSql = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sumSql.append(", ");
            }
            sumSql.append("SUM(").append(columns.get(i)).append(") AS ").append(columns.get(i));
        }
        sumSql.append(" FROM (").append(sql).append(") AS tmp");
        return sumSql.toString();
    }

    public static String buildCountSql(String querySql) {
        // 移除ORDER BY子句以提高性能
        String sql = querySql.replaceAll("(?i)\\s+ORDER\\s+BY\\s+[^)]+", "");
        return "SELECT COUNT(*) FROM (" + sql + ") AS count_table";
    }

    public static String buildPaginatedSql(String querySql, Long currentPage, Long pageSize) {
        long offset = (currentPage - 1) * pageSize;
        return querySql + " LIMIT " + pageSize + " OFFSET " + offset;
    }

    public static String buildFromSql(List<SysDatasetTable> dataSet) {
        StringBuilder fromSql = new StringBuilder();
        for (int i = 0; i < dataSet.size(); i++) {
            SysDatasetTable dataset = dataSet.get(i);
            if (i == 0) {
                // 主表
                fromSql.append(dataset.getTableSql()).append(" AS ").append(dataset.getTableAlias());
            } else {
                // JOIN表
                if (FuncUtil.isNotEmpty(dataset.getJoinType())) {
                    fromSql.append(" ").append(DictEnumUtil.getEnumByValue(dataset.getJoinType(), JoinTypeDict.class,
                            JoinTypeDict.INNER));
                }
                fromSql.append(" JOIN ").append(dataset.getTableSql()).append(" AS ")
                        .append(dataset.getTableAlias());
                if (FuncUtil.isNotEmpty(dataset.getJoinCondition())) {
                    fromSql.append(" ON ").append(dataset.getJoinCondition());
                }
            }
        }
        return fromSql.toString();
    }

    /**
     * 通过表配置和列配置构建完整的SQL查询语句
     *
     * @param tableList  表配置列表
     * @param columnList 列配置列表
     * @return 完整的SQL查询字符串
     */
    public static String buildQuerySql(List<SysDatasetTable> tableList, List<SysDatasetColumn> columnList) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 构建 SELECT 子句
        if (FuncUtil.isEmpty(columnList)) {
            sql.append("*");
        } else {
            for (int i = 0; i < columnList.size(); i++) {
                SysDatasetColumn column = columnList.get(i);
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(column.getColumnSql());
                // 如果列SQL和别名不同，添加AS别名
                if (FuncUtil.isNotEmpty(column.getColumnAlias()) && 
                    !column.getColumnSql().equals(column.getColumnAlias())) {
                    sql.append(" AS ").append(column.getColumnAlias());
                }
            }
        }
        
        // 构建 FROM 子句
        sql.append(" FROM ");
        if (FuncUtil.isNotEmpty(tableList)) {
            sql.append(buildFromSql(tableList));
        }
        
        return sql.toString();
    }

    public static void parseSqlColumn(List<SysDatasetColumn> sysDatasetColumns, List<SqlColumn> columns,
                                      Map<String, SqlColumn> aggregateColumns,
                                      Map<String, SqlColumn> notAggregateColumns) {
        if (FuncUtil.isNotEmpty(sysDatasetColumns)) {
            for (SysDatasetColumn column : sysDatasetColumns) {
                SqlColumn sqlColumn = new SqlColumn(column.getColumnSql(), column.getColumnAlias());
                columns.add(sqlColumn);
                if (StringUtil.convertSwitch(column.getIsAggregate())) {
                    aggregateColumns.put(column.getColumnAlias(), sqlColumn);
                } else {
                    notAggregateColumns.put(column.getColumnAlias(), sqlColumn);
                }
            }
        }
    }

    public static void parseCondition(Map<String, SqlColumn> aggregateColumns, QueryConditionReq req,
                                      AdvancedQuery where, AdvancedQuery having) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO conditionVO : req.getConditionList()) {
                if (aggregateColumns.containsKey(conditionVO.getProperty())) {
                    having.addCondition(conditionVO);
                } else {
                    where.addCondition(conditionVO);
                }
            }
        }
    }

    /**
     * 解析SQL，返回两个配置对象列表
     */
    public static void parseSql(String sql, Long datasetId, List<SysDatasetTable> tableList,
                                List<SysDatasetColumn> columnList) throws JSQLParserException {
        Statement statement = CCJSqlParserUtil.parse(sql);
        Validator.assertTrue((statement instanceof Select), ErrCodeSys.SYS_ERR_MSG, "仅支持 SELECT SQL");

        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        AtomicInteger datasetOrder = new AtomicInteger(1);
        AtomicInteger columnOrder = new AtomicInteger(1);

        // === 1. 解析 FROM 主表 ===
        FromItem fromItem = plainSelect.getFromItem();
        tableList.add(buildDatasetTable(fromItem, "主表", datasetOrder.getAndIncrement(), null, null, datasetId));

        // === 2. 解析 JOIN 表 ===
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                FromItem joinItem = join.getRightItem();
                String joinType = join.isInner() ? JoinTypeDict.INNER.getValue() : join.isLeft() ? JoinTypeDict.LEFT.getValue() : join.isRight() ?
                        JoinTypeDict.RIGHT.getValue() : join.isFull() ? JoinTypeDict.FULL.getValue() : "JOIN";

                Collection<Expression> on = join.getOnExpressions();
                String condition = null;
                if (on != null && !on.isEmpty()) {
                    // 如果有多个表达式，默认用 AND 拼接
                    condition = on.stream().map(Expression::toString).collect(Collectors.joining(" AND "));
                }

                tableList.add(
                        buildDatasetTable(joinItem, "关联表", datasetOrder.getAndIncrement(), joinType, condition, datasetId));
            }
        }

        // === 3. 解析 SELECT 字段 ===
        for (SelectItem selectItem : plainSelect.getSelectItems()) {
            if (selectItem instanceof SelectExpressionItem) {
                SelectExpressionItem exprItem = (SelectExpressionItem) selectItem;
                Expression expression = exprItem.getExpression();
                Alias alias = exprItem.getAlias();

                SysDatasetColumn column = new SysDatasetColumn();
                column.setDatasetId(datasetId);
                column.setColumnSql(expression.toString());
                column.setColumnAlias(alias != null ? alias.getName() : expression.toString());
                column.setIsAggregate(isAggregateField(expression.toString()) ? CommonConst.YES : CommonConst.NO);
                column.setDisplayOrder(columnOrder.getAndIncrement());
                column.setIsVisible(CommonConst.YES);
                columnList.add(column);
            }
        }
    }

    /**
     * 构建 SysDatasetTable
     */
    private static SysDatasetTable buildDatasetTable(FromItem fromItem, String remark, int order, String joinType,
                                                     String joinCondition, Long datasetId) {
        SysDatasetTable table = new SysDatasetTable();
        table.setDatasetId(datasetId);
        table.setTableOrder(order);

        if (fromItem instanceof Table) {
            Table t = (Table) fromItem;
            table.setTableSql(t.getFullyQualifiedName());
            table.setTableAlias(t.getAlias() != null ? t.getAlias().getName() : t.getName());
        } else {
            table.setTableSql(fromItem.toString());
            table.setTableAlias(fromItem.getAlias() != null ? fromItem.getAlias().getName() : null);
        }

        table.setJoinType(joinType);
        table.setJoinCondition(joinCondition);
        table.setRemark(remark);
        return table;
    }

    /**
     * 判断是否为聚合字段
     */
    private static boolean isAggregateField(String expr) {
        String upper = expr.toUpperCase();
        return upper.contains("SUM(") || upper.contains("COUNT(") || upper.contains("MAX(") || upper.contains("MIN(") ||
                upper.contains("AVG(");
    }
}
