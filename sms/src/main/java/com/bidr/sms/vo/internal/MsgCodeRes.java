package com.bidr.sms.vo.internal;

import com.bidr.sms.vo.SendSmsRes;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: MsgCodeRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/23 15:43
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgCodeRes extends SendSmsRes {
    @ApiModelProperty("短信验证码获取间隔")
    private Integer internal;
    @ApiModelProperty("短信验证码过期时间")
    private Integer timeout;
    @ApiModelProperty(value = "短信验证码长度")
    private Integer length;
}
