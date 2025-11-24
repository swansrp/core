package com.bidr.forge.controller.matrix;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.service.martix.SysMatrixChangeLogPortalService;
import com.bidr.forge.service.martix.SysMatrixPortalService;
import com.bidr.forge.vo.matrix.ImportChangeLogReqVO;
import com.bidr.forge.vo.matrix.ImportMatrixReq;
import com.bidr.forge.vo.matrix.SysMatrixVO;
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
    private final SysMatrixChangeLogPortalService sysMatrixChangeLogPortalService;

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

    /**
     * 清空表数据（只有超级管理员才能执行）
     *
     * @param id 矩阵ID
     */
    @ApiOperation("清空表数据")
    @PostMapping("/truncate-table")
    public void truncateTable(@RequestParam Long id) {
        sysMatrixPortalService.truncateTable(id);
    }

    /**
     * 导出DDL语句
     *
     * @param id 矩阵ID
     * @return DDL语句
     */
    @ApiOperation("导出DDL语句")
    @GetMapping("/export-ddl")
    public String exportDDL(@RequestParam Long id) {
        return sysMatrixPortalService.exportDDL(id);
    }

    /**
     * 导入DDL语句
     *
     * @param request DDL导入请求
     * @return 矩阵ID
     */
    @ApiOperation("导入DDL语句")
    @PostMapping("/import-ddl")
    public Long importDDL(@RequestBody ImportMatrixReq request) {
        return sysMatrixPortalService.importDDL(request.getDdl());
    }

    /**
     * 导出变更日志（跨环境同步）
     *
     * @param id 矩阵ID
     * @return 变更日志JSON数据
     */
    @ApiOperation("导出变更日志")
    @GetMapping("/export-changelog")
    public String exportChangeLog(@RequestParam Long id) {
        return sysMatrixChangeLogPortalService.exportChangeLog(id);
    }

    /**
     * 导入变更日志（跨环境同步）
     *
     * @param request 导入请求
     */
    @ApiOperation("导入变更日志")
    @PostMapping("/import-changelog")
    public void importChangeLog(@RequestBody ImportChangeLogReqVO request) {
        sysMatrixChangeLogPortalService.importChangeLog(request.getChangeLogData());
    }
}
