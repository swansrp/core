package com.bidr.sms.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * Title: SendSmsVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 10:30
 */
@Data
public class SendSmsVO {
    @ApiModelProperty("短信签名")
    private String sign;
    @ApiModelProperty("手机号码")
    private String phoneNumber;
    @ApiModelProperty("短信模板id")
    private String templateCode;

    private Map<String, String> prop;
}
