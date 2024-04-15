package com.bidr.authorization.dao.repository.join;

import com.bidr.authorization.bo.account.UserPermitInfo;
import com.bidr.authorization.bo.account.UserRolePermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.*;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserRoleMenuService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 14:43
 */
@Service
@RequiredArgsConstructor
public class AcUserRoleMenuService {

    private final AcUserService acUserService;
    private final AcMenuService acMenuService;

    private final AcRoleService acRoleService;

    public UserRolePermitInfo getByCustomerNumberAndClientType(String customerNumber, String clientType) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<AcUser>().selectAll(AcUser.class)
                .selectCollection(AcMenu.class, UserPermitInfo::getMenuList)
                .leftJoin(AcUserRole.class, AcUserRole::getUserId, AcUser::getUserId)
                .leftJoin(AcRole.class, AcRole::getRoleId, AcUserRole::getRoleId)
                .leftJoin(AcRoleMenu.class, AcRoleMenu::getRoleId, AcRole::getRoleId)
                .leftJoin(AcMenu.class, AcMenu::getMenuId, AcRoleMenu::getMenuId)
                .eq(AcUser::getCustomerNumber, customerNumber).eq(AcMenu::getClientType, clientType)
                .eq(AcMenu::getStatus, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        UserPermitInfo userPermitInfo = acUserService.selectJoinOne(UserPermitInfo.class, wrapper);
        UserRolePermitInfo res = ReflectionUtil.copy(userPermitInfo, UserRolePermitInfo.class);
        res.setRoleInfoList(getRole(customerNumber));
        return res;
    }

    public UserPermitInfo getByRoleIdListAndClientType(List<Long> roleIdList, String clientType) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<AcUser>().selectAll(AcUser.class)
                .selectCollection(AcMenu.class, UserPermitInfo::getMenuList)
                .leftJoin(AcUserRole.class, AcUserRole::getUserId, AcUser::getUserId)
                .leftJoin(AcRole.class, AcRole::getRoleId, AcUserRole::getRoleId)
                .leftJoin(AcRoleMenu.class, AcRoleMenu::getRoleId, AcRole::getRoleId)
                .leftJoin(AcMenu.class, AcMenu::getMenuId, AcRoleMenu::getMenuId)
                .in(AcRole::getRoleId, roleIdList).eq(AcMenu::getClientType, clientType)
                .eq(AcMenu::getStatus, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        return acUserService.selectJoinOne(UserPermitInfo.class, wrapper);
    }

    public List<RoleInfo> getRole(String customerNumber) {
        MPJLambdaWrapper<AcRole> wrapper = new MPJLambdaWrapper<AcRole>().selectAll(AcRole.class)
                .rightJoin(AcUserRole.class, AcUserRole::getRoleId, AcRole::getRoleId)
                .rightJoin(AcUser.class, AcUser::getUserId, AcUserRole::getUserId)
                .eq(AcUser::getCustomerNumber, customerNumber);
        return acRoleService.selectJoinList(RoleInfo.class, wrapper);
    }

    public List<AcMenu> getMainMenu(String customerNumber, String clientType) {
        MPJLambdaWrapper<AcMenu> wrapper = getMenuRoleUserWrapper(customerNumber, clientType).eq(AcMenu::getMenuType,
                MenuTypeDict.MENU.getValue());
        return acMenuService.selectJoinList(AcMenu.class, wrapper);
    }

    private MPJLambdaWrapper<AcMenu> getMenuRoleUserWrapper(String customerNumber, String clientType) {
        return new MPJLambdaWrapper<AcMenu>().selectAll(AcMenu.class).distinct()
                .leftJoin(AcRoleMenu.class, AcRoleMenu::getMenuId, AcMenu::getMenuId)
                .leftJoin(AcRole.class, AcRole::getRoleId, AcRoleMenu::getRoleId)
                .leftJoin(AcUserRole.class, AcUserRole::getRoleId, AcRole::getRoleId)
                .leftJoin(AcUser.class, AcUser::getUserId, AcUserRole::getUserId)
                .eq(AcUser::getCustomerNumber, customerNumber).eq(AcMenu::getClientType, clientType)
                .eq(AcMenu::getStatus, CommonConst.YES).eq(AcMenu::getVisible, CommonConst.YES)
                .orderByAsc(AcMenu::getShowOrder);
    }

    public List<AcMenu> getSubMenu(String customerNumber, String clientType, Long menuId) {
        MPJLambdaWrapper<AcMenu> wrapper = getMenuRoleUserWrapper(customerNumber, clientType).eq(AcMenu::getGrandId,
                menuId).in(AcMenu::getMenuType, MenuTypeDict.SUB_MENU.getValue(), MenuTypeDict.BUTTON.getValue());
        return acMenuService.selectJoinList(AcMenu.class, wrapper);
    }

    public List<AcMenu> getContent(String customerNumber, String clientType, Long menuId) {
        MPJLambdaWrapper<AcMenu> wrapper = getMenuRoleUserWrapper(customerNumber, clientType).eq(AcMenu::getGrandId,
                menuId).eq(AcMenu::getMenuType, MenuTypeDict.SUB_MENU.getValue());
        return acMenuService.selectJoinList(AcMenu.class, wrapper);
    }

    public List<AcMenu> getAllMenu(String customerNumber, String clientType) {
        MPJLambdaWrapper<AcMenu> wrapper = getMenuRoleUserWrapper(customerNumber, clientType);
        return acMenuService.selectJoinList(AcMenu.class, wrapper);
    }

    public List<AcMenu> getAllMenuByUserId(String userId, String clientType) {
        MPJLambdaWrapper<AcMenu> wrapper = new MPJLambdaWrapper<AcMenu>().selectAll(AcMenu.class).distinct()
                .leftJoin(AcRoleMenu.class, AcRoleMenu::getMenuId, AcMenu::getMenuId)
                .leftJoin(AcRole.class, AcRole::getRoleId, AcRoleMenu::getRoleId)
                .leftJoin(AcUserRole.class, AcUserRole::getRoleId, AcRole::getRoleId)
                .leftJoin(AcUser.class, AcUser::getUserId, AcUserRole::getUserId).eq(AcUser::getUserId, userId)
                .eq(AcMenu::getClientType, clientType).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        return acMenuService.selectJoinList(AcMenu.class, wrapper);

    }
}
