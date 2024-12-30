/**
 * Title: MsgLoginReq.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @description Project Name: Grote
 * @Package: com.srct.service.account.vo
 * @since 2019-7-31 21:46
 */
package com.bidr.authorization.vo.login;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class MsgRegReq extends MsgLoginReq {

    @NotBlank(message = "用户名不能为空")
    @ApiModelProperty(value = "用户名")
    private String loginId;

    @NotBlank(message = "密码不能为空")
    @ApiModelProperty(value = "密码")
    private String password;
}
