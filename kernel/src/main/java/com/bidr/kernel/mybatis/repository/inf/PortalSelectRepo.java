package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.OrderBySegmentList;
import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.mybatis.bo.DynamicColumn;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Title: PortalSelectRepo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 16:20
 */
public interface PortalSelectRepo<T> {

    /**
     * 构造通用查询wrapper
     *
     * @param req     请求
     * @param wrapper 现有查询条件
     * @return 查询wrapper
     */

    default QueryWrapper<T> parseQueryCondition(QueryConditionReq req, QueryWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                buildQueryWrapper(wrapper, condition);
            }
        }
        parseSort(req, wrapper);
        return wrapper;
    }

    default void buildQueryWrapper(QueryWrapper<T> wrapper, ConditionVO condition) {
        formatDateValue(condition);
        String columnName = getColumnName(condition, wrapper.getEntityClass());
        switch (PortalConditionDict.of(condition.getRelation())) {
            case EQUAL:
                wrapper.eq(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case NOT_EQUAL:
                wrapper.ne(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case IN:
                wrapper.in(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                break;
            case NOT_IN:
                wrapper.notIn(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                break;
            case LIKE:
                if (FuncUtil.isNotEmpty(condition.getValue())) {
                    if (FuncUtil.isNotEmpty(condition.getValue().get(0))) {
                        if (condition.getValue().get(0) instanceof String) {
                            final String[] andArray = condition.getValue().get(0).toString().split(" ");
                            final String[] orArray = condition.getValue().get(0).toString().split("\\|");
                            if (andArray.length > 1) {
                                wrapper.nested(wr -> {
                                    for (String s : andArray) {
                                        wr.like(FuncUtil.isNotEmpty(s), columnName, s);
                                    }
                                });
                            } else if (orArray.length > 1) {
                                wrapper.nested(wr -> {
                                    for (String s : orArray) {
                                        wr.like(FuncUtil.isNotEmpty(s), columnName, s).or();
                                    }
                                });
                            } else {
                                wrapper.like(columnName, condition.getValue().get(0));
                            }
                        }
                    }
                }
                break;
            case NOT_LIKE:
                wrapper.notLike(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case GREATER:
                wrapper.gt(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case GREATER_EQUAL:
                wrapper.ge(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case LESS:
                wrapper.lt(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case LESS_EQUAL:
                wrapper.le(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0));
                break;
            case NULL:
                wrapper.isNull(columnName);
                break;
            case NOT_NULL:
                wrapper.isNotNull(columnName);
                break;
            case BETWEEN:
                wrapper.between(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0), condition.getValue().get(1));
                break;
            case NOT_BETWEEN:
                wrapper.notBetween(
                        FuncUtil.isNotEmpty(condition.getValue()) && FuncUtil.isNotEmpty(condition.getValue().get(0)),
                        columnName, condition.getValue().get(0), condition.getValue().get(1));
                break;
            default:
                break;
        }
    }

    /**
     * 构造排序wrapper
     *
     * @param req     请求
     * @param wrapper 现有条件
     * @return 排序wrapper
     */

    default QueryWrapper<T> parseSort(QueryConditionReq req, QueryWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(req.getSortList())) {
            for (SortVO sortVO : req.getSortList()) {
                if (FuncUtil.isNotEmpty(sortVO.getProperty()) && FuncUtil.isNotEmpty(sortVO.getType())) {
                    String columnName = getColumnName(sortVO.getProperty(), wrapper.getEntityClass());
                    switch (PortalSortDict.of(sortVO.getType())) {
                        case ASC:
                            wrapper.orderByAsc(columnName);
                            break;
                        case DESC:
                            wrapper.orderByDesc(columnName);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return wrapper;
    }

    /**
     * 如果指定日期格式 将value输入转换成日期格式
     *
     * @param condition 条件
     */
    default void formatDateValue(ConditionVO condition) {
        if (FuncUtil.isNotEmpty(condition.getDateFormat())) {
            if (FuncUtil.isNotEmpty(condition.getValue())) {
                for (int index = 0; index < condition.getValue().size(); index++) {
                    if (FuncUtil.isNotEmpty(condition.getValue().get(index))) {
                        condition.getValue().set(index,
                                JsonUtil.readDateJson(condition.getValue().get(index), condition.getDateFormat(),
                                        Date.class));
                    }
                }
            }
        }
    }

    /**
     * 获取数据库字段名
     *
     * @param condition   查询条件
     * @param entityClass 所属数据库entity
     * @return 字段名
     */

    default String getColumnName(ConditionVO condition, Class<?> entityClass) {
        if (FuncUtil.isNotEmpty(condition.getProperty())) {
            return getColumnName(condition.getProperty(), entityClass);
        }
        return null;
    }

    /**
     * 获取数据库字段名
     *
     * @param property    java字段名
     * @param entityClass 所属数据库entity
     * @return 字段名
     */
    default String getColumnName(String property, Class<?> entityClass) {
        Field field = ReflectionUtil.getField(entityClass, property);
        if (FuncUtil.isNotEmpty(field)) {
            TableField annotation = field.getAnnotation(TableField.class);
            if (FuncUtil.isNotEmpty(annotation)) {
                return annotation.value();
            } else {
                return StringUtil.camelToUnderline(field.getName());
            }
        }
        return null;
    }

    /**
     * 构造通用查询wrapper
     *
     * @param req     请求
     * @param wrapper 现有查询条件
     * @return 查询wrapper
     */
    default MPJLambdaWrapper<T> parseQueryCondition(QueryConditionReq req, MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                buildQueryWrapper(wrapper, null, null, condition);
            }
        }
        parseSort(req, wrapper);
        return wrapper;
    }

    default void buildQueryWrapper(MPJLambdaWrapper<T> wrapper, Map<String, String> aliasMap,
                                   Collection<String> havingFields, ConditionVO condition) {
        formatDateValue(condition);
        String columnName = getColumnName(condition.getProperty(), aliasMap, wrapper.getEntityClass());
        if (FuncUtil.isEmpty(havingFields) || !havingFields.contains(condition.getProperty())) {
            switch (PortalConditionDict.of(condition.getRelation())) {
                case EQUAL:
                    wrapper.eq(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case NOT_EQUAL:
                    wrapper.ne(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case IN:
                    wrapper.in(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                    break;
                case NOT_IN:
                    wrapper.notIn(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                    break;
                case LIKE:
                    if (FuncUtil.isNotEmpty(condition.getValue())) {
                        if (FuncUtil.isNotEmpty(condition.getValue().get(0))) {
                            if (condition.getValue().get(0) instanceof String) {
                                final String[] andArray = condition.getValue().get(0).toString().split(" ");
                                final String[] orArray = condition.getValue().get(0).toString().split("\\|");
                                if (andArray.length > 1) {
                                    wrapper.nested(wr -> {
                                        for (String s : andArray) {
                                            wr.like(FuncUtil.isNotEmpty(s), columnName, s);
                                        }
                                    });
                                } else if (orArray.length > 1) {
                                    wrapper.nested(wr -> {
                                        for (String s : orArray) {
                                            wr.like(FuncUtil.isNotEmpty(s), columnName, s).or();
                                        }
                                    });
                                } else {
                                    wrapper.like(columnName, condition.getValue().get(0));
                                }
                            } else {
                                wrapper.like(columnName, condition.getValue().get(0));
                            }
                        }
                    }
                    break;
                case NOT_LIKE:
                    wrapper.notLike(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case GREATER:
                    wrapper.gt(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case GREATER_EQUAL:
                    wrapper.ge(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case LESS:
                    wrapper.lt(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case LESS_EQUAL:
                    wrapper.le(FuncUtil.isNotEmpty(condition.getValue()) &&
                            FuncUtil.isNotEmpty(condition.getValue().get(0)), columnName, condition.getValue().get(0));
                    break;
                case NULL:
                    wrapper.isNull(columnName);
                    break;
                case NOT_NULL:
                    wrapper.isNotNull(columnName);
                    break;
                case BETWEEN:
                    wrapper.between(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0),
                            condition.getValue().get(1));
                    break;
                case NOT_BETWEEN:
                    wrapper.notBetween(FuncUtil.isNotEmpty(condition.getValue()), columnName,
                            condition.getValue().get(0), condition.getValue().get(1));
                    break;
                case CONTAIN:
                    wrapper.nested(w -> w.apply(
                            String.format("FIND_IN_SET('%s', %s) > 0", condition.getValue().get(0), columnName)));
                    break;
                case CONTAIN_IN_OR:
                    wrapper.nested(w -> {
                        for (Object value : condition.getValue()) {
                            w.or().apply(String.format("FIND_IN_SET('%s', %s) > 0", value, columnName));
                        }
                    });
                    break;
                default:
                    break;
            }
        } else {
            String havingColumnName = condition.getProperty();
            switch (PortalConditionDict.of(condition.getRelation())) {
                case EQUAL:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " = {0}",
                            condition.getValue().get(0));
                    break;
                case NOT_EQUAL:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " != " + "{0}",
                            condition.getValue().get(0));
                    break;
                case IN:
                    if (FuncUtil.isNotEmpty(condition.getValue())) {
                        StringBuffer sql = new StringBuffer(havingColumnName + " in ( ");
                        for (int i = 0; i < condition.getValue().size(); i++) {
                            sql.append("{").append(i).append("}, ");
                        }
                        wrapper.having(sql.substring(0, sql.length() - 2) + ")", condition.getValue().toArray());
                    }
                    break;
                case NOT_IN:
                    if (FuncUtil.isNotEmpty(condition.getValue())) {
                        StringBuffer sql = new StringBuffer(havingColumnName + " not in ( ");
                        for (int i = 0; i < condition.getValue().size(); i++) {
                            sql.append("{").append(i).append("}, ");
                        }
                        wrapper.having(sql.substring(0, sql.length() - 2) + ")", condition.getValue().toArray());
                    }
                    break;
                case LIKE:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " like " + "{0} ",
                            "%" + condition.getValue().get(0) + "%");
                    break;
                case NOT_LIKE:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " not " + "like {0} ",
                            "%" + condition.getValue().get(0) + "%");
                    break;
                case GREATER:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " > {0}",
                            condition.getValue().get(0));
                    break;
                case GREATER_EQUAL:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " >= " + "{0}",
                            condition.getValue().get(0));
                    break;
                case LESS:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " < {0}",
                            condition.getValue().get(0));
                    break;
                case LESS_EQUAL:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)), havingColumnName + " <= " + "{0}",
                            condition.getValue().get(0));
                    break;
                case NULL:
                    wrapper.having(havingColumnName + " is null");
                    break;
                case NOT_NULL:
                    wrapper.having(havingColumnName + " is not null");
                    break;
                case BETWEEN:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()), havingColumnName + " between {0} and {1}",
                            condition.getValue().get(0), condition.getValue().get(1));
                    break;
                case NOT_BETWEEN:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()),
                            havingColumnName + " not between {0} and {1}", condition.getValue().get(0),
                            condition.getValue().get(1));
                    break;
                case CONTAIN:
                    wrapper.having(FuncUtil.isNotEmpty(condition.getValue()) &&
                                    FuncUtil.isNotEmpty(condition.getValue().get(0)),
                            "FIND_IN_SET({0}, " + havingColumnName + ") > 0", condition.getValue().get(0));
                    break;
                case CONTAIN_IN_OR:
                    String havingSqlFormat = "FIND_IN_SET({%d}, %s) > 0";
                    List<String> havingSqlList = new ArrayList<>();
                    if (FuncUtil.isNotEmpty(condition.getValue())) {
                        for (int i = 0; i < condition.getValue().size(); i++) {
                            havingSqlList.add(String.format(String.format(havingSqlFormat, i, havingColumnName)));
                        }
                    }
                    wrapper.having(StringUtil.joinWith(" or ", havingSqlList), condition.getValue().toArray());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 构造排序wrapper
     *
     * @param req     请求
     * @param wrapper 现有wrapper
     * @return 查询wrapper
     */

    default MPJLambdaWrapper<T> parseSort(QueryConditionReq req, MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(req.getSortList())) {
            OrderBySegmentList orderCache = new OrderBySegmentList();
            for (ISqlSegment iSqlSegment : wrapper.getExpression().getOrderBy()) {
                orderCache.add(iSqlSegment);
            }
            wrapper.getExpression().getOrderBy().clear();
            for (SortVO sortVO : req.getSortList()) {
                if (FuncUtil.isNotEmpty(sortVO.getProperty()) && FuncUtil.isNotEmpty(sortVO.getType())) {
                    switch (PortalSortDict.of(sortVO.getType())) {
                        case ASC:
                            wrapper.orderByAsc(sortVO.getProperty());
                            break;
                        case DESC:
                            wrapper.orderByDesc(sortVO.getProperty());
                            break;
                        default:
                            break;
                    }
                }
            }
            for (ISqlSegment iSqlSegment : orderCache) {
                wrapper.getExpression().getOrderBy().add(iSqlSegment);
            }
        }
        return wrapper;
    }

    /**
     * 获取数据库字段名
     *
     * @param property    字段名
     * @param aliasMap    别名表
     * @param entityClass 所属数据库entity
     * @return 字段名
     */

    default String getColumnName(String property, Map<String, String> aliasMap, Class<?> entityClass) {
        return getColumnName(property, aliasMap, entityClass, FuncUtil.isNotEmpty(aliasMap) ? "t" : "");
    }

    /**
     * 获取数据库字段名
     *
     * @param property    字段名
     * @param aliasMap    别名表
     * @param entityClass 所属数据库entity
     * @param alias       别名
     * @return 字段名
     */
    default String getColumnName(String property, Map<String, String> aliasMap, Class<?> entityClass, String alias) {
        if (FuncUtil.isNotEmpty(property)) {
            String columnName = null;
            if (FuncUtil.isNotEmpty(aliasMap)) {
                columnName = aliasMap.get(property);
            }
            if (FuncUtil.isEmpty(columnName)) {
                if (FuncUtil.isEmpty(alias)) {
                    columnName = getColumnName(property, entityClass);
                } else {
                    columnName = alias + "." + getColumnName(property, entityClass);
                }
            }
            return columnName;
        }
        return null;
    }

    default Map<String, String> parseSelectApply(Map<String, Object> selectColumnCondition,
                                                 Map<String, String> aliasMap,
                                                 Map<String, List<DynamicColumn>> selectApplyMap,
                                                 MPJLambdaWrapper<T> wrapper) {
        Map<String, String> deepCloneAliasMap = aliasMap;
        if (FuncUtil.isNotEmpty(selectApplyMap) && FuncUtil.isNotEmpty(selectColumnCondition)) {
            deepCloneAliasMap = JsonUtil.readJson(JsonUtil.toJson(aliasMap), Map.class, String.class, String.class);
            try {
                Context context = Context.enter();
                Scriptable scope = context.initStandardObjects();
                for (Map.Entry<String, Object> entry : selectColumnCondition.entrySet()) {
                    ScriptableObject.putProperty(scope, entry.getKey(), entry.getValue());
                }
                buildSelectWrapper(wrapper, deepCloneAliasMap, selectApplyMap, context, scope);
            } finally {
                Context.exit();
            }

        }
        return deepCloneAliasMap;
    }

    default void buildSelectWrapper(MPJLambdaWrapper<T> wrapper, Map<String, String> aliasMap,
                                    Map<String, List<DynamicColumn>> selectApplyMap, Context context,
                                    Scriptable scope) {
        if (FuncUtil.isNotEmpty(selectApplyMap)) {
            String complexScriptFormat = "function func() {%s} func();";
            String simpleScriptFormat = "function func() {return '%s';} func();";
            for (Map.Entry<String, List<DynamicColumn>> entry : selectApplyMap.entrySet()) {
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    StringBuilder select = new StringBuilder();
                    for (DynamicColumn column : entry.getValue()) {
                        try {
                            if (Context.toBoolean(context.evaluateString(scope, column.getCondition(), "", 1, null))) {
                                String format = column.isComplex() ? complexScriptFormat : simpleScriptFormat;
                                String scriptStr = column.isComplex() ? column.getScript() : column.getScript()
                                        .replace("'", "\\'");
                                String script = String.format(format, scriptStr);
                                String s = Context.toString(context.evaluateString(scope, script, "", 1, null));
                                if (FuncUtil.isNotEmpty(s)) {
                                    select.append(column.getPrefix()).append(s).append(column.getSuffix());
                                }
                            }
                        } catch (Exception e) {
                            LoggerFactory.getLogger(this.getClass()).error(e.getMessage(), e);
                        }
                    }
                    if (FuncUtil.isEmpty(select.toString())) {
                        select = new StringBuilder(" null ");
                    }
                    aliasMap.put(entry.getKey(), "(" + select + ")");
                    wrapper.getSelectColum().add(new SelectString(
                            StringUtil.joinWith(" as ", select.toString(), "'" + entry.getKey() + "'"),
                            wrapper.getAlias()));
                }

            }
        }
    }

    default void parseGeneralQuery(List<ConditionVO> conditionList, Map<String, String> aliasMap,
                                   Collection<String> havingFields, MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(conditionList)) {
            for (ConditionVO condition : conditionList) {
                if (FuncUtil.isNotEmpty(condition.getRelation())) {
                    buildQueryWrapper(wrapper, aliasMap, havingFields, condition);
                }
            }
        }
    }

    default void parseAdvancedQuery(AdvancedQuery req, Map<String, String> aliasMap, MPJLambdaWrapper<T> wrapper) {
        parseAdvancedQuery(req, aliasMap, wrapper, SqlConstant.AND);
    }

    default void parseAdvancedQuery(AdvancedQuery req, Map<String, String> aliasMap, MPJLambdaWrapper<T> wrapper,
                                    String andOr) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            if (req.getConditionList().size() == 1) {
                wrapper.and(w -> {
                    parseAdvancedQuery(req.getConditionList().get(0), aliasMap, w, SqlConstant.AND);
                });
            } else if (req.getConditionList().size() > 1) {
                for (AdvancedQuery advancedQuery : req.getConditionList()) {
                    if (StringUtil.convertSwitch(req.getAndOr())) {
                        wrapper.or(w -> {
                            parseAdvancedQuery(advancedQuery, aliasMap, w, SqlConstant.OR);
                        });
                    } else {
                        wrapper.and(w -> {
                            parseAdvancedQuery(advancedQuery, aliasMap, w, SqlConstant.AND);
                        });
                    }
                }
            }
        } else {
            if (FuncUtil.isNotEmpty(req.getProperty()) && FuncUtil.isNotEmpty(req.getRelation())) {
                if (req.getRelation().equals(PortalConditionDict.NULL.getValue()) ||
                        req.getRelation().equals(PortalConditionDict.NOT_NULL.getValue())) {
                    buildQueryWrapper(wrapper, aliasMap, null, req);
                    return;
                } else {
                    if (FuncUtil.isNotEmpty(req.getValue())) {
                        if (FuncUtil.isNotEmpty(req.getValue().get(0))) {
                            buildQueryWrapper(wrapper, aliasMap, null, req);
                        } else {
                            if (FuncUtil.equals(andOr, SqlConstant.AND)) {
                                wrapper.apply("1 = 1");
                            } else {
                                wrapper.apply("1 = 0");
                            }
                        }
                        return;
                    }
                }
                buildQueryWrapper(wrapper, aliasMap, null, req);
            }
            if (FuncUtil.equals(andOr, SqlConstant.AND)) {
                wrapper.apply("1 = 1");
            } else {
                wrapper.apply("1 = 0");
            }
        }
    }

    default MPJLambdaWrapper<T> parseAdvancedQuery(AdvancedQueryReq req, Map<String, String> aliasMap,
                                                   MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(req.getCondition())) {
            parseAdvancedQuery(req.getCondition(), aliasMap, wrapper);
        }
        parseSort(req, aliasMap, wrapper);
        return wrapper;
    }

    default MPJLambdaWrapper<T> parseSort(AdvancedQueryReq req, Map<String, String> aliasMap,
                                          MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(req.getSortList())) {
            return parseSort(req.getSortList(), aliasMap, wrapper);
        }
        return wrapper;
    }

    default MPJLambdaWrapper<T> parseSort(List<SortVO> sortList, Map<String, String> aliasMap,
                                          MPJLambdaWrapper<T> wrapper) {
        if (FuncUtil.isNotEmpty(sortList)) {
            OrderBySegmentList orderCache = new OrderBySegmentList();
            for (ISqlSegment iSqlSegment : wrapper.getExpression().getOrderBy()) {
                orderCache.add(iSqlSegment);
            }
            wrapper.getExpression().getOrderBy().clear();
            for (SortVO sortVO : sortList) {
                if (FuncUtil.isNotEmpty(sortVO.getProperty()) && FuncUtil.isNotEmpty(sortVO.getType())) {
                    String columnName = getColumnName(sortVO.getProperty(), aliasMap, wrapper.getEntityClass());
                    switch (PortalSortDict.of(sortVO.getType())) {
                        case ASC:
                            wrapper.orderByAsc(columnName);
                            break;
                        case DESC:
                            wrapper.orderByDesc(columnName);
                            break;
                        default:
                            break;
                    }
                }
            }
            for (ISqlSegment iSqlSegment : orderCache) {
                String[] s = iSqlSegment.getSqlSegment().split(" ");
                wrapper.orderBy(true, s[1].equals("ASC"), s[0]);
            }
        }
        return wrapper;
    }
}
