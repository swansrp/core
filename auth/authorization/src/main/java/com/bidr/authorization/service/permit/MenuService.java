package com.bidr.authorization.service.permit;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcDeptMenuService;
import com.bidr.authorization.dao.repository.AcGroupMenuService;
import com.bidr.authorization.dao.repository.AcUserMenuService;
import com.bidr.authorization.dao.repository.AcUserService;
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

import java.util.*;

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

    private final AcUserRoleMenuService acUserRoleMenuService;
    private final AcDeptMenuService acDeptMenuService;
    private final AcGroupMenuService acGroupMenuService;
    private final AcUserMenuService acUserMenuService;
    private final AcUserService acUserService;

    public List<MenuTreeRes> getMenuTree() {
        return getMenuTree(AccountContext.getOperator(), null);
    }

    public List<MenuTreeRes> getMenuTree(String customerNumber) {
        return getMenuTree(customerNumber, null);
    }

    /**
     * 归属应用收窄版（方案B 棒3 §4.1）：门户腿不传 appCode＝平台自身（app_code IS NULL 存量行为），
     * 开放口传 partner 解析出的子系统 app_code。user 不存在沿用既有业务拒绝（同口径）。
     */
    public List<MenuTreeRes> getMenuTree(String customerNumber, String appCode) {
        AcUser acUser = acUserService.getByCustomerNumber(customerNumber);
        if (acUser == null) {
            // 数据闸对「登录态但无租户绑定」的用户恒假（ac_user 查不到）——这是无权限，不是系统错误，
            // 菜单给空集合；不能让 null.getUserId() 变成 500。
            return Collections.emptyList();
        }
        Long userId = acUser.getUserId();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> roleMenuList = acUserRoleMenuService.getAllMenu(customerNumber, clientType);
        List<AcMenu> deptMenuList = acDeptMenuService.getAllMenu(userId, clientType);
        List<AcMenu> groupMenuList = acGroupMenuService.getAllMenu(userId, clientType);
        List<AcMenu> userMenuList = acUserMenuService.getAllMenu(customerNumber, clientType);
        // 四路查出的 menu 行统一按 appCode 谓词收窄后再 buildTree（聚合前行级过滤，非树后剪枝）
        List<AcMenu> menuList = narrowByAppCode(merge(roleMenuList, deptMenuList, groupMenuList, userMenuList), appCode);
        // 前端已经不需要必须配置菜单了
        // Validator.assertNotEmpty(menuList, AccountErrCode.AC_PERMIT_NOT_EXISTED);
        for (AcMenu acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);
    }

    /**
     * 菜单行按归属应用收窄（方案B 棒3 §4.1 防漏闸）：appCode 空＝平台自身（只留 app_code 为 NULL 的行，
     * 门户存量行为），非空＝只留等值行。行级谓词、聚合前过滤；绑定树控制器与门户缺省腿共用本口。
     */
    public static List<AcMenu> narrowByAppCode(List<AcMenu> menuList, String appCode) {
        if (FuncUtil.isEmpty(menuList)) {
            return menuList;
        }
        List<AcMenu> narrowed = new ArrayList<>();
        for (AcMenu menu : menuList) {
            if (FuncUtil.isEmpty(appCode) ? menu.getAppCode() == null : appCode.equals(menu.getAppCode())) {
                narrowed.add(menu);
            }
        }
        return narrowed;
    }

    /**
     * 合并多个菜单列表并去重
     * 使用 menuId 作为唯一标识，保持插入顺序
     *
     * @param menuLists 可变参数的菜单列表
     * @return 合并去重后的菜单列表
     */
    @SafeVarargs
    private final List<AcMenu> merge(List<AcMenu>... menuLists) {
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

        List<AcMenu> mergedMenus = new ArrayList<>(menuMap.values());
        mergedMenus.sort(
                Comparator.comparing(AcMenu::getShowOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AcMenu::getMenuId)
        );
        return mergedMenus;
    }

    public List<MenuTreeItem> getMenuList() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        // 门户缺省＝平台自身（app_code IS NULL，方案B 棒3 §4.1）
        List<AcMenu> allPermit = narrowByAppCode(
                acUserRoleMenuService.getAllMenu(customerNumber, clientType), null);
        Validator.assertNotEmpty(allPermit, AccountErrCode.AC_PERMIT_NOT_EXISTED);
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getMainMenuTree() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = narrowByAppCode(
                acUserRoleMenuService.getMainMenu(customerNumber, clientType), null);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getMainMenuList() {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = narrowByAppCode(
                acUserRoleMenuService.getMainMenu(customerNumber, clientType), null);
        return ReflectionUtil.copyList(menuList, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = narrowByAppCode(
                acUserRoleMenuService.getSubMenu(customerNumber, clientType, req.getMenuId()), null);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getSubMenuList(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> allPermit = narrowByAppCode(
                acUserRoleMenuService.getSubMenu(customerNumber, clientType, req.getMenuId()), null);
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getContentTree(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> menuList = narrowByAppCode(
                acUserRoleMenuService.getContent(customerNumber, clientType, req.getMenuId()), null);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getContentList(MenuTreeReq req) {
        String customerNumber = AccountContext.getOperator();
        String clientType = ClientTypeHolder.get();
        List<AcMenu> allPermit = narrowByAppCode(
                acUserRoleMenuService.getContent(customerNumber, clientType, req.getMenuId()), null);
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }
}
