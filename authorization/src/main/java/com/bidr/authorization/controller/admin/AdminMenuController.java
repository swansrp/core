package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.service.admin.AdminMenuService;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.vo.common.IdPidReqVO;
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
@Api(tags = "系统菜单管理")
@RestController("MenuController")
@RequestMapping(value = "/web/menu/admin")
public class AdminMenuController extends BaseAdminOrderController<AcMenu, AcMenu> {

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
    public Boolean addMainMenu(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.MENU);
        return true;
    }

    @ApiOperation(value = "添加侧边菜单")
    @RequestMapping(value = "/add/sub", method = RequestMethod.POST)
    public Boolean addSubMenu(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.SUB_MENU);
        return true;
    }

    @RequestMapping(value = "/add/content", method = RequestMethod.POST)
    public Boolean addContent(@RequestBody AcMenu entity) {
        adminMenuService.addMenu(entity, MenuTypeDict.CONTENT);
        return true;
    }

    @RequestMapping(value = "/add/button", method = RequestMethod.POST)
    public Boolean addButton(@RequestBody AcMenu entity) {
        entity.setKey(entity.getMenuId());
        entity.setMenuType(MenuTypeDict.BUTTON.getValue());
        entity.setKey(entity.getMenuId());
        return getRepo().updateById(entity);
    }

    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public Boolean enable(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::setStatus, CommonConst.YES);
    }


    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public Boolean disable(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::setStatus, CommonConst.NO);
    }

    @RequestMapping(value = "/show", method = RequestMethod.POST)
    public Boolean show(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::setVisible, CommonConst.YES);
    }

    @RequestMapping(value = "/hide", method = RequestMethod.POST)
    public Boolean hide(@RequestBody IdReqVO vo) {
        return update(vo, AcMenu::setVisible, CommonConst.NO);
    }

    @RequestMapping(value = "/pid", method = RequestMethod.POST)
    public Boolean pid(@RequestBody IdPidReqVO vo) {
        return update(vo, AcMenu::setPid, JsonUtil.readJson(vo.getPid(), Long.class));
    }

    @Override
    protected SFunction<AcMenu, ?> id() {
        return AcMenu::getMenuId;
    }

    @Override
    protected SFunction<AcMenu, Integer> order() {
        return AcMenu::getShowOrder;
    }
}
