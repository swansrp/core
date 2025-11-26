package com.bidr.forge.controller.dataset;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.service.dataset.SysDatasetColumnPortalService;
import com.bidr.forge.vo.dataset.SysDatasetColumnVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集列配置 Portal Controller
 *
 * @author sharp
 * @since 2025-11-25
 */
@Api(tags = "Forge - 数据集配置 - 数据集列配置")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dataset/column"})
public class SysDatasetColumnPortalController extends BaseAdminOrderController<SysDatasetColumn, SysDatasetColumnVO> {

    private final SysDatasetColumnPortalService sysDatasetColumnPortalService;

    @Override
    public PortalCommonService<SysDatasetColumn, SysDatasetColumnVO> getPortalService() {
        return sysDatasetColumnPortalService;
    }

    @Override
    protected SFunction<SysDatasetColumn, ?> id() {
        return SysDatasetColumn::getId;
    }

    @Override
    protected SFunction<SysDatasetColumn, Integer> order() {
        return SysDatasetColumn::getDisplayOrder;
    }
}
