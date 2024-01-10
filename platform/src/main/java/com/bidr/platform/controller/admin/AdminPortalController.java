package com.bidr.platform.controller.admin;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.service.portal.PortalService;
import com.bidr.platform.vo.params.SysConfigRes;
import com.bidr.platform.vo.portal.PortalColumnReq;
import com.bidr.platform.vo.portal.PortalReq;
import com.bidr.platform.vo.portal.PortalUpdateReq;
import com.bidr.platform.vo.portal.PortalWithColumnsRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
public class AdminPortalController extends BaseAdminController<SysConfig, SysConfigRes> {

    private final PortalService portalService;

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
    public void deletePortalConfig(@RequestBody PortalReq req) {
        portalService.deletePortalConfig(req);
        Resp.notice("删除后台配置成功");
    }
}
