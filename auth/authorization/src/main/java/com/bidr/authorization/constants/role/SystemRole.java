package com.bidr.authorization.constants.role;

/**
 * 系统内置角色接口
 * 实现此接口的枚举会在应用启动时自动同步到 ac_role 表
 * 系统角色 status = -1，不可删除、不可编辑
 *
 * @author Sharp
 */
public interface SystemRole {
    /**
     * 角色标识（对应 ac_role.role_key）
     */
    String roleKey();

    /**
     * 角色名称
     */
    String roleName();

    /**
     * 显示顺序
     */
    default int displayOrder() {
        return 0;
    }

    /**
     * 备注
     */
    default String remark() {
        return "";
    }
}
