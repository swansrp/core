package com.bidr.authorization.utils;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Title: SessionCookieUtil
 * Description: 登录会话 cookie（OAuth2IdpController authorize 的整页导航回退腿）的统一读写点。
 *
 * <p>存在理由：SSO 授权入口 {@code /web/oauth2/authorize} 是**整页导航**，浏览器带不上 SPA 存在
 * localStorage 的 Authorization 头 ⇒ 该端点按 cookie 回退取票。因此**每一个签发登录态的腿**
 * （密码登录／首设密／注册／短信登录／扫码／改密／refresh／SSO 换取／幽灵登录）都必须在响应里
 * 同步种这张 cookie，否则门户（纯 XHR）虽然已登录，一进子系统（整页 authorize）就只剩两条错路：
 * ①无 cookie ⇒ 302 回登录页（刚注册完要再登一次）；②共用浏览器残留上一账号 cookie ⇒
 * 子系统按旧票签 code，身份被上一个人接管（2026-11-11 test 环境 e2e 实证）。</p>
 *
 * <p>落点选在 <b>afterLogin 链</b>：所有登录成功腿都必经该链（含 pack-bidr 的
 * {@code POST /web/auth/login} 与超管幽灵登录），一处收口即全腿覆盖；用
 * {@link RequestContextHolder} 取当前响应，避免给每个端点加 {@code HttpServletResponse} 形参。
 * 非 Web 线程（单测／异步）取不到上下文时静默跳过，不影响 afterLogin 原有职责。</p>
 *
 * <p>🔴 只在登录腿种还不够（2026-11-15 pre SSO 死循环实证，台账 #71）：cookie 曾是无 Max-Age 的
 * 会话级（关浏览器即删），而 localStorage 票永久 ⇒ 重启浏览器后"票有效、cookie 缺席"，SPA 自认已登录、
 * authorize 恒判未登录 ⇒ 守卫↔authorize 死循环。修复两条：①本工具种 cookie 一律带
 * {@code Max-Age}（TTL 由本 bean 自读 {@link AccountParam#WEB_ACCESS_TOKEN_EXPIRED}，票 cookie 同寿，
 * 调用方无需逐处查 TTL）；②{@code SessionCookieHealFilter} 在任意
 * 鉴权请求上自愈补种（覆盖老会话／清 cookie／重启浏览器全场景）。</p>
 *
 * <p>cookie 名由项目名派生＝{@code my.project.name} 大写＋{@code _TOKEN}（inkhub→INKHUB_TOKEN、
 * epc-ai→EPC-AI_TOKEN）：同域名挂多应用时浏览器 cookie 罐按域共享、不按路径隔离，固定名会让
 * 各应用互相覆盖票；按项目名派生即天然隔离，且项目名是各应用既有的身份键（spring.application.name
 * 同源），不新增配置面。未声明项目名时回落框架中性名 {@code BIDR_TOKEN}。
 * 名字读写同源于本 bean，authorize 回退与自愈过滤器共用同一判据。</p>
 *
 * @author Sharp
 * @since 2026-11-11
 */
@Component
public class SessionCookieUtil {

    private static final String FALLBACK_NAME = "BIDR_TOKEN";

    @Value("${my.project.name:}")
    private String projectName;

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    private String cookieName() {
        return FuncUtil.isEmpty(projectName)
                ? FALLBACK_NAME
                : projectName.trim().toUpperCase(java.util.Locale.ROOT) + "_TOKEN";
    }

    /** 票 cookie 同寿：TTL 单点取自 WEB_ACCESS_TOKEN_EXPIRED，调用方不再逐处查。 */
    private int maxAgeSeconds() {
        return sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED);
    }

    /**
     * 登录成功即种会话 cookie；无 token 或非 Web 上下文时静默跳过（幂等：同一响应多次调用只保留最后一张票）。
     */
    public void write(LoginRes res) {
        if (res == null || FuncUtil.isEmpty(res.getAccessToken())) {
            return;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return;
        }
        writeToken(((ServletRequestAttributes) attributes).getResponse(), res.getAccessToken());
    }

    /**
     * 直写响应（登录腿经 {@link #write} 间接调用；自愈过滤器直接调用）。
     * 手写 Set-Cookie 而不用 Cookie#setAttribute：javax Cookie 的 SameSite 属性需 Servlet 4.0，
     * 本 reactor 编译期仍是老 servlet-api，直接拼头最稳（值＝Base64 信封，无 ; / , 风险）。
     */
    public void writeToken(HttpServletResponse response, String accessToken) {
        if (response == null || FuncUtil.isEmpty(accessToken)) {
            return;
        }
        String cookie = cookieName() + "=" + accessToken
                + "; Path=/; HttpOnly; SameSite=Lax";
        int maxAgeSeconds = maxAgeSeconds();
        if (maxAgeSeconds > 0) {
            cookie += "; Max-Age=" + maxAgeSeconds;
        }
        response.addHeader("Set-Cookie", cookie);
    }

    /** 读请求里的会话 cookie（无则 null）；authorize 的整页导航回退与自愈过滤器共用同一判据。 */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}