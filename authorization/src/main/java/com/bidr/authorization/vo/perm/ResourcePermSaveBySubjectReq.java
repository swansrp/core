package com.bidr.authorization.vo.perm;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 通用资源权限按主体保存请求（反向配置）
 * <p>
 * 全量设置某主体对哪些资源有权限，后端按 diff 增删该主体的授权行。
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Data
public class ResourcePermSaveBySubjectReq {

    @NotBlank(message = "资源类型不能为空")
    @ApiModelProperty(value = "资源类型（表名）", required = true)
    private String resourceType;

    @NotNull(message = "主体类型不能为空")
    @ApiModelProperty(value = "授权主体类型（0=角色 1=用户 2=用户组 3=部门）", required = true)
    private Integer subjectType;

    @NotBlank(message = "主体ID不能为空")
    @ApiModelProperty(value = "主体标识", required = true)
    private String subjectId;

    @NotNull(message = "资源ID列表不能为null")
    @ApiModelProperty(value = "该主体有权限的资源ID列表（全量）", required = true)
    private List<String> resourceIds;
}
