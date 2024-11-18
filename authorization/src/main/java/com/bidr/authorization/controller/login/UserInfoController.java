package com.bidr.authorization.controller.login;

import com.bidr.authorization.service.group.UserGroupService;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.authorization.vo.user.UserExistedReq;
import com.bidr.authorization.vo.user.UserInfoRes;
import com.bidr.authorization.vo.user.UserRes;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: UserInfoController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 08:59
 */
@Api(tags = "系统基础 - 用户信息")
@RestController("UserInfoController")
@RequestMapping(value = "/web/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final UserInfoService userInfoService;
    private final UserGroupService userGroupService;

    @RequestMapping(value = "/info", method = RequestMethod.GET)
    public UserInfoRes getUserInfo() {
        return userInfoService.getUserInfo();
    }

    @RequestMapping(value = "/existed", method = RequestMethod.GET)
    public UserRes userExisted(UserExistedReq req) {
        return userInfoService.userExisted(req);
    }

    @RequestMapping(value = "/group/tree", method = RequestMethod.GET)
    public List<UserGroupTreeRes> getGroup(String groupName) {
        return userGroupService.getGroupTreeByUserDataScope(groupName);
    }
}
