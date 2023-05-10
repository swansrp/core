package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import org.springframework.stereotype.Service;

/**
 * Title: AdminUserGroupBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 14:05
 */
@Service
public class AdminUserGroupBindService extends BaseBindRepo<AcGroup, AcUserGroup, AcUser> {

    @Override
    protected SFunction<AcUserGroup, ?> bindAttachId() {
        return AcUserGroup::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> attachId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcUserGroup, ?> bindEntityId() {
        return AcUserGroup::getGroupId;
    }

    @Override
    protected SFunction<AcGroup, ?> entityId() {
        return AcGroup::getId;
    }
}
