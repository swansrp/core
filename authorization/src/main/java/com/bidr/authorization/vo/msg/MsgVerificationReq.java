package com.bidr.authorization.vo.msg;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: MsgVerificationReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/24 11:12
 */
@Data
public class MsgVerificationReq implements IMsgVerificationReq {
    @ApiModelProperty(value = "用户名")
    private String phoneNumber;

    @ApiModelProperty(value = "短信验证码")
    private String msgCode;
}
