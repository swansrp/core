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
        acMenuService.insert(entity);
        entity.setKey(entity.getMenuId());
        acMenuService.updateById(entity);
        AcRoleMenu acRoleMenu = new AcRoleMenu();
        acRoleMenu.setMenuId(entity.getMenuId());
        acRoleMenu.setRoleId(sysConfigCacheService.getParamLong(ACCOUNT_ADMIN_ROLE_ID));
        acRoleMenuService.insert(acRoleMenu);
    }

    public List<MenuTreeRes> getMenuTree() {
        List<AcMenu> menuList = acMenuService.getAllMenu(permitService.isAdmin());
        for (AcMenu acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeRes> getMainMenuTree() {
        List<AcMenu> menuList = acMenuService.getMainMenu();
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        List<AcMenu> menuList = acMenuService.getSubMenu(req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }
}
