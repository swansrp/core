package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import org.springframework.stereotype.Service;

/**
 * Title: AdminRoleUserBindService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 17:06
 */
@Service
public class AdminRoleUserBindService extends BaseBindRepo<AcRole, AcUserRole, AcUser, RoleRes, AccountRes> {
    @Override
    protected SFunction<AcUserRole, ?> bindAttachId() {
        return AcUserRole::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> attachId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcUserRole, ?> bindEntityId() {
        return AcUserRole::getRoleId;
    }

    @Override
    protected SFunction<AcRole, ?> entityId() {
        return AcRole::getRoleId;
    }
}
