package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.service.admin.AdminUserDeptBindService;
import com.bidr.authorization.vo.department.*;
import com.bidr.authorization.vo.user.UserRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

/**
 * 部门-用户绑定控制器
 *
 * @author sharp
 */
@Api(tags = "系统管理 - 部门-人员 - 绑定管理")
@RestController("AdminUserDeptBindController")
@RequestMapping(value = "/web/admin/dept/user")
@RequiredArgsConstructor
public class AdminUserDeptBindController extends BaseBindController<AcDept, AcUserDept, AcUser, DepartmentItem, DepartmentAccountRes> {

    private final AdminUserDeptBindService adminUserDeptBindService;

    @Override
    protected BaseBindRepo<AcDept, AcUserDept, AcUser, DepartmentItem, DepartmentAccountRes> bindRepo() {
        return adminUserDeptBindService;
    }

    @Override
    protected SFunction<AcUserDept, ?> bindEntityId() {
        return AcUserDept::getDeptId;
    }

    @Override
    protected SFunction<AcUserDept, ?> bindAttachId() {
        return AcUserDept::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> attachId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcDept, ?> entityId() {
        return AcDept::getDeptId;
    }

    @ApiOperation(value = "绑定（带数据权限）")
    @RequestMapping(value = "/bind/dataScope", method = RequestMethod.POST)
    public void bind(@RequestBody @Validated BindDeptUserDataScopeReq req) {
        adminUserDeptBindService.bind(req);
        Resp.notice("绑定成功");
    }

    @ApiOperation(value = "批量绑定（带数据权限）")
    @RequestMapping(value = "/bind/batch/dataScope", method = RequestMethod.POST)
    public void bind(@RequestBody @Validated BindDeptUserListDataScopeReq req) {
        adminUserDeptBindService.bindList(req);
        Resp.notice("绑定成功");
    }

    @ApiOperation(value = "获取已绑定用户列表（查询条件）")
    @RequestMapping(value = "/bind/list/search", method = RequestMethod.GET)
    public List<DepartmentAccountRes> getBindList(@Validated BindDeptUserReq req) {
        List<DepartmentAccountRes> res = adminUserDeptBindService.searchBindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiOperation(value = "更改用户数据权限")
    @RequestMapping(value = "/data/scope", method = RequestMethod.POST)
    public void dataScope(@RequestBody AcUserDept req) {
        adminUserDeptBindService.updateAcUserDept(req);
        Resp.notice("更改用户数据权限成功");
    }

    @ApiOperation(value = "获取指定部门下所有用户")
    @RequestMapping(value = "/bind/list/all", method = RequestMethod.GET)
    public Collection<DepartmentAccountRes> getUserListByDeptId(String deptId) {
        return Resp.convert(adminUserDeptBindService.getUserListByDeptId(deptId), DepartmentAccountRes.class);
    }
}
