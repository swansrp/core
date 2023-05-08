package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserRoleService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.login.RoleBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Title: RoleBindServiceImpl
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 16:27
 */
@Service
@RequiredArgsConstructor
public class RoleBindServiceImpl implements RoleBindService {
    public final AcUserRoleService acUserRoleService;
    private final AcUserService acUserService;

    @Override
    public void bindRoleByCustomerNumber(String customerNumber, Long roleId) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        bindRole(user.getUserId(), roleId);
    }

    @Override
    public void bindRole(Long userId, Long roleId) {
        acUserRoleService.bind(userId, roleId);
    }

    @Override
    public void unbindRoleByCustomerNumber(String customerNumber, Long roleId) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        unbindRole(user.getUserId(), roleId);
    }

    @Override
    public void unbindRole(Long userId, Long roleId) {
        acUserRoleService.bind(userId, roleId);
    }
}
