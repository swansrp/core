package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * Title: PortalSelectRepo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 16:20
 */
public interface PortalSelectRepo {

    /**
     * 构造通用查询wrapper
     *
     * @param req     请求
     * @param wrapper 现有查询条件
     * @return 查询wrapper
     */

    default QueryWrapper parseQueryCondition(QueryConditionReq req, QueryWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                buildQueryWrapper(wrapper, condition);
            }
        }
        parseSort(req, wrapper);
        return wrapper;
    }

    default void buildQueryWrapper(QueryWrapper wrapper, ConditionVO condition) {
        formatDateValue(condition);
        String columnName = getColumnName(condition, wrapper.getEntityClass());
        switch (PortalConditionDict.of(condition.getRelation())) {
            case EQUAL:
                wrapper.eq(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case NOT_EQUAL:
                wrapper.ne(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case IN:
                wrapper.in(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                break;
            case NOT_IN:
                wrapper.notIn(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue());
                break;
            case LIKE:
                wrapper.like(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case NOT_LIKE:
                wrapper.notLike(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case GREATER:
                wrapper.gt(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case GREATER_EQUAL:
                wrapper.ge(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case LESS:
                wrapper.lt(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
                break;
            case LESS_EQUAL:
                wrapper.le(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0));
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
                wrapper.notBetween(FuncUtil.isNotEmpty(condition.getValue()), columnName, condition.getValue().get(0),
                        condition.getValue().get(1));
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

    default QueryWrapper parseSort(QueryConditionReq req, QueryWrapper wrapper) {
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
    default MPJLambdaWrapper parseQueryCondition(QueryConditionReq req, MPJLambdaWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                formatDateValue(condition);
                switch (PortalConditionDict.of(condition.getRelation())) {
                    case EQUAL:
                        wrapper.eq(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case NOT_EQUAL:
                        wrapper.ne(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case IN:
                        wrapper.in(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NOT_IN:
                        wrapper.notIn(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case LIKE:
                        wrapper.like(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case NOT_LIKE:
                        wrapper.notLike(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case GREATER:
                        wrapper.gt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case GREATER_EQUAL:
                        wrapper.ge(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case LESS:
                        wrapper.lt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case LESS_EQUAL:
                        wrapper.le(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0));
                        break;
                    case NULL:
                        wrapper.isNull(condition.getProperty());
                        break;
                    case NOT_NULL:
                        wrapper.isNotNull(condition.getProperty());
                        break;
                    case BETWEEN:
                        wrapper.between(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0), condition.getValue().get(1));
                        break;
                    case NOT_BETWEEN:
                        wrapper.notBetween(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue().get(0), condition.getValue().get(1));
                        break;
                    default:
                        break;
                }
            }
        }
        return wrapper;
    }

    /**
     * 构造排序wrapper
     *
     * @param req     请求
     * @param wrapper 现有wrapper
     * @return 查询wrapper
     */

    default MPJLambdaWrapper parseSort(QueryConditionReq req, MPJLambdaWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getSortList())) {
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
        }
        return wrapper;
    }

    default void parseAdvanceQuery(AdvanceQuery req, QueryWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getAndOr())) {
            wrapper.and(w -> parseAdvanceQuery(req.getConditionA(), (QueryWrapper) w));
            if (StringUtil.convertSwitch(req.getAndOr())) {
                wrapper.or(w -> parseAdvanceQuery(req.getConditionB(), (QueryWrapper) w));
            } else {
                wrapper.and(w -> parseAdvanceQuery(req.getConditionB(), (QueryWrapper) w));
            }
        } else {
            buildQueryWrapper(wrapper, req);
        }
    }

    default QueryWrapper parseAdvanceQuery(AdvanceQueryReq req, QueryWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getCondition())) {
            parseAdvanceQuery(req.getCondition(), wrapper);
        }
        parseSort(req, wrapper);
        return wrapper;
    }

    default QueryWrapper parseSort(AdvanceQueryReq req, QueryWrapper wrapper) {
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
}
