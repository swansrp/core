package com.bidr.admin.manage.user.service;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.admin.manage.user.vo.UserAdminRes;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import org.springframework.stereotype.Service;

/**
 * Title: UserGroupBindService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/01 11:43
 */
@Service
public class UserGroupBindService extends BaseBindRepo<AcUser, AcUserGroup, AcGroup, UserAdminRes, AcGroup> {

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
