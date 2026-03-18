package com.bidr.admin.controller.table;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalTableFilter;
import com.bidr.admin.service.table.SysPortalTableFilterPortalService;
import com.bidr.admin.vo.PortalTableFilterVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表格报表筛选项控制器（支持排序）
 *
 * @author Sharp
 */
@Api(tags = "表格报表筛选项")
@RestController
@RequiredArgsConstructor
@AdminPortal
@RequestMapping(value = "/web/admin/portal/table/filter")
public class PortalTableFilterPortalController extends BaseAdminOrderController<SysPortalTableFilter, PortalTableFilterVO> {

    private final SysPortalTableFilterPortalService sysPortalTableFilterPortalService;

    @Override
    public PortalCommonService<SysPortalTableFilter, PortalTableFilterVO> getPortalService() {
        return sysPortalTableFilterPortalService;
    }

    @Override
    protected SFunction<SysPortalTableFilter, ?> id() {
        return SysPortalTableFilter::getId;
    }

    @Override
    protected SFunction<SysPortalTableFilter, Integer> order() {
        return SysPortalTableFilter::getDisplayOrder;
    }
}
