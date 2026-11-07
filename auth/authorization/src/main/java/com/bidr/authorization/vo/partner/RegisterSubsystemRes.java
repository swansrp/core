package com.bidr.authorization.vo.partner;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Title: RegisterSubsystemRes
 * Description: 渠道注册子系统响应＝PartnerRes（appKey/appSecret 一次性展示语义不变）＋appCode 回显。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RegisterSubsystemRes extends PartnerRes {

    @ApiModelProperty(value = "应用编码（回显，同请求体）")
    private String appCode;

    public RegisterSubsystemRes(String appKey, String appSecret, String appCode) {
        super(appKey, appSecret);
        this.appCode = appCode;
    }
}
