package com.bidr.forge.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.service.SysMatrixColumnPortalService;
import com.bidr.forge.vo.SysMatrixColumnVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 矩阵字段配置Portal Controller
 *
 * @author sharp
 * @since 2025-11-20
 */
@Api(tags = "动态配置 - 矩阵字段管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/matrix-column"})
public class SysMatrixColumnPortalController extends BaseAdminOrderController<SysMatrixColumn, SysMatrixColumnVO> {

    private final SysMatrixColumnPortalService sysMatrixColumnPortalService;

    @Override
    public PortalCommonService<SysMatrixColumn, SysMatrixColumnVO> getPortalService() {
        return sysMatrixColumnPortalService;
    }

    @Override
    protected SFunction<SysMatrixColumn, ?> id() {
        return SysMatrixColumn::getId;
    }

    @Override
    protected SFunction<SysMatrixColumn, Integer> order() {
        return SysMatrixColumn::getSort;
    }
}
