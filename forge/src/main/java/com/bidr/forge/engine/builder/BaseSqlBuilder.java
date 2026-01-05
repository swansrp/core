package com.bidr.forge.engine.builder;

import com.bidr.forge.engine.builder.base.SqlBuilderQueryInf;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL构建器基类
 * 提取通用的条件构建逻辑
 *
 * @author Sharp
 * @since 2025-11-24
 */
public abstract class BaseSqlBuilder implements SqlBuilder {

    /**
     * 通用的 buildSqlParts 实现（一次解析，生成 SELECT 和 COUNT）
     * 子类只需提供差异化的部分：SELECT 列、FROM 子句、查询条件构建
     */
    @Override
    public SqlBuilderQueryInf.SqlParts buildSqlParts(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        // 1. 构建 SELECT 列（子类实现）
        String selectColumns = buildSelectColumns(aliasMap);

        // 2. 构建 FROM 子句（子类实现）
        String fromClause = buildFromClause();

        // 3. 一次性构建 WHERE/GROUP BY/HAVING/ORDER BY（含参数填充）
        String allClauses = buildQueryClauses(req, aliasMap, parameters, true);

        // 4. 拼接 SELECT SQL（含 LIMIT）
        StringBuilder selectSql = new StringBuilder("SELECT ").append(selectColumns).append(fromClause).append(allClauses);
        if (FuncUtil.isNotEmpty(req.getCurrentPage()) && FuncUtil.isNotEmpty(req.getPageSize())) {
            long offset = (req.getCurrentPage() - 1) * req.getPageSize();
            selectSql.append(" LIMIT ").append(offset).append(", ").append(req.getPageSize());
        }

        // 5. 拼接 COUNT SQL（不含 ORDER BY）
        String clausesWithoutOrder = buildQueryClauses(req, aliasMap, new HashMap<>(), false);
        String countSql = buildCountSql(fromClause, clausesWithoutOrder);

        return new SqlBuilderQueryInf.SqlParts(selectSql.toString(), countSql);
    }

    /**
     * 构建 SELECT 列（子类实现）
     *
     * @param aliasMap 字段别名映射
     * @return SELECT 列 SQL 片段
     */
    protected abstract String buildSelectColumns(Map<String, String> aliasMap);

    /**
     * 构建 FROM 子句（子类实现）
     *
     * @return FROM 子句 SQL 片段（包括 " FROM " 前缀）
     */
    protected abstract String buildFromClause();

    /**
     * 构建查询条件子句（WHERE/GROUP BY/HAVING/ORDER BY）
     * 子类可以重写以支持更复杂的逻辑（如 Dataset 的 GROUP BY/HAVING）
     *
     * @param req          查询请求
     * @param aliasMap     字段别名映射
     * @param parameters   参数Map（输出参数）
     * @param includeOrder 是否包含 ORDER BY
     * @return 查询条件 SQL 片段
     */
    public abstract String buildQueryClauses(AdvancedQueryReq req, Map<String, String> aliasMap,
                                             Map<String, Object> parameters, boolean includeOrder);

    /**
     * 构建 COUNT SQL（子类可以重写以支持特殊逻辑，如 Dataset 的子查询）
     *
     * @param fromClause          FROM 子句
     * @param clausesWithoutOrder WHERE/GROUP BY/HAVING（不含 ORDER BY）
     * @return COUNT SQL
     */
    protected String buildCountSql(String fromClause, String clausesWithoutOrder) {
        // 默认实现：适用于 Matrix 等单表查询
        return "SELECT COUNT(*)" + fromClause + clausesWithoutOrder;
    }

