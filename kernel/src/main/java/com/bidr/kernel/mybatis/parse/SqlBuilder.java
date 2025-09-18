package com.bidr.kernel.mybatis.parse;

import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.mybatis.bo.SqlColumn;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;

import java.util.*;
import java.util.stream.Collectors;

import static com.bidr.kernel.vo.portal.AdvancedQuery.AND;

/**
 * Title: SqlBuilder
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/17 14:12
 */


public class SqlBuilder {

    public static String buildSql(List<SqlColumn> columns, String from, AdvancedQuery where, List<String> groupBy,
                                  List<String> orderBy, AdvancedQuery having, String lastSql) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }

        String selectPart = columns.stream().map(c -> c.getSql() + " AS " + c.getAlias())
                .collect(Collectors.joining(", "));

        Map<String, String> aliasToSql = columns.stream()
                .collect(Collectors.toMap(SqlColumn::getAlias, SqlColumn::getSql));

        // WHERE
        String wherePart = (where != null) ? " WHERE " + parseAdvancedQuery(where, aliasToSql) : "";

        // GROUP BY
        String groupPart = (groupBy != null && !groupBy.isEmpty()) ? " GROUP BY " +
                groupBy.stream().map(f -> aliasToSql.getOrDefault(f, f)).collect(Collectors.joining(", ")) : "";

        // HAVING
        String havingPart = (having != null) ? " HAVING " + parseAdvancedQuery(having, aliasToSql) : "";

        // ORDER BY
        String orderPart = (orderBy != null && !orderBy.isEmpty()) ? " ORDER BY " +
                orderBy.stream().map(f -> aliasToSql.getOrDefault(f, f)).collect(Collectors.joining(", ")) : "";

        String lastPart = (FuncUtil.isNotEmpty(lastSql)) ? " " + lastSql : "";

        return "SELECT " + selectPart + " FROM " + from + wherePart + groupPart + havingPart + orderPart + lastPart;
    }

    private static String parseAdvancedQuery(AdvancedQuery query, Map<String, String> aliasToSql) {
        if (query == null) {
            return "";
        }

        String logic = query.getAndOr() != null ? query.getAndOr() : AdvancedQuery.AND;

        // 递归子条件
        if (query.getConditionList() != null && !query.getConditionList().isEmpty()) {
            String joined = query.getConditionList().stream()
                    .map(child -> "(" + parseAdvancedQuery(child, aliasToSql) + ")")
                    .collect(Collectors.joining(" " + (StringUtil.convertSwitch(logic) ? "OR" : "AND") + " "));
            if (joined.isEmpty()) {
                return AND.equalsIgnoreCase(logic) ? "1=1" : "1<>1";
            }
            return joined;
        }

        // 单个条件
        if (FuncUtil.isEmpty(query.getProperty())) {
            return AdvancedQuery.AND.equalsIgnoreCase(logic) ? "1=1" : "1<>1";
        }

        // 单个条件
        String column = aliasToSql.getOrDefault(query.getProperty(), query.getProperty());
        PortalConditionDict op = PortalConditionDict.of(query.getRelation());
        List<?> value = query.getValue();

        switch (op) {
            case EQUAL:
                return column + " = " + formatValue(value.get(0));
            case NOT_EQUAL:
                return column + " <> " + formatValue(value.get(0));
            case GREATER:
                return column + " > " + formatValue(value.get(0));
            case GREATER_EQUAL:
                return column + " >= " + formatValue(value.get(0));
            case LESS:
                return column + " < " + formatValue(value.get(0));
            case LESS_EQUAL:
                return column + " <= " + formatValue(value.get(0));
            case NULL:
                return column + " IS NULL";
            case NOT_NULL:
                return column + " IS NOT NULL";
            case LIKE:
                return buildSmartLike(column, value, false);
            case NOT_LIKE:
                return buildSmartLike(column, value, true);
            case IN:
                return column + " IN (" + joinValues(value) + ")";
            case NOT_IN:
                return column + " NOT IN (" + joinValues(value) + ")";
            case BETWEEN: {
                return column + " BETWEEN " + formatValue(value.get(0)) + " AND " + formatValue(value.get(1));
            }
            case NOT_BETWEEN: {
                return column + " NOT BETWEEN " + formatValue(value.get(0)) + " AND " + formatValue(value.get(1));
            }
            case CONTAIN:
            case CONTAIN_IN_AND:
                return buildContain(column, value, "AND");
            case CONTAIN_IN_OR:
                return buildContain(column, value, "OR");
            default:
                throw new IllegalArgumentException("不支持的操作符: " + op);
        }
    }

    private static String buildSmartLike(String column, Object value, boolean notLike) {
        if (value == null) {
            return "1=1";
        }

        String strVal = value.toString().trim();
        if (strVal.isEmpty()) {
            return "1=1";
        }

        // 空格分隔 AND
        String[] andArray = strVal.split(" ");
        if (andArray.length > 1) {
            return Arrays.stream(andArray).filter(s -> !s.isEmpty())
                    .map(s -> column + (notLike ? " NOT LIKE " : " LIKE ") + formatValue("%" + s + "%"))
                    .collect(Collectors.joining(" AND "));
        }

        // 竖线分隔 OR
        String[] orArray = strVal.split("\\|");
        if (orArray.length > 1) {
            return Arrays.stream(orArray).filter(s -> !s.isEmpty())
                    .map(s -> column + (notLike ? " NOT LIKE " : " LIKE ") + formatValue("%" + s + "%"))
                    .collect(Collectors.joining(" OR "));
        }

        // 默认普通 LIKE
        return column + (notLike ? " NOT LIKE " : " LIKE ") + formatValue("%" + strVal + "%");
    }

    private static String buildContain(String column, Object value, String logic) {
        if (value == null) {
            return logic.equalsIgnoreCase("AND") ? "1=1" : "1<>1";
        }

        List<String> items = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) {
                if (v != null) {
                    items.add(v.toString().trim());
                }
            }
        } else {
            String strVal = value.toString().trim();
            if (strVal.isEmpty()) {
                return logic.equalsIgnoreCase("AND") ? "1=1" : "1<>1";
            }

            String[] andArray = strVal.split(" ");
            if (andArray.length > 1 && logic.equalsIgnoreCase("AND")) {
                items.addAll(Arrays.asList(andArray));
            } else {
                String[] orArray = strVal.split("\\|");
                items.addAll(Arrays.asList(orArray));
            }
        }

        String joiner = logic.equalsIgnoreCase("AND") ? " AND " : " OR ";
        return items.stream().filter(s -> !s.isEmpty())
                .map(s -> "FIND_IN_SET(" + formatValue(s) + ", " + column + ") > 0")
                .collect(Collectors.joining(joiner));
    }

    private static String formatValue(Object value) {
        if (FuncUtil.isNotEmpty(value)) {
            if (value instanceof Number) {
                return value.toString();
            }
            return "'" + value.toString().replace("'", "''") + "'";
        } else {
            return "";
        }
    }

    private static String joinValues(Object value) {
        if (value instanceof Collection) {
            return ((Collection<?>) value).stream().map(SqlBuilder::formatValue).collect(Collectors.joining(", "));
        }
        return formatValue(value);
    }
}
