package com.bidr.admin.manage.role.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.manage.role.service.AcRolePortalService;
import com.bidr.admin.manage.role.vo.AcRoleVO;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AcRolePortalController
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/25 14:16
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "系统管理 - 角色管理")
@AdminPortal
@RequestMapping(value = "/web/admin/role")
public class AcRolePortalController extends BaseAdminOrderController<AcRole, AcRoleVO> {

    private final AcRolePortalService acRolePortalService;

    @Override
    public PortalCommonService<AcRole, AcRoleVO> getPortalService() {
        return acRolePortalService;
    }

    @Override
    protected SFunction<AcRole, ?> id() {
        return AcRole::getRoleId;
    }

    @Override
    protected SFunction<AcRole, Integer> order() {
        return AcRole::getDisplayOrder;
    }
}
