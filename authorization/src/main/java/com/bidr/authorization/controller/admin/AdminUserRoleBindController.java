package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.service.admin.AdminUserRoleBindService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AdminUserController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/20 11:48
 */
@Api(tags = "系统管理 - 人员-角色 - 绑定管理")
@RestController("AdminUserRoleBindController")
@RequestMapping(value = "/web/admin/user/role")
@RequiredArgsConstructor
public class AdminUserRoleBindController extends BaseBindController<AcUser, AcUserRole, AcRole, AccountRes, RoleRes> {

    private final AdminUserRoleBindService adminUserRoleBindService;

    @Override
    protected SFunction<AcUserRole, ?> bindAttachId() {
        return AcUserRole::getRoleId;
    }

    @Override
    protected SFunction<AcRole, ?> attachId() {
        return AcRole::getRoleId;
    }

    @Override
    protected SFunction<AcUserRole, ?> bindEntityId() {
        return AcUserRole::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> entityId() {
        return AcUser::getUserId;
    }

    @ApiOperation(value = "获取人员对应菜单树", notes = "全部")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(String userId) {
        return adminUserRoleBindService.getUserMenuTree(userId);
    }
}
