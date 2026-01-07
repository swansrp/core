package com.bidr.forge.controller.dataset;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.service.dataset.SysDatasetTablePortalService;
import com.bidr.forge.vo.dataset.SysDatasetTableVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集关联表 Portal Controller
 *
 * @author sharp
 * @since 2025-11-25
 */
@Api(tags = "系统基础 - 数据集配置 - 数据集关联表")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dataset/table"})
public class SysDatasetTablePortalController extends BaseAdminOrderController<SysDatasetTable, SysDatasetTableVO> {

    private final SysDatasetTablePortalService sysDatasetTablePortalService;

    @Override
    public PortalCommonService<SysDatasetTable, SysDatasetTableVO> getPortalService() {
        return sysDatasetTablePortalService;
    }

    @Override
    protected SFunction<SysDatasetTable, ?> id() {
        return SysDatasetTable::getId;
    }

    @Override
    protected SFunction<SysDatasetTable, Integer> order() {
        return SysDatasetTable::getTableOrder;
    }
}
