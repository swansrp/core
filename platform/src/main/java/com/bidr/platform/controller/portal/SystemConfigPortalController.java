package com.bidr.platform.controller.portal;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.platform.dao.entity.SysConfig;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: SystemConfigPortalController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/18 16:38
 */
@Api(tags = "系统基础 - 快速后台管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/sysConfig"})
public class SystemConfigPortalController extends BaseAdminController<SysConfig, SysConfig> {
}
