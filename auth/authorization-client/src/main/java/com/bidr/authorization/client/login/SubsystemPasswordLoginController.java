package com.bidr.authorization.client.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.vo.login.LoginRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: SubsystemPasswordLoginController
 * Description: 子系统账密登录端点（{@code POST /web/login}）——T3.1 自 epc 登录面抽象上提，
 * 让「子系统找主系统登录」成为 core client 的标准能力。
 *
 * <p>🔴 与主系统互斥：主系统（墨枢）由 server 侧 {@code LoginController} 独占同一 URL
 * （classpath 上有它即本类退位），本类只在子系统形态（无 server 登录件）注册。</p>
 *
 * <p>🔴 类级 {@code @ConditionalOnBean(SubsystemLoginService.class)} 在组件扫描期恒为 false
 * （登录 service 只由 {@code SubsystemSessionAutoConfiguration} 的 {@code @Bean} 注册）⇒
 * 本类统一由自动配置的 {@code @Bean} 注册，扫描路径恒不装配、不会出现双实例；宿主自定义
 * {@code SubsystemSessionService} bean（自动配置整体退位）时本端点不注册，而不是启动期
 * 因缺 {@code SubsystemLoginService} 注入失败。</p>
 *
 * <p>🔴 {@code @Auth(AuthToken.class)} 是硬要求（不是 {@code AuthNone}）：{@code @CaptchaVerify}
 * 由 {@code AuthInterceptor} 在鉴权之后执行，验证码校验从 {@code TokenHolder} 取临时 token，
 * 而 {@code TokenHolder} 正是 {@code AuthToken} 角色写入的。主系统同路径 {@code LoginController}
 * 同形（{@code @Auth(AuthToken.class)} ＋ {@code @CaptchaVerify("LOGIN_CAPTCHA")}）。</p>
 *
 * <p>账密校验本身不在本地：子系统无 ac_* 凭据面，由 {@link SubsystemLoginService} 凭机器凭证
 * 问主系统验身（未配机器凭证时保持墓碑文案「统一使用主系统账号登录」）。</p>
 *
 * @author coder (棒2, 子系统登录上提 2026-11-17)
 * @since 2026-11-17
 */
@Api(tags = "系统基础 - 子系统账密登录")
@RestController
@RequestMapping(value = "/web")
@ConditionalOnMissingClass("com.bidr.authorization.controller.login.LoginController")
@ConditionalOnBean(SubsystemLoginService.class)
public class SubsystemPasswordLoginController {

    private final SubsystemLoginService loginService;

    public SubsystemPasswordLoginController(SubsystemLoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * 账密登录：图形验证码前置 → 主系统验身 → 本地会话。
     *
     * @param loginId  用户标识（用户名/手机号/邮箱）
     * @param password 密码（前端已 MD5 形态）
     * @return 本地会话（accessToken/refreshToken/name/customerNumber）
     */
    @Auth(AuthToken.class)
    @ApiOperation(value = "登录", notes = "子系统账密登录：图形验证码前置，账密凭机器凭证透传主系统验身，出参同形 LoginRes")
    @CaptchaVerify("LOGIN_CAPTCHA")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public LoginRes login(String loginId, String password) {
        return loginService.loginByPassword(loginId, password);
    }
}