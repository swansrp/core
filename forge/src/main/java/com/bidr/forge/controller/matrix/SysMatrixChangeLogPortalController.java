package com.bidr.forge.controller.matrix;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysMatrixChangeLog;
import com.bidr.forge.service.martix.SysMatrixChangeLogPortalService;
import com.bidr.forge.vo.matrix.ImportChangeLogReqVO;
import com.bidr.forge.vo.matrix.SysMatrixChangeLogVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 矩阵表结构变更日志 Portal Controller
 *
 * @author sharp
 * @since 2025-11-21
 */
@Api(tags = "动态配置 - 矩阵表结构变更日志")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/matrix-change-log"})
public class SysMatrixChangeLogPortalController extends BaseAdminOrderController<SysMatrixChangeLog, SysMatrixChangeLogVO> {

    private final SysMatrixChangeLogPortalService sysMatrixChangeLogPortalService;

    @Override
    public PortalCommonService<SysMatrixChangeLog, SysMatrixChangeLogVO> getPortalService() {
        return sysMatrixChangeLogPortalService;
    }

    @Override
    protected SFunction<SysMatrixChangeLog, ?> id() {
        return SysMatrixChangeLog::getId;
    }

    @Override
    protected SFunction<SysMatrixChangeLog, Integer> order() {
        return SysMatrixChangeLog::getSort;
    }

    /**
     * 导出变更日志（跨环境同步）
     *
     * @param id 矩阵ID
     * @return 变更日志JSON数据
     */
    @ApiOperation("导出变更日志")
    @GetMapping("/export/log")
    public String exportChangeLog(@RequestParam Long id) {
        return sysMatrixChangeLogPortalService.exportChangeLog(id);
    }

    /**
     * 导入变更日志（跨环境同步）
     *
     * @param request 导入请求
     */
    @ApiOperation("导入变更日志")
    @PostMapping("/import/log")
    public void importChangeLog(@RequestBody ImportChangeLogReqVO request) {
        sysMatrixChangeLogPortalService.importChangeLog(request.getChangeLogData());
    }
}
