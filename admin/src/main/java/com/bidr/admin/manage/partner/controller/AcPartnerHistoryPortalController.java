package com.bidr.admin.manage.partner.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.manage.partner.service.AcPartnerHistoryPortalService;
import com.bidr.admin.manage.partner.vo.AcPartnerHistoryVO;
import com.bidr.admin.manage.role.vo.AcRoleVO;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AcPartnerHistoryPortalController
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 15:33
 */

@RestController
@RequiredArgsConstructor
@Api(tags = "系统管理 - 角色管理")
@AdminPortal
@RequestMapping(value = "/web/admin/partner/history")
public class AcPartnerHistoryPortalController extends BaseAdminController<AcPartnerHistory, AcPartnerHistoryVO> {

    private final AcPartnerHistoryPortalService acPartnerHistoryPortalService;

    @Override
    public PortalCommonService<AcPartnerHistory, AcPartnerHistoryVO> getPortalService() {
        return acPartnerHistoryPortalService;
    }
}