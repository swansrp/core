package com.bidr.admin.manage.account.controller;

import com.bidr.admin.manage.account.service.AdminAccountServiceImpl;
import com.bidr.admin.manage.account.vo.AccountVO;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminAccountController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/8/14 16:27
 */
@Api(tags = "系统管理 - 白名单账户管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/account"})
public class AdminAccountController extends BaseAdminController<AcAccount, AccountVO> {
    private final AdminAccountServiceImpl accountService;

    @Override
    public PortalCommonService<AcAccount, AccountVO> getPortalService() {
        return accountService;
    }
}
