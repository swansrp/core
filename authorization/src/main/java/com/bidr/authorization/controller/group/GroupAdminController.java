package com.bidr.authorization.controller.group;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.service.group.UserGroupService;
import com.bidr.authorization.vo.group.GroupRes;
import com.bidr.authorization.vo.group.GroupTypeRes;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminTreeController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: GroupAdminController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 09:56
 */
@Api(tags = "系统管理 - 用户组-用户 - 绑定管理")
@RequiredArgsConstructor
@RestController("GroupAdminController")
@RequestMapping(value = "/web/group/user")
public class GroupAdminController extends BaseAdminTreeController<AcGroup, GroupRes> {

    private final UserGroupService userGroupService;

    @ApiOperation(value = "获取全部用户组类型")
    @RequestMapping(value = "/type", method = RequestMethod.GET)
    public List<GroupTypeRes> getGroupType(String name) {
        return userGroupService.getGroupType(name);
    }


    @ApiOperation(value = "获取全部用户组(列表)")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<GroupRes> getGroupList(String groupType) {
        return userGroupService.getList(groupType);
    }

    @ApiOperation(value = "获取全部用户组(树)")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<UserGroupTreeRes> getGroupTree(String groupType) {
        return userGroupService.getTree(groupType);
    }

    @ApiOperation(value = "添加用户组")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public void addGroup(@RequestBody AcGroup req) {
        userGroupService.addGroup(req);
        Resp.notice("添加用户组成功");
    }

    @Override
    protected SFunction<AcGroup, ?> id() {
        return AcGroup::getId;
    }

    @Override
    protected SFunction<AcGroup, Integer> order() {
        return AcGroup::getDisplayOrder;
    }

    @Override
    protected SFunction<AcGroup, ?> pid() {
        return AcGroup::getPid;
    }
}
