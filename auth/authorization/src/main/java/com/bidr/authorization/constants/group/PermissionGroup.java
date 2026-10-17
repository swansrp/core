package com.bidr.authorization.constants.group;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Sharp
 * @since 2026/3/20 09:14
 */

@Getter
@AllArgsConstructor
public enum PermissionGroup implements Group {
    /**
     * 用户分组
     */
    PERMISSION_GROUP("权限用户组");

    private final String remark;
}