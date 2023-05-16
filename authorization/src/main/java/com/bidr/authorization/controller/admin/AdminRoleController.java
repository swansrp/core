package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.service.admin.AdminRoleService;
import com.bidr.authorization.vo.admin.QueryRoleReq;
import com.bidr.authorization.vo.admin.RoleReq;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.AdminController;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminRoleController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 09:51
 */
@Api(tags = "系统管理 - 角色 - 权限管理")
@RestController("AdminRoleController")
@RequestMapping(value = "/web-admin/role/menu")
@RequiredArgsConstructor
public class AdminRoleController extends AdminController<AcRole, RoleRes> {

    private final AdminRoleService adminRoleService;

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

    @Override
    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public void delete(@RequestBody IdReqVO vo) {
        adminRoleService.deleteRole(vo.getId());
        Resp.notice("删除成功");
    }
}
