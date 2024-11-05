package com.bidr.admin.manage.user.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.manage.user.vo.UserAdminRes;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminUserController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/29 10:31
 */
@Api(tags = "系统管理 - 用户管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/user/AcGroup"})
public class AdminUserGroupBindController extends BaseBindController<AcUser, AcUserGroup, AcGroup, UserAdminRes,
        AcGroup> {

    @Override
    protected SFunction<AcUserGroup, ?> bindEntityId() {
        return AcUserGroup::getUserId;
    }

    @Override
    protected SFunction<AcUserGroup, ?> bindAttachId() {
        return AcUserGroup::getGroupId;
    }

    @Override
    protected SFunction<AcGroup, ?> attachId() {
        return AcGroup::getId;
    }

    @Override
    protected SFunction<AcUser, ?> entityId() {
        return AcUser::getUserId;
    }
}
