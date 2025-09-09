package com.bidr.admin.controller.statistic;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.service.statistic.AdminPortalIndicatorGroupService;
import com.bidr.admin.vo.statistic.PortalIndicatorGroupVO;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminPortalIndicatorGroupService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Api(tags = "系统基础 - 快速后台管理 - 指标配置组")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/indicator/group"})
public class AdminPortalIndicatorGroupController extends BaseAdminTreeController<SysPortalIndicatorGroup, PortalIndicatorGroupVO> {

    private final AdminPortalIndicatorGroupService adminPortalIndicatorGroupService;

    @Override
    public PortalCommonService<SysPortalIndicatorGroup, PortalIndicatorGroupVO> getPortalService() {
        return adminPortalIndicatorGroupService;
    }

    @Override
    protected SFunction<SysPortalIndicatorGroup, ?> id() {
        return SysPortalIndicatorGroup::getId;
    }

    @Override
    protected SFunction<SysPortalIndicatorGroup, Integer> order() {
        return SysPortalIndicatorGroup::getDisplayOrder;
    }

    @Override
    protected SFunction<SysPortalIndicatorGroup, ?> pid() {
        return SysPortalIndicatorGroup::getPid;
    }

    @Override
    protected SFunction<SysPortalIndicatorGroup, String> name() {
        return SysPortalIndicatorGroup::getName;
    }
}
