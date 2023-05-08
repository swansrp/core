package com.bidr.authorization.dao.repository.join;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import org.springframework.stereotype.Service;

/**
 * Title: UserGroupBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 14:05
 */
@Service
public class UserGroupBindService extends BaseBindRepo<AcUser, AcUserGroup> {

    @Override
    protected SFunction<AcUserGroup, ?> bindMasterId() {
        return AcUserGroup::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> masterId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcUserGroup, ?> bindSlaveId() {
        return AcUserGroup::getGroupId;
    }
}
