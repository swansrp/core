package com.bidr.authorization.config.security;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.SessionCookieUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Title: SessionCookieHealFilterTest
 * Description: 会话 cookie 自愈过滤器判定矩阵（2026-11-15 pre SSO 死循环修复，台账 #71）：
 * 有效 WEB 票＋cookie 缺失/不一致 ⇒ 补种（Max-Age 对齐票 TTL）；一致 ⇒ 零开销不碰 Redis；
 * GUEST/APP/refresh 票、无效票、无头、畸形头 ⇒ 一律不种且请求链不受影响。
 * cookie 名由 my.project.name 派生（大写＋_TOKEN），本测试钉 inkhub→INKHUB_TOKEN 验证读写同源。
 *
 * <p>离线腿：TokenService/SysConfigCacheService 全 mock，不打 Redis。</p>
 *
 * @author Sharp
 * @since 2026-11-15
 */
public class SessionCookieHealFilterTest {

    private static final int TTL = 86400;
    private static final String COOKIE_NAME = "INKHUB_TOKEN";
    private static final String TICKET = AuthTokenUtil.getToken(
            new TokenInfo("uuid-web", TokenType.WEB_ACCESS_TOKEN, "2022344", 0));

    private TokenService tokenService;
    private SysConfigCacheService sysConfigCacheService;
    private SessionCookieUtil sessionCookieUtil;
    private SessionCookieHealFilter filter;
    private FilterChain chain;

    @Before
    public void setUp() {
        tokenService = Mockito.mock(TokenService.class);
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        when(sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED)).thenReturn(TTL);
        sessionCookieUtil = new SessionCookieUtil();
        ReflectionTestUtils.setField(sessionCookieUtil, "projectName", "inkhub");
        ReflectionTestUtils.setField(sessionCookieUtil, "sysConfigCacheService", sysConfigCacheService);
        filter = new SessionCookieHealFilter();
        ReflectionTestUtils.setField(filter, "tokenService", tokenService);
        ReflectionTestUtils.setField(filter, "sessionCookieUtil", sessionCookieUtil);
        chain = Mockito.mock(FilterChain.class);
    }

    private MockHttpServletResponse run(String authorization, String cookieValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        if (cookieValue != null) {
            request.setCookies(new javax.servlet.http.Cookie(COOKIE_NAME, cookieValue));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        return response;
    }

    @Test
    public void validTicket_cookieMissing_plantsWithTtl() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(true);
        MockHttpServletResponse response = run(TICKET, null);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.startsWith(COOKIE_NAME + "=" + TICKET + ";"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Max-Age=" + TTL));
    }

    @Test
    public void validTicket_cookieMatching_zeroOverhead() throws Exception {
        MockHttpServletResponse response = run(TICKET, TICKET);
        assertNull(response.getHeader("Set-Cookie"));
        verifyNoInteractions(tokenService);
    }

    @Test
    public void validTicket_cookieStale_overwrites() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(true);
        String stale = AuthTokenUtil.getToken(new TokenInfo("uuid-old", TokenType.WEB_ACCESS_TOKEN, "000001", 0));
        MockHttpServletResponse response = run(TICKET, stale);
        assertTrue(response.getHeader("Set-Cookie").startsWith(COOKIE_NAME + "=" + TICKET + ";"));
    }

    @Test
    public void configuredName_isUsedForPlantAndMatch() throws Exception {
        ReflectionTestUtils.setField(sessionCookieUtil, "projectName", "epc-ai");
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(true);
        // 别的应用的名字（inkhub 派生名）不算一致 ⇒ 按本项目派生名补种
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", TICKET);
        request.setCookies(new javax.servlet.http.Cookie("INKHUB_TOKEN", TICKET));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertTrue(response.getHeader("Set-Cookie").startsWith("EPC-AI_TOKEN=" + TICKET + ";"));
        // 本项目派生名一致 ⇒ 零开销
        MockHttpServletRequest matched = new MockHttpServletRequest();
        matched.addHeader("Authorization", TICKET);
        matched.setCookies(new javax.servlet.http.Cookie("EPC-AI_TOKEN", TICKET));
        MockHttpServletResponse quiet = new MockHttpServletResponse();
        filter.doFilter(matched, quiet, Mockito.mock(FilterChain.class));
        assertNull(quiet.getHeader("Set-Cookie"));
    }

    @Test
    public void bearerPrefix_normalizedToBareTicket() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(true);
        MockHttpServletResponse response = run("Bearer " + TICKET, null);
        assertTrue(response.getHeader("Set-Cookie").startsWith(COOKIE_NAME + "=" + TICKET + ";"));
    }

    @Test
    public void invalidTicket_noPlant() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(false);
        MockHttpServletResponse response = run(TICKET, null);
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    public void guestTicket_typeGateBeforeRedis() throws Exception {
        String guest = AuthTokenUtil.getToken(new TokenInfo("uuid-guest", TokenType.GUEST_TOKEN, "GUEST", 0));
        MockHttpServletResponse response = run(guest, null);
        assertNull(response.getHeader("Set-Cookie"));
        verify(tokenService, never()).verifyToken(any(TokenInfo.class));
    }

    @Test
    public void appTicket_noPlant() throws Exception {
        String app = AuthTokenUtil.getToken(new TokenInfo("uuid-app", TokenType.APP_ACCESS_TOKEN, "2022344", 0));
        MockHttpServletResponse response = run(app, null);
        assertNull(response.getHeader("Set-Cookie"));
        verify(tokenService, never()).verifyToken(any(TokenInfo.class));
    }

    @Test
    public void refreshTicket_noPlant() throws Exception {
        String refresh = AuthTokenUtil.getToken(new TokenInfo("uuid-refresh", TokenType.WEB_REFRESH_TOKEN, "2022344", 0));
        MockHttpServletResponse response = run(refresh, null);
        assertNull(response.getHeader("Set-Cookie"));
        verify(tokenService, never()).verifyToken(any(TokenInfo.class));
    }

    @Test
    public void noHeader_noPlant_noRedis() throws Exception {
        MockHttpServletResponse response = run(null, null);
        assertNull(response.getHeader("Set-Cookie"));
        verifyNoInteractions(tokenService);
    }

    @Test
    public void malformedHeader_chainUnaffected() throws Exception {
        MockHttpServletResponse response = run("%%%not-a-token%%%", null);
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    public void redisDown_chainUnaffected() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenThrow(new RuntimeException("redis down"));
        MockHttpServletResponse response = run(TICKET, null);
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    public void ttlZero_sessionCookieShape() throws Exception {
        when(tokenService.verifyToken(any(TokenInfo.class))).thenReturn(true);
        when(sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED)).thenReturn(0);
        MockHttpServletResponse response = run(TICKET, null);
        String cookie = response.getHeader("Set-Cookie");
        assertEquals(-1, cookie.indexOf("Max-Age"));
    }
}