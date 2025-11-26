package com.bidr.forge.controller.dataset;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.service.dataset.SysDatasetPortalService;
import com.bidr.forge.vo.dataset.SysDatasetVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集主表 Portal Controller
 *
 * @author sharp
 * @since 2025-11-25
 */
@Api(tags = "Forge - 数据集配置 - 数据集主表")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dataset"})
public class SysDatasetPortalController extends BaseAdminController<SysDataset, SysDatasetVO> {

    private final SysDatasetPortalService sysDatasetPortalService;

    @Override
    public PortalCommonService<SysDataset, SysDatasetVO> getPortalService() {
        return sysDatasetPortalService;
    }
}
