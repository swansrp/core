package com.bidr.authorization.controller.login;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.service.group.UserGroupService;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.AdminController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: UserGroupController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/04 17:49
 */
@Api(tags = "用户组管理")
@RequiredArgsConstructor
@RestController("UserGroupController")
@RequestMapping(value = "/web/user/group")
public class UserGroupController extends AdminController<AcGroup, AcGroup> {

    private final UserGroupService userGroupService;

    @ApiOperation(value = "获取全部用户组")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public List<UserGroupTreeRes> getGroup() {
        return userGroupService.getTree();
    }

    @ApiOperation(value = "添加用户组")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public void addGroup(@RequestBody AcGroup req) {
        userGroupService.addGroup(req);
        Resp.notice("添加用户组成功");
    }
}
