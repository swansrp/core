package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.admin.AdminMenuService;
import com.bidr.authorization.service.permit.MenuService;
import com.bidr.authorization.service.permit.PermitSourceService;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.authorization.vo.permit.UserPermitRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
@Auth(AuthAdmin.class)
public class AdminMenuController extends BaseAdminTreeController<AcMenu, AcMenu> {

    @Resource
    private AdminMenuService adminMenuService;
    @Resource
    private MenuService menuService;
    @Resource
    private PermitSourceService permitSourceService;

    @ApiOperation(value = "获取菜单树", notes = "全部；appCode 缺省/空＝平台自身")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(@RequestParam(required = false) String appCode) {
        return adminMenuService.getMenuTree(appCode);
    }

    /**
     * 基类 {@code GET /tree/data} 覆写点（方案B 棒3 §4.4）：基类端点无参签名不能加映射参数
     * （同路径双映射会 ambiguous），可选 {@code appCode} 经当前请求读参透传——缺省/空＝平台自身。
     */
    @Override
    protected List<AcMenu> getAllData() {
        return adminMenuService.getAllData(currentAppCode());
    }

    /** 当前请求的可选 appCode 参数（仅 /tree/data 覆写点用；无请求上下文＝缺省平台自身） */
    private static String currentAppCode() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest().getParameter("appCode");
        }
        return null;
    }

    @ApiOperation(value = "获取主菜单树", notes = "全部；appCode 缺省/空＝平台自身")
    @RequestMapping(value = "/main/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMainMenuTree(@RequestParam(required = false) String appCode) {
        return adminMenuService.getMainMenuTree(appCode);
    }

    @ApiOperation(value = "获取子菜单树", notes = "全部；appCode 缺省/空＝平台自身")
    @RequestMapping(value = "/sub/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req, @RequestParam(required = false) String appCode) {
        return adminMenuService.getSubMenuTree(req, appCode);
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
        adminMenuService.addMenu(entity, MenuTypeDict.BUTTON);
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

    @ApiOperation(value = "获取用户权限来源")
    @RequestMapping(value = "/user/permit/source", method = RequestMethod.GET)
    public List<UserPermitRes> getUserPermitSource(Long menuId, @RequestParam(required = false) String customerNumber) {
        if(FuncUtil.isEmpty(customerNumber)) {
            customerNumber = AccountContext.getOperator();
        }
        return permitSourceService.getUserPermitSource(menuId, customerNumber);
    }

    @ApiOperation(value = "获取菜单权限分配目标")
    @RequestMapping(value = "/permit/targets", method = RequestMethod.GET)
    public List<UserPermitRes> getMenuPermitTargets(Long menuId) {
        return permitSourceService.getMenuPermitTargets(menuId);
    }

    @ApiOperation(value = "获取用户权限", notes = "appCode 缺省/空＝平台自身")
    @RequestMapping(value = "/user/permit", method = RequestMethod.GET)
    public List<MenuTreeRes> getUserPermit(String customerNumber, @RequestParam(required = false) String appCode) {
        return menuService.getMenuTree(customerNumber, appCode);
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
