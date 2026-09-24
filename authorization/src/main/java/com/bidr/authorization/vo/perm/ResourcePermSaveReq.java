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
    @ApiModelProperty(value = "资源标识（表主键或 Portal 名称）", required = true)
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

        /**
         * 扩展信息JSON，随授权行整条覆盖保存；本模块不解析其结构（同 ac_group_bind.extra_data 的约定），
         * 行级权限条件等语义由消费侧（forge）解释
         */
        @ApiModelProperty(value = "扩展信息JSON（可空）")
        private String extraData;
    }
}
