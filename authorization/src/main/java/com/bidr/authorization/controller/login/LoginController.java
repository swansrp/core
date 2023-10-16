package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.vo.login.LoginReq;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.token.TokenReq;
import com.bidr.kernel.utils.FuncUtil;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: LoginController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:17
 */
@Api(tags = "系统基础 - 登录操作")
@RestController("LoginController")
@RequestMapping(value = "/web/login")
public class LoginController {

    @Resource
    protected LoginService loginService;

    @Autowired(required = false)
    private List<LoginFillTokenInf> fillTokenInfList;

    @Auth(AuthToken.class)
    @RequestMapping(value = "", method = RequestMethod.POST)
    @CaptchaVerify("LOGIN_CAPTCHA")
    public LoginRes login(@Validated LoginReq req) {
        LoginRes res = loginService.login(req.getLoginId(), req.getPassword());
        afterLogin(res);
        return res;
    }

    protected void afterLogin(LoginRes res) {
        if (FuncUtil.isNotEmpty(fillTokenInfList)) {
            for (LoginFillTokenInf loginFillTokenInf : fillTokenInfList) {
                loginFillTokenInf.fillToken(res);
            }
        }
    }

    @Auth(AuthToken.class)
    @RequestMapping(value = "/msg", method = RequestMethod.POST)
    @CaptchaVerify("LOGIN_MSG_CODE_CAPTCHA")
    public LoginRes login(String phoneNumber) {
        LoginRes res = loginService.loginOrReg(phoneNumber);
        afterLogin(res);
        return res;
    }

    @RequestMapping(value = "/refresh", method = RequestMethod.POST)
    public LoginRes refresh(@RequestBody @Validated TokenReq req) {
        LoginRes res = loginService.refreshLogin(req.getToken());
        afterLogin(res);
        return res;
    }
}
