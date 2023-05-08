package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.dao.mapper.AcUserRoleDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: AcUserRoleService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcUserRoleService extends BaseSqlRepo<AcUserRoleDao, AcUserRole> {

    public void bind(Long userId, Long roleId) {
        AcUserRole acUserRole = new AcUserRole();
        acUserRole.setUserId(userId);
        acUserRole.setRoleId(roleId);
        super.insertOrUpdate(acUserRole);
    }

    public void unbind(Long userId, Long roleId) {
        AcUserRole acUserRole = new AcUserRole();
        acUserRole.setUserId(userId);
        acUserRole.setRoleId(roleId);
        super.deleteByMultiId(acUserRole);
    }
}

