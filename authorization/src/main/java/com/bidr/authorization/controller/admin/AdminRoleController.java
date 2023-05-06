package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.service.admin.AdminRoleService;
import com.bidr.authorization.vo.admin.QueryRoleReq;
import com.bidr.authorization.vo.admin.RoleReq;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.AdminController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: AdminRoleController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/20 11:48
 */
@Api(tags = "系统角色管理")
@RestController("AdminRoleController")
@RequestMapping(value = "/web/role/admin")
public class AdminRoleController extends AdminController<AcRole, RoleRes> {

    @Resource
    private AdminRoleService adminRoleService;

    @ApiOperation(value = "获取角色列表", notes = "全部")
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Page<RoleRes> queryRole(@RequestBody QueryRoleReq req) {
        return adminRoleService.queryRole(req);
    }

    @ApiOperation(value = "创建角色", notes = "全部")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public void addRole(@RequestBody RoleReq req) {
        adminRoleService.addRole(req);
        Resp.notice("创建角色成功");
    }

}
