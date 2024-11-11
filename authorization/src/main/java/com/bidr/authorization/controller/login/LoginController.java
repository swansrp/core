package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.annotation.msg.MsgCodeVerify;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.login.PasswordService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginReq;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.login.MsgLoginReq;
import com.bidr.authorization.vo.login.pwd.InitPasswordReq;
import com.bidr.authorization.vo.token.TokenReq;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Title: LoginController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:17
 */
@Slf4j
@Api(tags = "系统基础 - 登录操作")
@RestController("LoginController")
@RequestMapping(value = "/web/login")
public class LoginController {

    @Resource
    protected LoginService loginService;
    @Resource
    protected PasswordService passwordService;
    @Resource
    private TokenService tokenService;


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
        Map<String, Object> tokenValue = tokenService.getTokenValue();
        log.info("[登录信息]用户: {}, 登录token: {}, 内容: {}", res.getCustomerNumber(), res.getAccessToken(),
                JsonUtil.toJson(tokenValue, false, false, true));
    }

    @Auth(AuthToken.class)
    @RequestMapping(value = "/password/init", method = RequestMethod.POST)
    @CaptchaVerify("INIT_PASSWORD_CAPTCHA")
    public LoginRes initPassword(@Validated InitPasswordReq req) {
        passwordService.initPassword(req);
        LoginRes res = loginService.login(req.getLoginId(), req.getPassword());
        afterLogin(res);
        return res;
    }

    @Auth(AuthToken.class)
    @RequestMapping(value = "/msg", method = RequestMethod.POST)
    @MsgCodeVerify("LOGIN_MSG_CODE")
    public LoginRes login(@Validated MsgLoginReq req) {
        LoginRes res = loginService.loginOrReg(req.getPhoneNumber());
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
