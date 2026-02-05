package com.bidr.authorization.controller;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.MenuService;
import com.bidr.authorization.service.permit.PermitSourceService;
import com.bidr.authorization.vo.menu.MenuTreeItem;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.authorization.vo.permit.UserPermitRes;
import com.bidr.kernel.utils.FuncUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: PermitController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/13 15:04
 */
@Api(tags = "系统基础 - 页面权限")
@RestController("PermitController")
@RequestMapping(value = "/web/menu")
public class PermitController {
    @Resource
    private MenuService menuService;
    @Resource
    private PermitSourceService permitSourceService;

    @ApiOperation(value = "获取主菜单树", notes = "登录后准入")
    @RequestMapping(value = "/main/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMainMenuTree() {
        return menuService.getMainMenuTree();
    }

    @ApiOperation(value = "获取主菜单列表", notes = "登录后准入")
    @RequestMapping(value = "/main/list", method = RequestMethod.GET)
    public List<MenuTreeItem> getMainMenuList() {
        return menuService.getMenuList();
    }

    @ApiOperation(value = "获取子菜单树", notes = "登录后准入")
    @RequestMapping(value = "/sub/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        return menuService.getSubMenuTree(req);
    }

    @ApiOperation(value = "获取子菜单列表", notes = "登录后准入")
    @RequestMapping(value = "/sub/list", method = RequestMethod.GET)
    public List<MenuTreeItem> getSubMenuList(MenuTreeReq req) {
        return menuService.getSubMenuList(req);
    }

    @ApiOperation(value = "获取目录树", notes = "登录后准入")
    @RequestMapping(value = "/content/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getContentTree(MenuTreeReq req) {
        return menuService.getContentTree(req);
    }

    @ApiOperation(value = "获取目录列表", notes = "登录后准入")
    @RequestMapping(value = "/content/list", method = RequestMethod.GET)
    public List<MenuTreeItem> getContentList(MenuTreeReq req) {
        return menuService.getContentList(req);
    }

    @ApiOperation(value = "获取权限树", notes = "登录后准入")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenu() {
        return menuService.getMenuTree();
    }

    @ApiOperation(value = "获取权限列表", notes = "登录后准入")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<MenuTreeItem> getPermitList() {
        return menuService.getMenuList();
    }

    @ApiOperation(value = "获取用户权限来源")
    @RequestMapping(value = "/user/permit", method = RequestMethod.GET)
    public List<UserPermitRes> getUserPermit(Long menuId, @RequestParam(required = false) String customerNumber) {
        if(FuncUtil.isNotEmpty(customerNumber)) {
            customerNumber = AccountContext.getOperator();
        }
        return permitSourceService.getUserPermit(menuId, customerNumber);
    }

}
