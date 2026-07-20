package com.bidr.authorization.vo.perm;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 通用资源权限保存请求
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Data
public class ResourcePermSaveReq {

    @NotBlank(message = "资源类型不能为空")
    @ApiModelProperty(value = "资源类型（表名）", required = true)
    private String resourceType;

    @NotBlank(message = "资源ID不能为空")
    @ApiModelProperty(value = "资源ID（表主键）", required = true)
    private String resourceId;

    @Valid
    @NotNull(message = "权限列表不能为null")
    @ApiModelProperty(value = "授权列表", required = true)
    private List<PermItem> perms;

    @Data
    public static class PermItem {

        @NotNull(message = "主体类型不能为空")
        @ApiModelProperty(value = "授权主体类型（0=角色 1=用户 2=用户组 3=部门）", required = true)
        private Integer subjectType;

        @NotBlank(message = "主体ID不能为空")
        @ApiModelProperty(value = "主体标识", required = true)
        private String subjectId;
    }
}
