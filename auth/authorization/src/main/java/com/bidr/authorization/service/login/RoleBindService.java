package com.bidr.authorization.service.login;

/**
 * Title: RoleBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 16:25
 */
public interface RoleBindService {
    /**
     * 绑定用户角色
     *
     * @param customerNumber 用户编码
     * @param roleId         角色id
     */
    void bindRoleByCustomerNumber(String customerNumber, Long roleId);

    /**
     * 绑定用户角色
     *
     * @param userId 用户id
     * @param roleId 角色id
     */
    void bindRole(Long userId, Long roleId);

    /**
     * 解除绑定用户角色
     *
     * @param customerNumber 用户编码
     * @param roleId         角色id
     */
    void unbindRoleByCustomerNumber(String customerNumber, Long roleId);

    /**
     * 解除绑定用户角色
     *
     * @param userId 用户id
     * @param roleId 角色id
     */
    void unbindRole(Long userId, Long roleId);
}
