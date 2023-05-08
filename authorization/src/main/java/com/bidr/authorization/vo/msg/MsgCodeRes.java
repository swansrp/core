package com.bidr.authorization.vo.msg;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: MsgCodeRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/23 15:43
 */
@Data
public class MsgCodeRes {
    @ApiModelProperty("短信验证码获取间隔")
    private Integer internal;
    @ApiModelProperty("短信验证码过期时间")
    private Integer timeout;
    @ApiModelProperty(value = "短信验证码长度")
    private Integer length;
}
