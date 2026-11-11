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
import com.bidr.authorization.utils.SessionCookieUtil;
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

    /**
     * 登录成功统一收尾：fillToken 链 + 会话 cookie + 登录日志。
     *
     * <p>🔴 会话 cookie 必须在这里种（而不是逐个端点）：authorize 是整页导航，只认 cookie 回退，
     * 而本项目所有登录成功腿——密码登录／首设密／注册／短信登录／扫码／改密／refresh／SSO 换取
     * （pack-bidr {@code POST /web/auth/login}）／超管幽灵登录——都必经本方法。漏任何一条腿都会
     * 出现"门户已登录、子系统 302 或身份错位"（2026-11-11 test e2e 实证，见
     * {@link com.bidr.authorization.utils.SessionCookieUtil} 类注释）。</p>
     */
    protected void afterLogin(LoginRes res) {
        if (FuncUtil.isNotEmpty(fillTokenInfList)) {
            for (LoginFillTokenInf loginFillTokenInf : fillTokenInfList) {
                loginFillTokenInf.fillToken(res);
            }
        }
        // fillToken 之后再种：cookie 里的票必须与最终返回给前端的票一致
        SessionCookieUtil.write(res);
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
    @ApiOperation(value = "获取生效的自助注册模式", notes = "open/tenant/closed，只读 yml my.auth.register-mode，与注册闸同一读取点")
    @RequestMapping(value = "/register-mode", method = RequestMethod.GET)
    public String registerMode() {
        return loginService.getRegisterMode();
    }

    // refresh 以票自证：access token 过期后才会来续期，端点不能再要求有效 access token
    @Auth(AuthNone.class)
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
