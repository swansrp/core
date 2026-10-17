package com.bidr.admin.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Title: PortalRoleBindReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/11 09:04
 */
@Data
public class PortalRoleBindReq {
    @NotNull(message = "新绑定角色id不能为空")
    @ApiModelProperty("新绑定角色id")
    private Long roleId;

    @ApiModelProperty("模版角色id")
    private Long templateRoleId;

    @ApiModelProperty("指定复制的portal名称列表；为空时复制模版角色的全部配置")
    private List<String> portalNames;
}
