package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface AdminStatisticParseInf {
    default String parseStatisticSelect(AdvancedQuery query, String statisticColumn) {
        String thenStr = FuncUtil.isNotEmpty(statisticColumn) ? statisticColumn : "1";
        String elseStr = FuncUtil.isNotEmpty(statisticColumn) ? "0" : "null";
        StringBuilder sql = new StringBuilder();
        String conditionSql = parseStatisticSelectRecursion(sql, query, SqlConstant.AND);
        return String.format("case when %s then %s else %s end", conditionSql, thenStr, elseStr);
    }

    default String parseStatisticSelectRecursion(StringBuilder sql, AdvancedQuery query, String andOr) {
        List<String> conditions = new ArrayList<>();

        if (FuncUtil.isNotEmpty(query.getConditionList())) {
            // 递归处理子条件
            for (AdvancedQuery subQuery : query.getConditionList()) {
                StringBuilder subSql = new StringBuilder();
                String result = parseStatisticSelectRecursion(subSql, subQuery, query.getAndOr());
                if (FuncUtil.isNotEmpty(result)) {
                    conditions.add("(" + result + ")");
                }
            }
            String logic = FuncUtil.equals(query.getAndOr(), SqlConstant.OR) ? " or " : " and ";
            sql.append(String.join(logic, conditions));
        } else {
            // 构建单个查询条件
            if (FuncUtil.isNotEmpty(query.getProperty()) && FuncUtil.isNotEmpty(query.getRelation())) {
                if ((query.getRelation().equals(PortalConditionDict.NULL.getValue())
                        || query.getRelation().equals(PortalConditionDict.NOT_NULL.getValue()))
                        || FuncUtil.isNotEmpty(query.getValue())) {
                    String conditionStr = buildQueryStr(query);
                    sql.append(conditionStr);
                } else {
                    // 值为空时的补充逻辑
                    sql.append(FuncUtil.equals(andOr, SqlConstant.AND) ? "1=1" : "1=0");
                }
            } else {
                // 值为空时的补充逻辑
                sql.append(FuncUtil.equals(andOr, SqlConstant.AND) ? "1=1" : "1=0");
            }
        }
        return sql.toString();
    }


    default String buildQueryStr(AdvancedQuery query) {
        String columnName = query.getProperty();
        List<?> valuesList = query.getValue();
        if (FuncUtil.isEmpty(valuesList)) {
            return "1=1";
        }
        String firstValue = "'" + valuesList.get(0) + "'";
        Object secondValue = "";
        if (valuesList.size() > 1) {
            secondValue = "'" + valuesList.get(1) + "'";
        }
        String values = query.getValue().stream()
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(","));
        switch (PortalConditionDict.of(query.getRelation())) {
            case EQUAL:
                return columnName + " = " + values;
            case NOT_EQUAL:
                return columnName + " <> " + values;
            case IN:
                return columnName + " in (" + values + ")";
            case NOT_IN:
                return columnName + " not in (" + values + ")";
            case LIKE:
                if (valuesList.get(0) instanceof String) {
                    String str = valuesList.get(0).toString();
                    if (str.contains(" ")) {
                        return Arrays.stream(str.split(" "))
                                .map(v -> columnName + " like '%" + v + "%'")
                                .collect(Collectors.joining(" and "));
                    } else if (str.contains("|")) {
                        return Arrays.stream(str.split("\\|"))
                                .map(v -> columnName + " like '%" + v + "%'")
                                .collect(Collectors.joining(" or "));
                    } else {
                        return columnName + " like '%" + str + "%'";
                    }
                }
                return columnName + " like '%" + firstValue + "%'";
            case NOT_LIKE:
                return columnName + " not like '%" + firstValue + "%'";
            case GREATER:
                return columnName + " > " + firstValue;
            case GREATER_EQUAL:
                return columnName + " >= " + firstValue;
            case LESS:
                return columnName + " < " + firstValue;
            case LESS_EQUAL:
                return columnName + " <= " + firstValue;
            case NULL:
                return columnName + " is null";
            case NOT_NULL:
                return columnName + " is not null";
            case BETWEEN:
                return columnName + " between " + firstValue + " and " + secondValue;
            case NOT_BETWEEN:
                return columnName + " not between " + firstValue + " and " + secondValue;
            case CONTAIN:
                return "FIND_IN_SET(" + firstValue + "," + columnName + ") > 0";
            case CONTAIN_IN_OR:
                return valuesList.stream()
                        .map(v -> "(FIND_IN_SET(" + v + "," + columnName + ") > 0)")
                        .collect(Collectors.joining(" or "));
            case CONTAIN_IN_AND:
                return valuesList.stream()
                        .map(v -> "(FIND_IN_SET(" + v + "," + columnName + ") > 0)")
                        .collect(Collectors.joining(" and "));
            default:
                return "1=1";
        }
    }

}
