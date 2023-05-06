package com.bidr.authorization.service.permit;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.vo.menu.MenuTreeItem;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: MenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:05
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final AcMenuService acMenuService;
    private final AcUserRoleMenuService acUserRoleMenuService;

    public List<MenuTreeRes> getMenuTree() {
        List<AcMenu> menuList = acMenuService.getAllMenu();
        for (AcMenu acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);
    }

    public List<MenuTreeItem> getMenuList() {
        List<AcMenu> allPermit = acMenuService.getAllMenu();
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getMainMenuTree() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = acUserRoleMenuService.getMainMenu(customerNumber, clientType);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getMainMenuList() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = acUserRoleMenuService.getMainMenu(customerNumber, clientType);
        return ReflectionUtil.copyList(menuList, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = acUserRoleMenuService.getSubMenu(customerNumber, clientType, req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getSubMenuList(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> allPermit = acUserRoleMenuService.getSubMenu(customerNumber, clientType, req.getMenuId());
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getContentTree(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = acUserRoleMenuService.getContent(customerNumber, clientType, req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getContentList(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> allPermit = acUserRoleMenuService.getContent(customerNumber, clientType, req.getMenuId());
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }
}
