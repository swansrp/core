package com.bidr.authorization.utils;

import com.bidr.authorization.controller.oauth2.OAuth2IdpController;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;

/**
 * Title: SessionCookieUtil
 * Description: 登录成功会话 cookie 的统一写入点。
 *
 * <p>存在理由：SSO 授权入口 {@code /web/oauth2/authorize} 是**整页导航**，浏览器带不上 SPA 存在
 * localStorage 的 Authorization 头 ⇒ 该端点按 {@code INKHUB_TOKEN} cookie 回退取票（口径见
 * {@link OAuth2IdpController#SESSION_COOKIE}）。因此**每一个签发登录态的腿**（密码登录／首设密／
 * 注册／短信登录／扫码／改密／refresh／SSO 换取／幽灵登录）都必须在响应里同步种这张 cookie，
 * 否则门户（纯 XHR）虽然已登录，一进子系统（整页 authorize）就只剩两条错路：
 * ①无 cookie ⇒ 302 回登录页（刚注册完要再登一次）；②共用浏览器残留上一账号 cookie ⇒
 * 子系统按旧票签 code，身份被上一个人接管（2026-11-11 test 环境 e2e 实证）。</p>
 *
 * <p>落点选在 <b>afterLogin 链</b>：所有登录成功腿都必经该链（含 pack-bidr 的
 * {@code POST /web/auth/login} 与超管幽灵登录），一处收口即全腿覆盖；用
 * {@link RequestContextHolder} 取当前响应，避免给每个端点加 {@code HttpServletResponse} 形参。
 * 非 Web 线程（单测／异步）取不到上下文时静默跳过，不影响 afterLogin 原有职责。</p>
 *
 * @author Sharp
 * @since 2026-11-11
 */
public final class SessionCookieUtil {

    private SessionCookieUtil() {
    }

    /** 登录成功即种会话 cookie；无 token 或非 Web 上下文时静默跳过（幂等：同一响应多次调用只保留最后一张票）。 */
    public static void write(LoginRes res) {
        if (res == null || FuncUtil.isEmpty(res.getAccessToken())) {
            return;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return;
        }
        HttpServletResponse response = ((ServletRequestAttributes) attributes).getResponse();
        if (response == null) {
            return;
        }
        // 手写 Set-Cookie 而不用 Cookie#setAttribute：javax Cookie 的 SameSite 属性需 Servlet 4.0，
        // 本 reactor 编译期仍是老 servlet-api，直接拼头最稳（值＝Base64 信封，无 ; / , 风险）。
        response.addHeader("Set-Cookie", OAuth2IdpController.SESSION_COOKIE + "="
                + res.getAccessToken() + "; Path=/; HttpOnly; SameSite=Lax");
    }
}