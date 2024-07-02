package com.bidr.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.service.associate.PortalAssociateService;
import com.bidr.admin.vo.PortalAssociateRes;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminPortalAssociateController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/7/2 13:37
 */
@Api(tags = "系统基础 - 快速后台管理 - 关联实体配置")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/associate"})
public class AdminPortalAssociateController extends BaseAdminOrderController<SysPortalAssociate, PortalAssociateRes> {

    private final PortalAssociateService portalAssociateService;

    @Override
    public PortalCommonService<SysPortalAssociate, PortalAssociateRes> getPortalService() {
        return portalAssociateService;
    }

    @Override
    protected SFunction<SysPortalAssociate, ?> id() {
        return SysPortalAssociate::getId;
    }

    @Override
    protected SFunction<SysPortalAssociate, Integer> order() {
        return SysPortalAssociate::getDisplayOrder;
    }
}
