package com.bidr.admin.controller;

import com.bidr.admin.service.PortalConfigService;
import com.bidr.admin.service.PortalService;
import com.bidr.admin.vo.*;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Title: AdminPortalController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/04 15:17
 */
@Api(tags = "系统基础 - 快速后台管理 - 参数配置")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/admin"})
public class AdminPortalController {

    private final PortalService portalService;
    private final PortalConfigService portalConfigService;

    @RequestMapping(path = {"/list"}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取管理配置列表")
    public List<KeyValueResVO> getPortalList(PortalReq req) {
        return portalService.getPortalList(req);
    }

    @RequestMapping(path = {"/config"}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取后台管理配置")
    public PortalWithColumnsRes getPortal(PortalReq req) {
        return portalService.getPortalWithColumnsConfig(req);
    }

    @RequestMapping(path = {"/config"}, method = {RequestMethod.POST})
    @ApiOperation(value = "更新后台管理配置")
    public void updatePortalConfig(@RequestBody PortalUpdateReq req) {
        portalService.updatePortalConfig(req);
        Resp.notice("表格配置更新成功");
    }

    @RequestMapping(path = {"/column/order"}, method = {RequestMethod.POST})
    @ApiOperation(value = "更新后台配置字段顺序")
    public void updatePortalConfig(@RequestBody List<IdOrderReqVO> orderList) {
        portalService.updatePortalColumnOrder(orderList);
        Resp.notice("更新后台配置字段顺序成功");
    }

    @RequestMapping(path = {"/column"}, method = {RequestMethod.POST})
    @ApiOperation(value = "更新后台配置字段")
    public void updatePortalConfig(@RequestBody PortalColumnReq req) {
        portalService.updatePortalColumn(req);
        Resp.notice("更新后台配置字段成功");
    }

    @RequestMapping(path = {"/config/delete"}, method = {RequestMethod.POST})
    @ApiOperation(value = "删除配置")
    public void deletePortalConfig(@RequestBody IdReqVO req) {
        portalService.deletePortalConfig(req);
        Resp.notice("删除后台配置成功");
    }

    @RequestMapping(path = {"/config/refresh"}, method = {RequestMethod.POST})
    @ApiOperation(value = "恢复默认设置")
    public void importConfig(@RequestBody IdReqVO req) {
        portalService.refreshPortalConfig(req);
        Resp.notice("恢复后台配置成功");
    }

    @RequestMapping(path = {"/config/existed"}, method = {RequestMethod.GET})
    @ApiOperation(value = "检查后台管理配置是否存在")
    public void validatePortalExisted(PortalReq req) {
        portalService.validatePortalExisted(req);
    }

    @RequestMapping(path = {"/config/copy"}, method = {RequestMethod.POST})
    @ApiOperation(value = "复制后台管理配置")
    public void copyPortalConfig(@RequestBody PortalCopyReq req) {
        portalService.copyPortalConfig(req);
        Resp.notice("复制表格配置成功");
    }

    @RequestMapping(path = {"/role/bind"}, method = {RequestMethod.POST})
    @ApiOperation(value = "初始化角色配置")
    public void bindRole(@RequestBody PortalRoleBindReq req) {
        portalConfigService.bindRole(req.getRoleId(), req.getTemplateRoleId());
        Resp.notice("初始化角色配置成功");
    }

    @RequestMapping(path = {"/role/unbind"}, method = {RequestMethod.POST})
    @ApiOperation(value = "删除角色配置")
    public void unBindRole(@RequestBody PortalRoleUnBindReq req) {
        portalConfigService.unBindRole(req.getRoleId());
        Resp.notice("删除角色配置成功");
    }

    @RequestMapping(path = {"/role"}, method = {RequestMethod.GET})
    @ApiOperation(value = "查看已经绑定配置的角色列表")
    public List<KeyValueResVO> getBindRoleDict() {
        return portalConfigService.getBindRoleDict();
    }

    @RequestMapping(path = {"/config/export"}, method = {RequestMethod.GET})
    @ApiOperation(value = "导出指定配置")
    public void exportConfig(HttpServletRequest request, HttpServletResponse response, PortalReq req) {
        PortalWithColumnsRes res = portalService.getPortalWithColumnsConfig(req.getName(), req.getRoleId());
        HttpUtil.export(request, response, "application/json", "utf-8", "portalConfig.dat",
                JsonUtil.toJson(res).getBytes());
    }

    @RequestMapping(path = {"/config/import"}, method = {RequestMethod.POST})
    @ApiOperation(value = "导入指定配置")
    public void importConfig(MultipartFile file) throws IOException {
        portalConfigService.importConfig(file.getInputStream());
        Resp.notice("导入配置成功");
    }


}
