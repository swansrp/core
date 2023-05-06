package com.bidr.authorization.vo.login.pwd;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChangePasswordReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/4/18 11:30
 * @description Project Name: Mall
 * @Package: com.srct.service.account.vo.login
 */
@Data
public class ChangePasswordReq {
    @ApiModelProperty("更换密码token")
    private String token;
    @ApiModelProperty("设置密码账户")
    private String userId;
    @ApiModelProperty("新密码")
    private String password;
    @ApiModelProperty("密码确认")
    private String passwordConfirm;
}
