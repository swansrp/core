package com.bidr.authorization.vo.msg;

import com.bidr.authorization.vo.captcha.ICaptchaVerificationReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Title: MsgCodeReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/23 15:42
 */
@Data
public class MsgCodeReq implements ICaptchaVerificationReq {
    @NotBlank(message = "手机号码不能为空")
    @ApiModelProperty(value = "手机号码")
    private String phoneNumber;
    @NotBlank(message = "短信验证码类型不能为空")
    @ApiModelProperty(value = "短信验证码类型")
    private String msgCodeType;
    @NotBlank(message = "图形验证码不能为空")
    @ApiModelProperty(value = "图形验证码")
    private String graphCode;
}
