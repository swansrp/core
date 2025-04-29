package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.GeneralStatisticReq;
import com.bidr.kernel.vo.portal.StatisticRes;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminStatisticCountControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:43
 */

public interface AdminStatisticMetricControllerInf<ENTITY, VO> extends AdminStatisticBaseControllerInf<ENTITY, VO> {
    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByGeneralReq(GeneralStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        }
    }

    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByAdvancedReq(AdvancedStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        }
    }

    default String parseStatisticSelect(AdvancedQuery query, String statisticColumn) {
        // todo 解析 case when
        return null;
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<String> metricColumn,
                                                           Map<String, AdvancedQuery> conditionMap,
                                                           String statisticColumn) {
        Validator.assertNotEmpty(conditionMap, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        for (Map.Entry<String, AdvancedQuery> entry : conditionMap.entrySet()) {
            if (FuncUtil.isNotEmpty(statisticColumn)) {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("sum(%s) as %s", parseStatisticSelect(entry.getValue(), statisticColumn),
                                entry.getKey()), wrapper.getAlias()));
            } else {
                wrapper.getSelectColum().add(new SelectString(
                        String.format("count(%s) as %s", parseStatisticSelect(entry.getValue(), statisticColumn),
                                entry.getKey()), wrapper.getAlias()));
            }
        }
        return wrapper;
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(List<String> metricColumn, String statisticColumn,
                                                           Integer sort) {
        String concatJoinStr = ", ',', ";
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(statisticColumn)) {
            wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as %s", statisticColumn,
                    LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)), wrapper.getAlias()));
        } else {
            wrapper.getSelectColum().add(new SelectString(
                    String.format("count(1) as %s", LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)),
                    wrapper.getAlias()));
        }
        Validator.assertNotEmpty(metricColumn, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");
        StringBuilder sql = new StringBuilder("CONCAT(");

        for (String column : metricColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                sql.append("IFNULL(").append(column).append(", 'NULL')").append(concatJoinStr);
            }
        }
        String s = sql.substring(0, sql.length() - concatJoinStr.length()) + ")";
        wrapper.getSelectColum().add(new SelectString(
                String.format("%s as %s", s, LambdaUtil.getFieldNameByGetFunc(StatisticRes::getMetric)),
                wrapper.getAlias()));
        for (String column : metricColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                wrapper.groupBy(column);
            }
        }
        if (FuncUtil.isNotEmpty(sort)) {
            switch (PortalSortDict.of(sort)) {
                case ASC:
                    wrapper.orderByAsc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                    break;
                case DESC:
                    wrapper.orderByDesc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                    break;
                default:
                    break;
            }
        } else {
            for (String column : metricColumn) {
                if (FuncUtil.isNotEmpty(column)) {
                    wrapper.orderByAsc(column);
                }
            }
        }
        return wrapper;
    }

    default List<StatisticRes> getStatisticRes(MPJLambdaWrapper<ENTITY> wrapper, Integer sort) {
        Map<String, Object> map = getRepo().selectJoinMap(wrapper);
        List<StatisticRes> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(map)) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    resList.add(new StatisticRes(entry.getKey(), new BigDecimal(entry.getValue().toString())));
                } else {
                    resList.add(new StatisticRes(entry.getKey(), BigDecimal.ZERO));
                }
            }
        }
        if (FuncUtil.isNotEmpty(sort)) {
            switch (PortalSortDict.of(sort)) {
                case ASC:
                    resList.sort(Comparator.comparing(StatisticRes::getStatistic));
                    break;
                case DESC:
                    resList.sort((o1, o2) -> o2.getStatistic().compareTo(o1.getStatistic()));
                    break;
                default:
                    break;
            }
        }
        return resList;
    }
}
