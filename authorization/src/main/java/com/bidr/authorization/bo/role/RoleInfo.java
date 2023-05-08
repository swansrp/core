package com.bidr.authorization.bo.role;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: RoleInfo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:07
 */
@Data
public class RoleInfo {

    @ApiModelProperty(value = "角色ID")
    private Long roleId;

    @ApiModelProperty(value = "角色名称")
    private String roleName;

    @ApiModelProperty(value = "角色权限字符串")
    private String roleKey;

    @ApiModelProperty(value = "数据范围DataPermitScopeDict")
    private Integer dataScope;


}
