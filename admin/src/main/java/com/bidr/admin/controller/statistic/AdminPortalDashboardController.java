package com.bidr.admin.controller.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.service.statistic.AdminPortalDashboardService;
import com.bidr.admin.vo.statistic.DashboardVO;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
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
@Api(tags = "系统基础 - 快速后台管理 - 仪表盘配置")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/dashboard"})
public class AdminPortalDashboardController extends BaseAdminController<SysPortalDashboard, DashboardVO> {

    private final AdminPortalDashboardService adminPortalDashboardService;

    @Override
    public PortalCommonService<SysPortalDashboard, DashboardVO> getPortalService() {
        return adminPortalDashboardService;
    }

    @RequestMapping(value = "/personal", method = RequestMethod.GET)
    public List<DashboardVO> getPersonalStatistic(String tableId) {
        return adminPortalDashboardService.getPersonalDashboard(tableId);
    }

    @RequestMapping(value = "/common", method = RequestMethod.GET)
    public List<DashboardVO> getCommonStatistic(String tableId) {
        return adminPortalDashboardService.getCommonDashboard(tableId);
    }

    @RequestMapping(value = "/personal", method = RequestMethod.POST)
    public void addPersonalStatistic(@RequestBody List<DashboardVO> dashboardList, String tableId) {
        adminPortalDashboardService.addPersonStatistic(dashboardList, tableId);
        Resp.notice("添加个人图表成功");
    }

    @RequestMapping(value = "/common", method = RequestMethod.POST)
    public void addCommonStatistic(@RequestBody List<DashboardVO> dashboardList, String tableId) {
        adminPortalDashboardService.addCommonStatistic(dashboardList, tableId);
        Resp.notice("添加通用图表成功");
    }
}
