package com.bidr.authorization.controller.login;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.join.UserGroupBindService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: UserGroupBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/06 11:00
 */
@Api(tags = "用户组管理")
@RequiredArgsConstructor
@RestController("UserGroupBindController")
@RequestMapping(value = "/web/user/group")
public class UserGroupBindController extends BaseBindController<AcUser, AccountRes, AcUserGroup> {

    private final UserGroupBindService userGroupBindService;

    @Override
    protected BaseBindRepo<AcUser, AcUserGroup> bindRepo() {
        return userGroupBindService;
    }
}
