package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.service.admin.AdminRoleMenuBindService;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: AdminRoleMenuBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 13:36
 */
@Api(tags = "系统角色管理")
@RestController("AdminRoleMenuBindController")
@RequestMapping(value = "/web/role/admin")
public class AdminRoleMenuBindController extends BaseBindController<AcRole, RoleRes, AcRoleMenu> {
    @Resource
    private AdminRoleMenuBindService adminRoleMenuBindService;

    @Override
    protected BaseBindRepo bindRepo() {
        return adminRoleMenuBindService;
    }
}
