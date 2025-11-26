package com.bidr.forge.controller.portal;

import com.bidr.admin.vo.PortalReq;
import com.bidr.forge.service.portal.PortalGenerateService;
import com.bidr.forge.vo.portal.GeneratePortalReq;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Portal配置生成接口
 * 用于为Matrix和Dataset生成对应的Portal配置
 *
 * @author sharp
 * @since 2025-11-25
 */
@Slf4j
@RestController
@Api(tags = "Portal配置生成")
@RequestMapping("/web/portal/generate")
@RequiredArgsConstructor
public class PortalGenerateController {

    private final PortalGenerateService portalGenerateService;

    @PostMapping("/matrix")
    @ApiOperation(value = "为Matrix生成Portal配置")
    public void generatePortalForMatrix(@Validated @RequestBody GeneratePortalReq req) {
        req.setDataMode("MATRIX");
        Long portalId = portalGenerateService.generatePortalForMatrix(req);
        Resp.notice("Matrix Portal配置生成成功");
    }

    @PostMapping("/dataset")
    @ApiOperation(value = "为Dataset生成Portal配置")
    public void generatePortalForDataset(@Validated @RequestBody GeneratePortalReq req) {
        req.setDataMode("DATASET");
        Long portalId = portalGenerateService.generatePortalForDataset(req);
        Resp.notice("Dataset Portal配置生成成功");
    }

    @PostMapping("/matrix/refresh")
    @ApiOperation(value = "刷新Matrix对应的Portal配置")
    public void refreshPortalForMatrix(@RequestBody PortalReq req) {
        portalGenerateService.refreshPortalForMatrix(req.getName(), req.getRoleId());
        Resp.notice("Matrix Portal配置刷新成功");
    }

    @PostMapping("/dataset/refresh")
    @ApiOperation(value = "刷新Dataset对应的Portal配置")
    public void refreshPortalForDataset(@RequestBody PortalReq req) {
        portalGenerateService.refreshPortalForDataset(req.getName(), req.getRoleId());
        Resp.notice("Dataset Portal配置刷新成功");
    }
}
