package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.vo.portal.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * Title: AdminStatisticControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:53
 */
public interface AdminStatisticControllerInf<ENTITY, VO> extends AdminStatisticCountControllerInf<ENTITY, VO>, AdminStatisticSummaryControllerInf<ENTITY, VO>, AdminStatisticMetricControllerInf<ENTITY, VO> {

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


}
