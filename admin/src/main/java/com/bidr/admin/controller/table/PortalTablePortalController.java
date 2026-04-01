package com.bidr.admin.controller.table;

import com.bidr.admin.dao.entity.SysPortalTable;
import com.bidr.admin.service.table.SysPortalTablePortalService;
import com.bidr.admin.vo.PortalTableVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表格展示配置控制器
 *
 * @author Sharp
 */
@Api(tags = "系统基础 - 表格展示配置")
@RestController
@RequiredArgsConstructor
@AdminPortal
@RequestMapping(value = "/web/admin/portal/table")
public class PortalTablePortalController extends BaseAdminController<SysPortalTable, PortalTableVO> {

    private final SysPortalTablePortalService sysPortalTablePortalService;

    @Override
    public PortalCommonService<SysPortalTable, PortalTableVO> getPortalService() {
        return sysPortalTablePortalService;
    }
}
