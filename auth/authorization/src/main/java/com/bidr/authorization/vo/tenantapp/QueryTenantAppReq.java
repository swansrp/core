package com.bidr.authorization.vo.tenantapp;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

/**
 * Title: QueryTenantAppReq
 * Description: 租户应用开通 分页查询请求体（按 tenantId 列开通行）。
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryTenantAppReq extends QueryReqVO {

    @ApiModelProperty(value = "租户id（ac_tenant.id）")
    @NotNull(message = "租户id不能为null")
    private Long tenantId;
}
