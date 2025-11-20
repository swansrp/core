package com.bidr.forge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.service.dataset.PortalDatasetService;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.*;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;

/**
 * Title: AdminDatasetController
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 8:44
 */
@RequiredArgsConstructor
public class AdminDatasetController {
    private final PortalDatasetService portalDatasetService;

    @ApiIgnore
    @ApiOperation("通用查询数据")
    @RequestMapping(value = "/general/query", method = RequestMethod.POST)
    public Page<Map<String, Object>> generalQuery(@RequestBody QueryConditionReq req, String tableId) {
        return portalDatasetService.generalQuery(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("通用查询数据(不分页)")
    @RequestMapping(value = "/general/select", method = RequestMethod.POST)
    public List<Map<String, Object>> generalSelect(@RequestBody QueryConditionReq req, String tableId) {
        return portalDatasetService.generalSelect(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("高级查询数据")
    @RequestMapping(value = "/advanced/query", method = RequestMethod.POST)
    public Page<Map<String, Object>> advancedQuery(@RequestBody AdvancedQueryReq req, String tableId) {
        return portalDatasetService.advancedQuery(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("高级查询数据(不分页)")
    @RequestMapping(value = "/advanced/select", method = RequestMethod.POST)
    public List<Map<String, Object>> advancedSelect(@RequestBody AdvancedQueryReq req, String tableId) {
        return portalDatasetService.advancedSelect(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("个数统计")
    @RequestMapping(value = "/general/count", method = RequestMethod.POST)
    public Long generalCount(@RequestBody QueryConditionReq req, String tableId) {
        return portalDatasetService.countByGeneralReq(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("统计个数")
    @RequestMapping(value = "/advanced/count", method = RequestMethod.POST)
    public Long advancedCount(@RequestBody AdvancedQueryReq req, String tableId) {
        return portalDatasetService.countByAdvancedReq(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("汇总")
    @RequestMapping(value = "/general/summary", method = RequestMethod.POST)
    public Map<String, Object> generalSummary(@RequestBody GeneralSummaryReq req, String tableId) {
        return portalDatasetService.summaryByGeneralReq(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("汇总")
    @RequestMapping(value = "/advanced/summary", method = RequestMethod.POST)
    public Map<String, Object> advancedSummary(@RequestBody AdvancedSummaryReq req, String tableId) {
        return portalDatasetService.summaryByAdvancedReq(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("指标统计")
    @RequestMapping(value = "/general/statistic", method = RequestMethod.POST)
    public List<StatisticRes> generalStatistic(@RequestBody GeneralStatisticReq req, String tableId) {
        return portalDatasetService.statisticByGeneralReq(req, tableId);
    }

    @ApiIgnore
    @ApiOperation("指标统计")
    @RequestMapping(value = "/advanced/statistic", method = RequestMethod.POST)
    public List<StatisticRes> advancedStatistic(@RequestBody AdvancedStatisticReq req, String tableId) {
        return portalDatasetService.statisticByAdvancedReq(req, tableId);
    }
}