    /**
     * 递归构建条件表达式
     *
     * @param query      查询条件
     * @param aliasMap   别名映射
     * @param parameters 参数Map
     * @return SQL条件字符串
     */
    protected String buildCondition(AdvancedQuery query, Map<String, String> aliasMap, Map<String, Object> parameters) {
        if (FuncUtil.isEmpty(query)) {
            return "";
        }

        List<String> conditions = new ArrayList<>();

        // 处理子条件列表
        if (FuncUtil.isNotEmpty(query.getConditionList())) {
            for (AdvancedQuery subQuery : query.getConditionList()) {
                String subCondition = buildCondition(subQuery, aliasMap, parameters);
                if (FuncUtil.isNotEmpty(subCondition)) {
                    conditions.add("(" + subCondition + ")");
                }
            }
        }

        // 处理叶子条件
        if (FuncUtil.isNotEmpty(query.getProperty())) {
            String fieldCondition = buildFieldCondition(query, aliasMap, parameters);
            if (FuncUtil.isNotEmpty(fieldCondition)) {
                conditions.add(fieldCondition);
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
     * 构建单个字段条件
     * 子类可以重写以自定义列名格式（如添加反引号）
     *
     * @param query      查询条件
     * @param aliasMap   别名映射
     * @param parameters 参数Map
     * @return SQL条件字符串
     */
    protected String buildFieldCondition(AdvancedQuery query, Map<String, String> aliasMap, Map<String, Object> parameters) {
        String fieldName = query.getProperty();
        String columnName = aliasMap.getOrDefault(fieldName, fieldName);
        Integer relation = query.getRelation();
        List<?> value = query.getValue();

        if (relation == null) {
            return "";
        }

        String paramKey = "param_" + parameters.size();
        PortalConditionDict conditionDict = PortalConditionDict.of(relation);

        if (conditionDict == null) {
            return "";
        }

        // 格式化列名（子类可重写）
        String formattedColumn = formatColumnName(columnName);

        switch (conditionDict) {
            case EQUAL:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " = :" + paramKey;
            case NOT_EQUAL:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " != :" + paramKey;
            case GREATER:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " > :" + paramKey;
            case GREATER_EQUAL:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " >= :" + paramKey;
            case LESS:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " < :" + paramKey;
            case LESS_EQUAL:
                parameters.put(paramKey, getFirstValue(value));
                return formattedColumn + " <= :" + paramKey;
            case NULL:
                return formattedColumn + " IS NULL";
            case NOT_NULL:
                return formattedColumn + " IS NOT NULL";
            case LIKE:
                parameters.put(paramKey, "%" + getFirstValue(value) + "%");
                return formattedColumn + " LIKE :" + paramKey;
            case NOT_LIKE:
                parameters.put(paramKey, "%" + getFirstValue(value) + "%");
                return formattedColumn + " NOT LIKE :" + paramKey;
            case IN:
            case CONTAIN:
                if (FuncUtil.isNotEmpty(value)) {
                    parameters.put(paramKey, value);
                    return formattedColumn + " IN (:" + paramKey + ")";
                }
                break;
            case NOT_IN:
                if (FuncUtil.isNotEmpty(value)) {
                    parameters.put(paramKey, value);
                    return formattedColumn + " NOT IN (:" + paramKey + ")";
                }
                break;
            case BETWEEN:
                if (FuncUtil.isNotEmpty(value) && value.size() >= 2) {
                    String paramKey1 = paramKey + "_start";
                    String paramKey2 = paramKey + "_end";
                    parameters.put(paramKey1, value.get(0));
                    parameters.put(paramKey2, value.get(1));
                    return formattedColumn + " BETWEEN :" + paramKey1 + " AND :" + paramKey2;
                }
                break;
            case NOT_BETWEEN:
                if (FuncUtil.isNotEmpty(value) && value.size() >= 2) {
                    String paramKey1 = paramKey + "_start";
                    String paramKey2 = paramKey + "_end";
                    parameters.put(paramKey1, value.get(0));
                    parameters.put(paramKey2, value.get(1));
                    return formattedColumn + " NOT BETWEEN :" + paramKey1 + " AND :" + paramKey2;
                }
                break;
            default:
                break;
        }

        return "";
    }

    /**
     * 格式化列名
     * 默认不做处理，子类可以重写（如 Matrix 加反引号，Dataset 不加）
     *
     * @param columnName 列名
     * @return 格式化后的列名
     */
    protected String formatColumnName(String columnName) {
        return columnName;
    }

    /**
     * 获取列表的第一个值
     *
     * @param value 值列表
     * @return 第一个值
     */
    protected Object getFirstValue(List<?> value) {
        return FuncUtil.isNotEmpty(value) ? value.get(0) : null;
    }

    public String findVoColumnName(String columnName, Map<String, String> aliasMap) {
        for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
            if (entry.getValue().equals(columnName)) {
                return entry.getKey();
            }
        }
        return columnName;
    }

    /**
     * 构建 WHERE 条件（参数化），对外公开以便 Driver 在统计/自定义SQL 时复用统一的条件解析逻辑。
     */
    public String buildWhereCondition(AdvancedQuery query, Map<String, String> aliasMap, Map<String, Object> parameters) {
        return buildCondition(query, aliasMap, parameters);
    }
}
