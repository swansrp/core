package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.service.admin.AdminMenuService;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminMenuController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/20 11:48
 */
@Api(tags = "系统管理 - 菜单管理")
@RestController("AdminMenuController")
@RequestMapping(value = "/web/admin/menu")
public class AdminMenuController extends BaseAdminTreeController<AcMenu, AcMenu> {

    @Resource
    private AdminMenuService adminMenuService;

    @ApiOperation(value = "获取菜单树", notes = "全部")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree() {
        return adminMenuService.getMenuTree();
    }

    @ApiOperation(value = "获取主菜单树", notes = "全部")
    @RequestMapping(value = "/main/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMainMenuTree() {
        return adminMenuService.getMainMenuTree();
    }

    @ApiOperation(value = "获取子菜单树", notes = "全部")
    @RequestMapping(value = "/sub/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        return adminMenuService.getSubMenuTree(req);
    }

    @ApiOperation(value = "添加顶部菜单")
    @RequestMapping(value = "/add/main", method = RequestMethod.POST)
    public void addMainMenu(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.MENU);
        Resp.notice("添加顶部菜单成功");
    }

    @ApiOperation(value = "添加侧边菜单")
    @RequestMapping(value = "/add/sub", method = RequestMethod.POST)
    public void addSubMenu(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.SUB_MENU);
        Resp.notice("添加左侧菜单成功");
    }

    @RequestMapping(value = "/add/content", method = RequestMethod.POST)
    public void addContent(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.CONTENT);
        Resp.notice("添加页面目录成功");
    }

    @RequestMapping(value = "/add/button", method = RequestMethod.POST)
    public void addButton(@RequestBody AcMenu entity) {
        entity.setKey(entity.getMenuId());
        entity.setMenuType(MenuTypeDict.BUTTON.getValue());
        entity.setKey(entity.getMenuId());
        getRepo().updateById(entity);
        Resp.notice("添加按钮成功");
    }

    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public Boolean enable(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::getStatus, CommonConst.YES);
    }

    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public Boolean disable(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::getStatus, CommonConst.NO);
    }

    @RequestMapping(value = "/show", method = RequestMethod.POST)
    public Boolean show(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::getVisible, CommonConst.YES);
    }

    @RequestMapping(value = "/hide", method = RequestMethod.POST)
    public Boolean hide(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::getVisible, CommonConst.NO);
    }

    @Override
    protected SFunction<AcMenu, ?> id() {
        return AcMenu::getMenuId;
    }

    @Override
    protected SFunction<AcMenu, Integer> order() {
        return AcMenu::getShowOrder;
    }

    @Override
    protected SFunction<AcMenu, ?> pid() {
        return AcMenu::getPid;
    }

    @Override
    protected SFunction<AcMenu, String> name() {
        return AcMenu::getTitle;
    }
}
