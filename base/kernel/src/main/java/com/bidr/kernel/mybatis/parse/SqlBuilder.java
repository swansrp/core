package com.bidr.kernel.mybatis.parse;

import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.mybatis.bo.SqlColumn;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.SortVO;

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

    public static String buildSql(List<SqlColumn> columns, String from, AdvancedQuery where,
                                  Collection<SqlColumn> groupBy, List<SortVO> orderBy, AdvancedQuery having,
                                  String lastSql) {
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
                groupBy.stream().map(f -> aliasToSql.getOrDefault(f.getAlias(), f.getSql()))
                        .collect(Collectors.joining(", ")) : "";

        // HAVING
        String havingPart = (having != null && FuncUtil.isNotEmpty(having.getConditionList())) ?
                " HAVING " + parseAdvancedQuery(having, aliasToSql) : "";

        // ORDER BY
        String orderPart = "";
        if (FuncUtil.isNotEmpty(orderBy)) {
            List<String> orderFragments = new ArrayList<>();
            for (SortVO s : orderBy) {
                if (s == null || s.getProperty() == null) {
                    continue;
                }

                // 1) 优先使用 alias 映射回原字段
                String fieldSql = aliasToSql.getOrDefault(s.getProperty(), s.getProperty());

                // 2) 根据 type 决定升降序（1=ASC, 2=DESC，其他默认 ASC）
                String direction;
                if (s.getType() != null && s.getType().equals(PortalSortDict.DESC.getValue())) {
                    direction = "DESC";
                } else {
                    direction = "ASC";
                }

                orderFragments.add(fieldSql + " " + direction);
            }
            if (!orderFragments.isEmpty()) {
                orderPart = " ORDER BY " + String.join(", ", orderFragments);
            }
        }

        String lastPart = (FuncUtil.isNotEmpty(lastSql)) ? " " + lastSql : "";

        return "SELECT " + selectPart + " FROM " + from + wherePart + groupPart + havingPart + orderPart + lastPart;
    }

    private static String parseAdvancedQuery(AdvancedQuery query, Map<String, String> aliasToSql) {
        return parseAdvancedQuery(query, aliasToSql, AdvancedQuery.AND);
    }

    /**
     * 递归解析高级查询条件
     *
     * @param query       条件节点
     * @param aliasToSql  别名到列表达式的映射
     * @param parentLogic 父节点连接方式：本节点条件不可用需兜底恒等式时按它取值
     *                    （AND 取恒真即跳过该条件，OR 取恒假即不让空条件放大结果集），
     *                    与 PortalSelectRepo.applyEmptyCondition 口径一致
     * @return 条件 SQL 片段（除入参为 null 外不会返回空串）
     */
    private static String parseAdvancedQuery(AdvancedQuery query, Map<String, String> aliasToSql, String parentLogic) {
        if (query == null) {
            return "";
        }

        String logic = query.getAndOr() != null ? query.getAndOr() : AdvancedQuery.AND;

        // 递归子条件
        if (query.getConditionList() != null && !query.getConditionList().isEmpty()) {
            List<String> fragments = new ArrayList<>();
            for (AdvancedQuery child : query.getConditionList()) {
                String fragment = parseAdvancedQuery(child, aliasToSql, logic);
                // 空片段必须丢弃，否则会拼出 `()` 这种非法语句
                if (FuncUtil.isNotEmpty(fragment)) {
                    fragments.add("(" + fragment + ")");
                }
            }
            if (fragments.isEmpty()) {
                return identityFragment(parentLogic);
            }
            return fragments.stream().collect(Collectors.joining(" " + (StringUtil.convertSwitch(logic) ? "OR" : "AND") + " "));
        }

        // 单个条件
        if (FuncUtil.isEmpty(query.getProperty())) {
            return identityFragment(parentLogic);
        }

        // 单个条件
        String column = aliasToSql.getOrDefault(query.getProperty(), query.getProperty());
        PortalConditionDict op = PortalConditionDict.of(query.getRelation());
        if (op == null) {
            return identityFragment(parentLogic);
        }

        // 条件值需先清洗：查询条件来自前端配置，脏数据里会出现嵌套数组（如 value:[[]]）
        // 这类值内联成字面量时输出空串，于是拼出 `col = ` 这种残缺 SQL 让数据库直接语法报错，整条查询失败
        // 清洗后可用值个数不足该关系类型的最低要求时，本条条件按恒等式跳过
        List<Object> values = FuncUtil.scalarValues(query.getValue());
        if (values.size() < op.requiredValueCount()) {
            return identityFragment(parentLogic);
        }

        switch (op) {
            case EQUAL:
                return column + " = " + formatValue(values.get(0));
            case NOT_EQUAL:
                return column + " <> " + formatValue(values.get(0));
            case GREATER:
                return column + " > " + formatValue(values.get(0));
            case GREATER_EQUAL:
                return column + " >= " + formatValue(values.get(0));
            case LESS:
                return column + " < " + formatValue(values.get(0));
            case LESS_EQUAL:
                return column + " <= " + formatValue(values.get(0));
            case NULL:
                return column + " IS NULL";
            case NOT_NULL:
                return column + " IS NOT NULL";
            // LIKE 只取第一个可用值：与 PortalSelectRepo 的 smartLike 口径一致
            // （原写法把整个 List 传入，toString 后得到 "[特斯拉]" 这类带方括号的匹配串，永远查不到数据）
            case LIKE:
                return buildSmartLike(column, values.get(0), false);
            case NOT_LIKE:
                return buildSmartLike(column, values.get(0), true);
            case IN:
                return column + " IN (" + joinValues(values) + ")";
            case NOT_IN:
                return column + " NOT IN (" + joinValues(values) + ")";
            case BETWEEN: {
                return column + " BETWEEN " + formatValue(values.get(0)) + " AND " + formatValue(values.get(1));
            }
            case NOT_BETWEEN: {
                return column + " NOT BETWEEN " + formatValue(values.get(0)) + " AND " + formatValue(values.get(1));
            }
            case CONTAIN:
            case CONTAIN_IN_AND:
                return buildContain(column, values, "AND");
            case CONTAIN_IN_OR:
                return buildContain(column, values, "OR");
            default:
                throw new IllegalArgumentException("不支持的操作符: " + op);
        }
    }

    /**
     * 条件不可用时的恒等式兜底片段
     *
     * @param logic 该条件所属的连接方式
     * @return AND→1=1（跳过该条件），其余→1<>1（不放大结果集）
     */
    private static String identityFragment(String logic) {
        return AND.equalsIgnoreCase(logic) ? "1=1" : "1<>1";
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
        String condition = items.stream().filter(s -> !s.isEmpty())
                .map(s -> "FIND_IN_SET(" + formatValue(s) + ", " + column + ") > 0")
                .collect(Collectors.joining(joiner));
        // 全为空值时按恒等式兜底，避免返回空片段让上层拼出 `()`
        return FuncUtil.isEmpty(condition) ? identityFragment(logic) : condition;
    }

    private static String formatValue(Object value) {
        if (!FuncUtil.isScalar(value)) {
            // null 或集合/数组等嵌套结构无法内联为单个字面量，返回空串，由调用方的值个数校验兜底
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        // 空串是合法的维度值，必须内联成 '' 字面量；此前按判空返回空串正是 `col = ` 残缺 SQL 的来源
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static String joinValues(Object value) {
        if (value instanceof Collection) {
            return ((Collection<?>) value).stream().map(SqlBuilder::formatValue).collect(Collectors.joining(", "));
        }
        return formatValue(value);
    }
}
