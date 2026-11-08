package com.bidr.authorization.vo.tenantapp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Title: UnbindTenantAppReq
 * Description: 租户应用取消开通请求体（软删置 valid='0'，行不存在静默幂等）。
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Data
@ApiModel(description = "租户应用取消开通申请")
public class UnbindTenantAppReq {

    @ApiModelProperty(value = "租户id（ac_tenant.id）")
    @NotNull(message = "租户id不能为null")
    private Long tenantId;

    @ApiModelProperty(value = "应用编码（ac_subsystem.app_code）")
    @NotBlank(message = "应用编码不能为空")
    @Size(max = 64, message = "应用编码最大长度要小于 64")
    private String appCode;
}
