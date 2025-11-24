package com.bidr.forge.service.driver.builder;

import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;

import java.util.ArrayList;
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
}
