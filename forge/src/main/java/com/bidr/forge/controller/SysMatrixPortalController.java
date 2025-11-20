package com.bidr.forge.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.service.SysMatrixPortalService;
import com.bidr.forge.vo.SysMatrixVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 矩阵配置Portal Controller
 *
 * @author sharp
 * @since 2025-11-20
 */
@Api(tags = "动态配置 - 矩阵管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/matrix"})
public class SysMatrixPortalController extends BaseAdminOrderController<SysMatrix, SysMatrixVO> {

    private final SysMatrixPortalService sysMatrixPortalService;

    @Override
    public PortalCommonService<SysMatrix, SysMatrixVO> getPortalService() {
        return sysMatrixPortalService;
    }

    @Override
    protected SFunction<SysMatrix, ?> id() {
        return SysMatrix::getId;
    }

    @Override
    protected SFunction<SysMatrix, Integer> order() {
        return SysMatrix::getSort;
    }

    /**
     * 创建物理表
     *
     * @param id 矩阵ID
     */
    @ApiOperation("创建物理表")
    @PostMapping("/create-table")
    public void createPhysicalTable(@RequestParam Long id) {
        sysMatrixPortalService.createPhysicalTable(id);
    }

    /**
     * 同步表结构
     *
     * @param id 矩阵ID
     */
    @ApiOperation("同步表结构")
    @PostMapping("/sync-table")
    public void syncTableStructure(@RequestParam Long id) {
        sysMatrixPortalService.syncTableStructure(id);
    }
}
