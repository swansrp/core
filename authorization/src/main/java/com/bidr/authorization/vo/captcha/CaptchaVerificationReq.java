package com.bidr.authorization.vo.captcha;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: CaptchaVerificationReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/24 11:44
 */
@Data
public class CaptchaVerificationReq implements ICaptchaVerificationReq {
    @ApiModelProperty(value = "图形验证码")
    private String graphCode;
}
