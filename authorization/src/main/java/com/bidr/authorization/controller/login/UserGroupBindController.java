package com.bidr.authorization.controller.login;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.join.UserGroupBindService;
import com.bidr.authorization.service.group.UserGroupService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: UserGroupBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 11:00
 */
@Api(tags = "用户组管理")
@RequiredArgsConstructor
@RestController("UserGroupBindController")
@RequestMapping(value = "/web/user/group")
public class UserGroupBindController extends BaseBindController<AcUser, AcUserGroup, AcGroup, AccountRes, AcGroup> {

    private final UserGroupBindService userGroupBindService;
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

    @Override
    protected BaseBindRepo<AcUser, AcUserGroup, AcGroup> bindRepo() {
        return userGroupBindService;
    }
}
