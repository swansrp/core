package com.bidr.authorization.controller.group;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.service.admin.AdminUserGroupBindService;
import com.bidr.authorization.vo.group.BindUserReq;
import com.bidr.authorization.vo.group.GroupAccountRes;
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

import java.util.List;

/**
 * Title: GroupUserBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 11:00
 */
@Api(tags = "系统管理 - 用户组-用户 - 绑定管理")
@RequiredArgsConstructor
@RestController("GroupUserBindController")
@RequestMapping(value = "/web/group/user")
public class GroupUserBindController extends BaseBindController<AcGroup, AcUserGroup, AcUser, AcGroup,
        GroupAccountRes> {

    private final AdminUserGroupBindService adminUserGroupBindService;

    @Override
    protected BaseBindRepo<AcGroup, AcUserGroup, AcUser, AcGroup, GroupAccountRes> bindRepo() {
        return adminUserGroupBindService;
    }

    @ApiOperation(value = "获取已绑定(列表)(查询条件)")
    @RequestMapping(value = "/bind/list/search", method = RequestMethod.GET)
    public List<GroupAccountRes> getBindList(@Validated BindUserReq req) {
        List<GroupAccountRes> res = adminUserGroupBindService.searchBindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiOperation(value = "更改用户数据权限")
    @RequestMapping(value = "/data/scope", method = RequestMethod.POST)
    public void dataScope(@RequestBody AcUserGroup req) {
        adminUserGroupBindService.updateAcUserGroup(req);
        Resp.notice("更改用户数据权限成功");
    }
}
