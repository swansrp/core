package com.bidr.authorization.vo.permit;


import io.swagger.annotations.ApiModelProperty;
import lombok.*;

/**
 * @author Sharp
 * @since 2026/2/5 16:19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPermitRes {
    /** 来源类型：USER / ROLE / DEPT / GROUP */
    private PermitSourceType sourceType;

    /** 来源ID（userId / roleId / deptId / groupId） */
    private Long sourceId;

    /** 来源名称（用户名 / 角色名 / 部门名 / 用户组名） */
    private String sourceName;

    /** 人类可读的授权路径描述 */
    private String path;

    @RequiredArgsConstructor
    @Getter
    public enum PermitSourceType {
        /**
         *
         */
        ROLE("角色"),
        DEPT("部门"),
        DEPT_DATA_SCOPE("部门继承"),
        GROUP("用户组"),
        GROUP_DATA_SCOPE("用户组继承"),
        USER("用户"),
        ;

        private final String description;
    }
}
