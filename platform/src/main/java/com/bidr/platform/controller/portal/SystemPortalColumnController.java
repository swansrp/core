package com.bidr.platform.controller.portal;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.vo.portal.PortalColumnRes;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: SystemPortalColumnController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/18 17:06
 */
@Api(tags = "系统基础 - 快速后台管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/portalColumn"})
public class SystemPortalColumnController extends BaseAdminController<SysPortalColumn, PortalColumnRes> {
}
