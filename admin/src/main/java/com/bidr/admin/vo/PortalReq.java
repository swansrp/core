package com.bidr.admin.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * Title: PortalReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:12
 */
@Data
public class PortalReq {
    @NotEmpty(message = "表格名称不能为空")
    @ApiModelProperty("管理表格名称")
    private String name;

    @ApiModelProperty("对应角色")
    private Long roleId;
}
