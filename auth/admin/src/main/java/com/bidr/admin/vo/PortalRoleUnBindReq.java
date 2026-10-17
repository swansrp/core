package com.bidr.admin.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: PortalRoleBindReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/11 09:04
 */
@Data
public class PortalRoleUnBindReq {
    @NotNull(message = "解除绑定角色id不能为空")
    @ApiModelProperty("解除绑定角色id")
    private Long roleId;
}
