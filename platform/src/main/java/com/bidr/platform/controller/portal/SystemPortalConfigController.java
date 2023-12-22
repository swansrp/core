package com.bidr.platform.controller.portal;

import com.bidr.platform.service.portal.PortalService;
import com.bidr.platform.vo.portal.PortalReq;
import com.bidr.platform.vo.portal.PortalWithColumnsRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: SystemPortalConfigController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:01
 */
@Api(tags = "系统基础 - 快速后台管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/config"})
public class SystemPortalConfigController {

    private final PortalService portalService;

    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取后台管理配置")
    public PortalWithColumnsRes getPortal(PortalReq req) {
        return portalService.getPortalWithColumnsConfig(req);
    }
}
