package com.bidr.kernel.controller.inf;

import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminStatisticControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:53
 */
public interface AdminStatisticControllerInf<ENTITY, VO> extends AdminBaseControllerInf<ENTITY, VO> {

    /**
     * 统计个数
     *
     * @param req 查询条件
     * @return 统计个数数据
     */

    Long generalCount(@RequestBody QueryConditionReq req);

    /**
     * 统计个数
     *
     * @param req 高级查询条件
     * @return 统计个数数据
     */
    Long advancedCount(@RequestBody AdvancedQueryReq req);

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 汇总数据
     */
    Map<String, Object> generalSummary(@RequestBody GeneralSummaryReq req);

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 数据
     */
    Map<String, Object> advancedSummary(@RequestBody AdvancedSummaryReq req);

    /**
     * 指标统计
     *
     * @param req 查询条件
     * @return 指标统计数据
     */
    List<StatisticRes> generalStatistic(@RequestBody GeneralStatisticReq req);


    /**
     * 指标统计
     *
     * @param req 高级查询条件
     * @return 指标统计数据
     */
    List<StatisticRes> advancedStatistic(@RequestBody AdvancedStatisticReq req);

    default MPJLambdaWrapper<ENTITY> buildGeneralFromWapper(QueryConditionReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            Map<String, String> aliasMap = getRepo().parseSelectApply(req.getConditionList(),
                    getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
            if (FuncUtil.isNotEmpty(req.getConditionList())) {
                getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                        from);
            } else {
                getRepo().parseGeneralQuery(req.getConditionList(), null, null, from);
            }
        }
        return from;
    }

    default MPJLambdaWrapper<ENTITY> buildAdvancedFromWapper(AdvancedQueryReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectApplyList(),
                    getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
            if (FuncUtil.isNotEmpty(req.getCondition())) {
                getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
            } else {
                getRepo().parseAdvancedQuery(req.getCondition(), null, from);
            }
        }
        return from;
    }

    /**
     * 统计个数
     *
     * @param req 查询条件
     * @return 统计个数数据
     */
    default Long countByGeneralReq(QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> buildGeneralFromWapper(req, from));
        return getRepo().selectJoinCount(wrapper);
    }

    /**
     * 统计个数
     *
     * @param req 高级查询条件
     * @return 统计个数数据
     */
    default Long countByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> buildAdvancedFromWapper(req, from));
        return getRepo().selectJoinCount(wrapper);
    }

    /**
     * 汇总
     *
     * @param req 查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByGeneralReq(GeneralSummaryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(req.getColumns())) {
            for (String column : req.getColumns()) {
                wrapper.getSelectColum()
                        .add(new SelectString(String.format("sum(%s) as %s", column, column), wrapper.getAlias()));
            }
        }
        wrapper.from(from -> buildGeneralFromWapper(req, from));
        return getRepo().selectJoinMap(wrapper);
    }

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByAdvancedReq(AdvancedSummaryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(req.getColumns())) {
            for (String column : req.getColumns()) {
                wrapper.getSelectColum()
                        .add(new SelectString(String.format("sum(%s) as %s", column, column), wrapper.getAlias()));
            }
        }
        wrapper.from(from -> buildAdvancedFromWapper(req, from));
        return getRepo().selectJoinMap(wrapper);
    }

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
        if (FuncUtil.isNotEmpty(req.getMetricColumn())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildGeneralFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
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
        if (FuncUtil.isNotEmpty(req.getMetricColumn())) {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricColumn(), req.getStatisticColumn(),
                    req.getSort());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getRepo().selectJoinList(StatisticRes.class, wrapper);
        } else {
            MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getMetricCondition(),
                    req.getStatisticColumn());
            wrapper.from(from -> buildAdvancedFromWapper(req, from));
            return getStatisticRes(wrapper, req.getSort());
        }
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

    default String parseStatisticSelect(AdvancedQuery query, String statisticColumn) {
        // todo 解析 case when
        return null;
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(Map<String, AdvancedQuery> conditionMap,
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
}
