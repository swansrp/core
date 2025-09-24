package com.bidr.admin.controller.statistic;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.service.statistic.AdminPortalDashboardStatisticService;
import com.bidr.admin.vo.statistic.DashboardStatisticRes;
import com.bidr.admin.vo.statistic.DashboardStatisticVO;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AdminPortalIndicatorService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Api(tags = "系统基础 - 快速后台管理 - 仪表盘数据配置")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/dashboard/statistic"})
public class AdminPortalDashboardStatisticController extends BaseAdminTreeController<SysPortalDashboardStatistic, DashboardStatisticVO> {

    private final AdminPortalDashboardStatisticService adminPortalDashboardStatisticService;

    @Override
    public PortalCommonService<SysPortalDashboardStatistic, DashboardStatisticVO> getPortalService() {
        return adminPortalDashboardStatisticService;
    }

    @Override
    protected SFunction<SysPortalDashboardStatistic, ?> id() {
        return SysPortalDashboardStatistic::getId;
    }

    @Override
    protected SFunction<SysPortalDashboardStatistic, Integer> order() {
        return SysPortalDashboardStatistic::getOrder;
    }

    @Override
    protected SFunction<SysPortalDashboardStatistic, ?> pid() {
        return SysPortalDashboardStatistic::getPid;
    }

    @Override
    protected SFunction<SysPortalDashboardStatistic, String> name() {
        return SysPortalDashboardStatistic::getTitle;
    }

    @RequestMapping(value = "/common", method = RequestMethod.GET)
    public List<DashboardStatisticRes> getCommonStatistic(String tableId) {
        List<DashboardStatisticVO> list = adminPortalDashboardStatisticService.getCommonStatistic(tableId);
        return ReflectionUtil.buildTree(DashboardStatisticRes::setChildren, list, DashboardStatisticVO::getId,
                DashboardStatisticVO::getPid);
    }

    @RequestMapping(value = "/personal", method = RequestMethod.GET)
    public List<DashboardStatisticRes> getPersonalStatistic(String tableId) {
        List<DashboardStatisticVO> list = adminPortalDashboardStatisticService.getPersonalStatistic(tableId);
        return ReflectionUtil.buildTree(DashboardStatisticRes::setChildren, list, DashboardStatisticVO::getId,
                DashboardStatisticVO::getPid);
    }
}
