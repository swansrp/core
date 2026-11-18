package com.bidr.authorization.utils;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: SessionCookieUtilTest
 * Description: 会话 cookie 统一读写点（台账 #71）：名字由 my.project.name 派生（大写＋_TOKEN，
 * 未声明回落中性名）、Max-Age 由本 bean 单点读 WEB_ACCESS_TOKEN_EXPIRED（>0 带／≤0 会话级，
 * 调用方不再逐处传 TTL）、afterLogin 经 RequestContextHolder 取响应、非 Web 线程静默跳过、
 * read 判据与 authorize 共用。
 *
 * @author Sharp
 * @since 2026-11-15
 */
public class SessionCookieUtilTest {

    private static final String COOKIE_NAME = "INKHUB_TOKEN";

    private SessionCookieUtil util;
    private SysConfigCacheService sysConfigCacheService;

    @Before
    public void setUp() {
        util = new SessionCookieUtil();
        ReflectionTestUtils.setField(util, "projectName", "inkhub");
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED)).thenReturn(86400);
        ReflectionTestUtils.setField(util, "sysConfigCacheService", sysConfigCacheService);
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void writeToken_withTtl_fullShape() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.writeToken(response, "ticket-abc");
        // 逐属性断言而非整串相等：MockHttpServletResponse 会解析重建 Set-Cookie（补 Expires、重排属性），
        // 属性顺序按 RFC 6265 本无语义；真实响应是手写原串
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.startsWith(COOKIE_NAME + "=ticket-abc;"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Max-Age=86400"));
    }

    @Test
    public void writeToken_zeroTtl_sessionShape() {
        Mockito.when(sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED)).thenReturn(0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.writeToken(response, "ticket-abc");
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.startsWith(COOKIE_NAME + "=ticket-abc;"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertEquals(-1, cookie.indexOf("Max-Age"));
    }

    @Test
    public void writeToken_emptyInputs_noop() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.writeToken(response, "");
        util.writeToken(response, null);
        util.writeToken(null, "ticket-abc");
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    public void projectName_derivesCookieName() {
        ReflectionTestUtils.setField(util, "projectName", "epc-ai");
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.writeToken(response, "ticket-abc");
        assertTrue(response.getHeader("Set-Cookie").startsWith("EPC-AI_TOKEN=ticket-abc;"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, "other-app-ticket"), new Cookie("EPC-AI_TOKEN", "ticket-abc"));
        assertEquals("ticket-abc", util.read(request));
    }

    @Test
    public void noProjectName_fallbackNeutralName() {
        ReflectionTestUtils.setField(util, "projectName", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.writeToken(response, "ticket-abc");
        assertTrue(response.getHeader("Set-Cookie").startsWith("BIDR_TOKEN=ticket-abc;"));
    }

    @Test
    public void write_viaRequestContext_plantsOnCurrentResponse() {
        Mockito.when(sysConfigCacheService.getParamInt(AccountParam.WEB_ACCESS_TOKEN_EXPIRED)).thenReturn(3600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        LoginRes res = new LoginRes();
        res.setAccessToken("ticket-xyz");
        util.write(res);
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=3600"));
    }

    @Test
    public void write_nullOrEmptyRes_noop() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
        util.write(null);
        util.write(new LoginRes());
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    public void write_nonWebContext_silentSkip() {
        RequestContextHolder.resetRequestAttributes();
        LoginRes res = new LoginRes();
        res.setAccessToken("ticket-xyz");
        util.write(res);
    }

    @Test
    public void read_findsSessionCookieOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER", "x"), new Cookie(COOKIE_NAME, "ticket-abc"));
        assertEquals("ticket-abc", util.read(request));
    }

    @Test
    public void read_absent_null() {
        assertNull(util.read(new MockHttpServletRequest()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER", "x"));
        assertNull(util.read(request));
    }
}