package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AdminUserRoleBindService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 10:29
 */
@Service
@RequiredArgsConstructor
public class AdminUserRoleBindService {

    private final AcUserRoleMenuService acUserRoleMenuService;

    public List<MenuTreeRes> getUserMenuTree(String userId) {
        List<AcMenu> allMenu = acUserRoleMenuService.getAllMenuByUserId(userId, ClientType.WEB.getValue());
        if(FuncUtil.isNotEmpty(allMenu)) {
            for (AcMenu menu : allMenu) {
                if (FuncUtil.isEmpty(menu.getPid())) {
                    menu.setPid(menu.getGrandId());
                }
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, allMenu, AcMenu::getMenuId,
                AcMenu::getPid);
    }
}
