package com.bidr.authorization.config.security;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.SessionCookieUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Title: SessionCookieHealFilter
 * Description: 会话 cookie 自愈过滤器（2026-11-15 pre SSO 死循环修复，台账 #71）。
 *
 * <p>authorize 是整页导航、只认会话 cookie 回退（名见 {@link SessionCookieUtil}），而 cookie
 * 只在登录成功那一下种——浏览器重启（旧形态会话级 cookie 丢失）／cookie 机制部署前的老会话／
 * 手工清 cookie，都会造成"localStorage 票有效、cookie 缺席"：SPA 守卫自认已登录把页面弹回
 * authorize，authorize 恒判未登录又弹回登录侧 ⇒ 死循环（pre 实测 14 秒 96 次 authorize 全 302）。</p>
 *
 * <p>本过滤器在**任意请求**上对齐两者：Authorization 头票有效（仅 WEB_ACCESS_TOKEN＋Redis 核验，
 * GUEST/APP/PLATFORM/refresh 票一律不种）且 cookie 缺失或不一致 ⇒ 响应补种，Max-Age 对齐票 TTL
 * （由 {@link SessionCookieUtil} 内部单点读取）。SPA 启动首个鉴权 XHR 即自愈，authorize 随即便通；
 * 顺带收敛"共用浏览器残留上一账号 cookie"——当前请求的有效票总是覆盖旧票。</p>
 *
 * <p>纯加法、不改既有语义：cookie 已一致时零开销（一次字符串比较，不碰 Redis）；自愈任何异常
 * 静默跳过，绝不影响请求本身（鉴权仍由 AuthInterceptor 自理）。</p>
 *
 * @author Sharp
 * @since 2026-11-15
 */
@Slf4j
@Component
public class SessionCookieHealFilter extends OncePerRequestFilter {

    @Resource
    private TokenService tokenService;
    @Resource
    private SessionCookieUtil sessionCookieUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            heal(request, response);
        } catch (Exception e) {
            log.debug("[会话cookie自愈]跳过（不影响请求本身）: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void heal(HttpServletRequest request, HttpServletResponse response) {
        String header = request.getHeader(RequestConst.TOKEN);
        if (FuncUtil.isEmpty(header)) {
            return;
        }
        // Bearer 前缀归一：cookie 值与 afterLogin 种的 accessToken 同形（裸 Base64 信封）
        String ticket = header.trim();
        int space = ticket.lastIndexOf(' ');
        if (space >= 0) {
            ticket = ticket.substring(space + 1);
        }
        if (FuncUtil.isEmpty(ticket) || ticket.equals(sessionCookieUtil.read(request))) {
            return;
        }
        TokenInfo info = AuthTokenUtil.resolveToken(ticket);
        if (info == null || info.getType() != TokenType.WEB_ACCESS_TOKEN) {
            return;
        }
        if (!tokenService.verifyToken(info)) {
            return;
        }
        sessionCookieUtil.writeToken(response, ticket);
    }
}