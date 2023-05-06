package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

/**
 * Title: PortalSelectRepo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/05 16:20
 */
public interface PortalSelectRepo {
    default MPJLambdaWrapper parseQueryCondition(QueryConditionReq req, MPJLambdaWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                switch (PortalConditionDict.of(condition.getRelation())) {
                    case EQUAL:
                        wrapper.eq(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NOT_EQUAL:
                        wrapper.ne(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case IN:
                        wrapper.in(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList());
                        break;
                    case NOT_IN:
                        wrapper.notIn(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList());
                        break;
                    case LIKE:
                        wrapper.like(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NOT_LIKE:
                        wrapper.notLike(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case GREATER:
                        wrapper.gt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case GREATER_EQUAL:
                        wrapper.ge(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case LESS:
                        wrapper.lt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case LESS_EQUAL:
                        wrapper.le(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NULL:
                        wrapper.isNull(condition.getProperty());
                        break;
                    case NOT_NULL:
                        wrapper.isNotNull(condition.getProperty());
                        break;
                    case BETWEEN:
                        wrapper.between(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList().get(0), condition.getValueList().get(1));
                        break;
                    case NOT_BETWEEN:
                        wrapper.notBetween(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList().get(0), condition.getValueList().get(1));
                        break;
                    default:
                        break;
                }
            }
        }
        return wrapper;
    }

    default MPJLambdaWrapper parseSort(QueryConditionReq req, MPJLambdaWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getSort())) {
            if (FuncUtil.isNotEmpty(req.getSort().getProperty())) {
                switch (PortalSortDict.of(req.getSort().getType())) {
                    case ASC:
                        wrapper.orderByAscStr(req.getSort().getProperty());
                        break;
                    case DESC:
                        wrapper.orderByDescStr(req.getSort().getProperty());
                        break;
                    default:
                        break;
                }
            }
        }
        return wrapper;
    }


    default QueryWrapper parseQueryCondition(QueryConditionReq req, QueryWrapper wrapper) {
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                switch (PortalConditionDict.of(condition.getRelation())) {
                    case EQUAL:
                        wrapper.eq(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NOT_EQUAL:
                        wrapper.ne(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case IN:
                        wrapper.in(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList());
                        break;
                    case NOT_IN:
                        wrapper.notIn(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList());
                        break;
                    case LIKE:
                        wrapper.like(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NOT_LIKE:
                        wrapper.notLike(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case GREATER:
                        wrapper.gt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case GREATER_EQUAL:
                        wrapper.ge(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case LESS:
                        wrapper.lt(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case LESS_EQUAL:
                        wrapper.le(FuncUtil.isNotEmpty(condition.getValue()), condition.getProperty(),
                                condition.getValue());
                        break;
                    case NULL:
                        wrapper.isNull(condition.getProperty());
                        break;
                    case NOT_NULL:
                        wrapper.isNotNull(condition.getProperty());
                        break;
                    case BETWEEN:
                        wrapper.between(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList().get(0), condition.getValueList().get(1));
                        break;
                    case NOT_BETWEEN:
                        wrapper.notBetween(FuncUtil.isNotEmpty(condition.getValueList()), condition.getProperty(),
                                condition.getValueList().get(0), condition.getValueList().get(1));
                        break;
                    default:
                        break;
                }
            }
        }
        if (FuncUtil.isNotEmpty(req.getSort().getProperty())) {
            switch (PortalSortDict.of(req.getSort().getType())) {
                case ASC:
                    wrapper.orderByAsc(req.getSort().getProperty());
                    break;
                case DESC:
                    wrapper.orderByDesc(req.getSort().getProperty());
                    break;
                default:
                    break;
            }
        }
        return wrapper;
    }
}
