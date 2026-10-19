package com.bidr.admin.controller.table;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.dao.entity.SysPortalPivotColumn;
import com.bidr.admin.service.table.SysPortalPivotColumnPortalService;
import com.bidr.admin.vo.PortalPivotColumnVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 透视报表父表头列配置控制器（支持排序）
 *
 * @author Sharp
 */
@Api(tags = "系统基础 - 透视报表父表头列配置")
@RestController
@RequiredArgsConstructor
@AdminPortal
@RequestMapping(value = "/web/admin/portal/table/pivot/column")
public class PortalPivotColumnPortalController extends BaseAdminOrderController<SysPortalPivotColumn, PortalPivotColumnVO> {

    private final SysPortalPivotColumnPortalService sysPortalPivotColumnPortalService;

    @Override
    public PortalCommonService<SysPortalPivotColumn, PortalPivotColumnVO> getPortalService() {
        return sysPortalPivotColumnPortalService;
    }

    @Override
    protected SFunction<SysPortalPivotColumn, ?> id() {
        return SysPortalPivotColumn::getId;
    }

    @Override
    protected SFunction<SysPortalPivotColumn, Integer> order() {
        return SysPortalPivotColumn::getDisplayOrder;
    }
}
