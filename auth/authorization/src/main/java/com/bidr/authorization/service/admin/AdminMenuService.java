package com.bidr.authorization.service.admin;

import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleMenuService;
import com.bidr.authorization.service.permit.PermitService;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.bidr.authorization.constants.param.AccountParam.ACCOUNT_ADMIN_ROLE_ID;

/**
 * Title: AdminMenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/24 09:50
 */
@Service
public class AdminMenuService {

    @Resource
    private AcMenuService acMenuService;
    @Resource
    private AcRoleMenuService acRoleMenuService;
    @Resource
    private SysConfigCacheService sysConfigCacheService;
    @Resource
    private PermitService permitService;

    @Transactional(rollbackFor = Exception.class)
    public void addMenu(AcMenu entity, MenuTypeDict menuType) {
        if (MenuTypeDict.SUB_MENU.equals(menuType)) {
            entity.setShowOrder(acMenuService.countByPid(entity.getPid()) + 1);
        } else {
            entity.setShowOrder(acMenuService.countByGrandId(entity.getGrandId()) + 1);
        }
        entity.setMenuType(menuType.getValue());
        entity.setStatus(CommonConst.YES);
        entity.setVisible(CommonConst.YES);
        // app_code 落列（方案B 棒3 §4.4）：请求体显式携带则原样落库；未携带＝继承当前应用上下文
        // ——非顶部菜单按父行（pid 为空取 grandId）的 app_code 继承，顶部菜单无父行即平台自身（NULL）
        if (FuncUtil.isEmpty(entity.getAppCode())) {
            entity.setAppCode(inheritAppCode(entity));
        }
        acMenuService.insert(entity);
        entity.setKey(entity.getMenuId());
        acMenuService.updateById(entity);
        AcRoleMenu acRoleMenu = new AcRoleMenu();
        acRoleMenu.setMenuId(entity.getMenuId());
        acRoleMenu.setRoleId(sysConfigCacheService.getParamLong(ACCOUNT_ADMIN_ROLE_ID));
        acRoleMenuService.insert(acRoleMenu);
    }

    /**
     * 新增菜单的 app_code 继承：父行在场则取父行 app_code（子菜单挂到子系统父节点下不会错落平台自身），
     * 父行缺席/为顶部菜单 ⇒ null（平台自身）。
     */
    private String inheritAppCode(AcMenu entity) {
        Long parentId = FuncUtil.isNotEmpty(entity.getPid()) ? entity.getPid() : entity.getGrandId();
        if (FuncUtil.isEmpty(parentId)) {
            return null;
        }
        AcMenu parent = acMenuService.selectById(parentId);
        return parent == null ? null : parent.getAppCode();
    }

    public List<AcMenu> getAllData() {
        return getAllData(null);
    }

    /**
     * 全量菜单（方案B 棒3 §4.4）：appCode 空＝平台自身（app_code IS NULL），非空＝等值收窄。
     */
    public List<AcMenu> getAllData(String appCode) {
        List<AcMenu> menuList = acMenuService.getAllMenu(permitService.isAdmin(), appCode);
        for (AcMenu acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return menuList;
    }

    public List<MenuTreeRes> getMenuTree() {
        return getMenuTree(null);
    }

    /**
     * 管理面菜单树（方案B 棒3 §4.4）：appCode 空＝平台自身，非空＝该子系统菜单。
     */
    public List<MenuTreeRes> getMenuTree(String appCode) {
        List<AcMenu> menuList = getAllData(appCode);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeRes> getMainMenuTree() {
        return getMainMenuTree(null);
    }

    public List<MenuTreeRes> getMainMenuTree(String appCode) {
        List<AcMenu> menuList = acMenuService.getMainMenu(appCode);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        return getSubMenuTree(req, null);
    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req, String appCode) {
        List<AcMenu> menuList = acMenuService.getSubMenu(req.getMenuId(), appCode);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }
}
