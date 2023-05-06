package com.bidr.authorization.vo.login;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Title: EmailRegReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/4/17 20:43
 * @description Project Name: Mall
 * @Package: com.srct.service.account.vo.login
 */
@Data
public class EmailRegReq {
    @NotBlank(message = "用户标识不能为空")
    @ApiModelProperty(value = "用户标识")
    private String id;

    @NotBlank(message = "邮箱不能为空")
    @ApiModelProperty(value = "邮箱")
    private String email;

    @NotBlank(message = "图形验证码不能为空")
    @ApiModelProperty(value = "图形验证码")
    private String graphCode;
}
