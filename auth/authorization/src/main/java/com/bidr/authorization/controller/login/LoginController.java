package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.annotation.msg.MsgCodeVerify;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.login.PasswordService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.*;
import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.authorization.vo.login.pwd.InitPasswordReq;
import com.bidr.authorization.vo.token.TokenReq;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import com.bidr.authorization.controller.oauth2.OAuth2IdpController;
import javax.servlet.http.HttpServletResponse;
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
    public LoginRes login(@Validated LoginReq req, HttpServletResponse response) {
        LoginRes res = loginService.login(req.getLoginId(), req.getPassword());
        afterLogin(res);
        // 会话 cookie（T1.3 浏览器腿）：SSO 授权入口 /web/oauth2/authorize 是**整页导航**，
        // 浏览器带不上 localStorage 里的 Authorization 头 ⇒ 该端点按 cookie 回退取票，
        // 否则一律 302 回登录页、登录后再跳 authorize 仍无头＝死循环。
        // 只种给登录成功路径；口径见 OAuth2IdpController#SESSION_COOKIE。
        if (FuncUtil.isNotEmpty(res.getAccessToken())) {
            // 手写 Set-Cookie 而不用 Cookie#setAttribute：javax Cookie 的 SameSite 属性需 Servlet 4.0，
            // 本 reactor 编译期仍是老 servlet-api，直接拼头最稳（值＝Base64 信封，无 ; / , 风险）。
            response.addHeader("Set-Cookie", OAuth2IdpController.SESSION_COOKIE + "="
                    + res.getAccessToken() + "; Path=/; HttpOnly; SameSite=Lax");
        }
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
    @ApiOperation("变更密码")
    @CaptchaVerify("PASSWORD_CHANGE_CAPTCHA")
    @RequestMapping(value = "/password/change", method = RequestMethod.POST)
    public LoginRes changePassword(@Validated ChangePasswordReq req) {
        passwordService.changePassword(req);
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

    @Auth(AuthToken.class)
    @RequestMapping(value = "/register/msg", method = RequestMethod.POST)
    @MsgCodeVerify("REGISTER_MSG_CODE")
    public LoginRes regAndLogin(@Validated MsgRegReq req) {
        LoginRes res = loginService.registerWithPhoneNumber(req.getLoginId(), req.getPassword(),
                req.getPhoneNumber(), req.getTenantCode());
        afterLogin(res);
        return res;
    }

    @Auth(AuthToken.class)
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @CaptchaVerify("REGISTER_CAPTCHA")
    public LoginRes register(@Validated LoginReq req) {
        LoginRes res = loginService.register(req.getLoginId(), req.getPassword(), req.getTenantCode());
        afterLogin(res);
        return res;
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "获取生效的自助注册模式", notes = "open/tenant/closed，三级链与注册闸同一读取点")
    @RequestMapping(value = "/register-mode", method = RequestMethod.GET)
    public String registerMode() {
        return loginService.getRegisterMode();
    }

    @RequestMapping(value = "/refresh", method = RequestMethod.POST)
    public LoginRes refresh(@RequestBody @Validated TokenReq req) {
        LoginRes res = loginService.refreshLogin(req.getToken());
        afterLogin(res);
        return res;
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "通过扫码信息进行登录", notes = "通过扫码信息进行登录")
    @RequestMapping(value = "/qrCode", method = RequestMethod.POST)
    public LoginRes loginByQrCode(@RequestBody QrCodeReq req) {
        LoginRes res = loginService.loginByQrCode(req);
        afterLogin(res);
        return res;
    }
}
