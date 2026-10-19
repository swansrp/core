package com.bidr.admin.controller.statistic;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.service.statistic.AdminPortalIndicatorGroupService;
import com.bidr.admin.service.statistic.IndicatorService;
import com.bidr.admin.vo.statistic.IndicatorRes;
import com.bidr.admin.vo.statistic.PortalIndicatorGroupVO;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final IndicatorService indicatorService;

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

    @RequestMapping(path = {"/indicator/tree"}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取指定表格的指标配置")
    public List<IndicatorRes> getPortal(String tableId) {
        return indicatorService.getIndicator(tableId);
    }
}
