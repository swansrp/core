package com.bidr.authorization.vo.tenantapp;

import com.bidr.authorization.dao.entity.AcTenantApp;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryTenantAppRes
 * Description: 租户应用开通 列表行（照 QuerySubsystemRes 形态：实体平铺＋两步查出的补充列）。
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryTenantAppRes extends AcTenantApp {

    @ApiModelProperty(value = "应用名称（经 app_code 两步查出，展示用）")
    private String appName;
}
