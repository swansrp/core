package com.bidr.admin.controller.statistic;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalIndicator;
import com.bidr.admin.service.statistic.AdminPortalIndicatorService;
import com.bidr.admin.vo.statistic.PortalIndicatorVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminPortalIndicatorService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Api(tags = "系统基础 - 快速后台管理 - 指标配置")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/indicator"})
public class AdminPortalIndicatorController extends BaseAdminOrderController<SysPortalIndicator, PortalIndicatorVO> {

    private final AdminPortalIndicatorService adminPortalIndicatorService;

    @Override
    public PortalCommonService<SysPortalIndicator, PortalIndicatorVO> getPortalService() {
        return adminPortalIndicatorService;
    }

    @Override
    protected SFunction<SysPortalIndicator, ?> id() {
        return SysPortalIndicator::getId;
    }

    @Override
    protected SFunction<SysPortalIndicator, Integer> order() {
        return SysPortalIndicator::getDisplayOrder;
    }
}
