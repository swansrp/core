package com.bidr.authorization.service.permit;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcDeptMenuService;
import com.bidr.authorization.dao.repository.AcGroupMenuService;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcUserMenuService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.vo.menu.MenuTreeItem;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: MenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/13 15:05
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final AcMenuService acMenuService;
    private final AcUserRoleMenuService acUserRoleMenuService;
    private final AcDeptMenuService acDeptMenuService;
    private final AcGroupMenuService acGroupMenuService;
    private final AcUserMenuService acUserMenuService;

    public List<MenuTreeRes> getMenuTree() {
        String customerNumber = AccountContext.getOperator();
        Long userId = AccountContext.getUserId();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> roleMenuList = acUserRoleMenuService.getAllMenu(customerNumber, clientType);
        List<AcMenu> deptMenuList = acDeptMenuService.getAllMenu(userId, clientType);
        List<AcMenu> groupMenuList = acGroupMenuService.getAllMenu(userId, clientType);
        List<AcMenu> userMenuList = acUserMenuService.getAllMenu(userId, clientType);
        List<AcMenu> menuList = merge(roleMenuList, deptMenuList, groupMenuList, userMenuList);
        Validator.assertNotEmpty(menuList, AccountErrCode.AC_PERMIT_NOT_EXISTED);
        for (AcMenu acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);
    }

    /**
     * 合并多个菜单列表并去重
     * 使用 menuId 作为唯一标识，保持插入顺序
     *
     * @param menuLists 可变参数的菜单列表
     * @return 合并去重后的菜单列表
     */
    private List<AcMenu> merge(List<AcMenu>... menuLists) {
        Map<Long, AcMenu> menuMap = new LinkedHashMap<>();
        
        // 遍历所有菜单列表
        for (List<AcMenu> menuList : menuLists) {
            if (FuncUtil.isNotEmpty(menuList)) {
                for (AcMenu menu : menuList) {
                    // 使用 menuId 作为唯一键，如果已存在则不覆盖（保留第一次出现的）
                    if (!menuMap.containsKey(menu.getMenuId())) {
                        menuMap.put(menu.getMenuId(), menu);
                    }
                }
            }
        }
        
        return new ArrayList<>(menuMap.values());
    }

    public List<MenuTreeItem> getMenuList() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> allPermit = acUserRoleMenuService.getAllMenu(customerNumber, clientType);
        Validator.assertNotEmpty(allPermit, AccountErrCode.AC_PERMIT_NOT_EXISTED);
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
