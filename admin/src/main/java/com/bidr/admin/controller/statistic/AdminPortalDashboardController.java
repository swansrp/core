package com.bidr.admin.controller.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.service.statistic.AdminPortalDashboardService;
import com.bidr.admin.vo.statistic.DashboardVO;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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

    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    @RequestMapping(value = "/order/update", method = RequestMethod.POST)
    public void updateOrder(@RequestBody List<IdOrderReqVO> idOrderReqVOList) {
        List<SysPortalDashboard> entityList = new ArrayList<>();
        String operator = AccountContext.getOperator();
        if (CollectionUtils.isNotEmpty(idOrderReqVOList)) {
            for (IdOrderReqVO req : idOrderReqVOList) {
                SysPortalDashboard entity = new SysPortalDashboard();
                entity.setStatisticId((Long) req.getId());
                entity.setCustomerNumber(operator);
                entityList.add(entity);
            }
            getRepo().updateBatchById(entityList);
        }
        Resp.notice("变更顺序成功");
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public List<DashboardVO> getPersonalStatistic(String tableId) {
        return adminPortalDashboardService.getPersonalDashboard(tableId);
    }

    @RequestMapping(value = "/common", method = RequestMethod.POST)
    public List<DashboardVO> addCommonStatistic(@RequestBody String[] ids) {
        return adminPortalDashboardService.addCommonStatistic(ids);
    }

    @RequestMapping(value = "/personal", method = RequestMethod.POST)
    public List<DashboardVO> addPersonalStatistic(@RequestBody String[] ids) {
        return adminPortalDashboardService.addPersonalStatistic(ids);
    }
}
