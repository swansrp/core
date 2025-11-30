package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.login.PasswordService;
import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.authorization.vo.login.pwd.ResetPasswordReq;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: LoginController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:17
 */
@Api(tags = "系统基础 - 密码操作")
@RestController("PasswordController")
@RequestMapping(value = "/web/password")
public class PasswordController {

    @Resource
    protected PasswordService passwordService;

    @ApiOperation("重置密码")
    @RequestMapping(value = "/reset", method = RequestMethod.POST)
    public void resetPassword(@Validated @RequestBody ResetPasswordReq req) {
        passwordService.resetPassword(req.getCustomerNumber());
        Resp.notice("密码已重置");
    }

    @ApiOperation("变更密码")
    @RequestMapping(value = "/change", method = RequestMethod.POST)
    public void changePassword(@Validated @RequestBody ChangePasswordReq req) {
        passwordService.changePassword(req);
        Resp.notice("密码已变更");
    }

}
