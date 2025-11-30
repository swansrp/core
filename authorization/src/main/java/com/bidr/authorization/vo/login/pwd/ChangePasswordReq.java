package com.bidr.authorization.vo.login.pwd;

import com.bidr.authorization.vo.captcha.ICaptchaVerificationReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChangePasswordReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/4/18 11:30
 */
@Data
public class ChangePasswordReq implements ICaptchaVerificationReq {
    @ApiModelProperty("用户名")
    private String loginId;
    @ApiModelProperty("老密码")
    private String oldPassword;
    @ApiModelProperty("新密码")
    private String password;
    @ApiModelProperty("密码确认")
    private String passwordConfirm;
    @ApiModelProperty("图形验证码")
    private String graphCode;
}
