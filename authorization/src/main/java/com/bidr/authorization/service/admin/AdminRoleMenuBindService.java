package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AdminRoleMenuBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 13:38
 */
@Service
public class AdminRoleMenuBindService extends BaseBindRepo<AcMenu, AcRoleMenu, AcRole> {
    @Override
    protected SFunction<AcRoleMenu, ?> bindMasterId() {
        return AcRoleMenu::getMenuId;
    }

    @Override
    protected SFunction<AcMenu, ?> masterId() {
        return AcMenu::getMenuId;
    }

    @Override
    protected SFunction<AcRoleMenu, ?> bindSlaveId() {
        return AcRoleMenu::getRoleId;
    }

    @Override
    protected SFunction<AcRole, ?> slaveId() {
        return AcRole::getRoleId;
    }
}
