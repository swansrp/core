package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.service.admin.AdminRoleUserBindService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminRoleUserBindController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 16:59
 */
@Api(tags = "系统管理 - 角色-人员 - 绑定管理")
@RestController("AdminRoleUserBindController")
@RequestMapping(value = "/web-admin/role/user")
@RequiredArgsConstructor
public class AdminRoleUserBindController extends BaseBindController<AcRole, AcUserRole, AcUser, RoleRes, AccountRes> {

    private final AdminRoleUserBindService adminRoleUserBindService;

    @Override
    protected BaseBindRepo<AcRole, AcUserRole, AcUser, RoleRes, AccountRes> bindRepo() {
        return adminRoleUserBindService;
    }
}
