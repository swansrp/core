package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;

/**
 * Title: AdminRoleMenuBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 13:38
 */
public class AdminRoleMenuBindService extends BaseBindRepo<AcRole, AcRoleMenu> {
    @Override
    protected SFunction<AcRoleMenu, ?> bindMasterId() {
        return AcRoleMenu::getRoleId;
    }

    @Override
    protected SFunction<AcRole, ?> masterId() {
        return AcRole::getRoleId;
    }

    @Override
    protected SFunction<AcRoleMenu, ?> bindSlaveId() {
        return AcRoleMenu::getMenuId;
    }
}
