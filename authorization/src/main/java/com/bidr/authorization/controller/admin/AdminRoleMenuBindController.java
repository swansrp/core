package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.service.admin.AdminRoleMenuBindService;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AdminRoleMenuBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 13:36
 */
@Api(tags = "系统管理 - 角色 - 权限 - 绑定管理")
@RestController("AdminRoleMenuBindController")
@RequestMapping(value = "/web/admin/role/menu")
@RequiredArgsConstructor
public class AdminRoleMenuBindController extends BaseBindController<AcRole, AcRoleMenu, AcMenu, RoleRes, AcMenu> {

    private final AdminRoleMenuBindService adminRoleMenuBindService;

    @ApiOperation(value = "获取角色对应菜单树", notes = "全部")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(Long entityId) {
        List<AcMenu> bindList = adminRoleMenuBindService.getBindList(entityId);
        for (AcMenu acMenu : bindList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, bindList, AcMenu::getMenuId,
                AcMenu::getPid);
    }

    @Override
    protected BaseBindRepo<AcRole, AcRoleMenu, AcMenu, RoleRes, AcMenu> bindRepo() {
        return adminRoleMenuBindService;
    }
}
