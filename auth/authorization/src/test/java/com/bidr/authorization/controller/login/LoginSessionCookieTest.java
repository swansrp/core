package com.bidr.authorization.controller.login;

import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.HashMap;

/**
 * Title: LoginSessionCookieTest
 * Description: 登录成功会话 cookie 契约（2026-11-11，对账单 #65）。
 *
 * <p>钉住「所有登录成功腿都必经 afterLogin，afterLogin 必写 INKHUB_TOKEN cookie」这条不变量：
 * authorize 是整页导航，只认 header 或该 cookie；漏种会让门户已登录、子系统 302 回登录页，
 * 共用浏览器上还会复用上一账号残留 cookie 导致身份错位（test 环境 e2e 实证）。</p>
 *
 * <p>纯 Mockito + spring-test mock servlet 对象，不起容器；cookie 通过
 * {@link RequestContextHolder} 取当前响应，因此每条腿都显式绑定请求上下文。</p>
 *
 * @author Sharp
 * @since 2026-11-11
 */
public class LoginSessionCookieTest {

    private LoginController controller;
    private TokenService tokenService;

    @Before
    public void setUp() {
        controller = new LoginController();
        tokenService = Mockito.mock(TokenService.class);
        Mockito.when(tokenService.getTokenValue()).thenReturn(new HashMap<String, Object>());
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "fillTokenInfList", null);
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletResponse bindWebContext() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), response));
        return response;
    }

    @Test
    public void afterLoginWritesSessionCookieWithAccessToken() {
        MockHttpServletResponse response = bindWebContext();
        LoginRes res = new LoginRes();
        res.setAccessToken("abcDEF123:0:000001");

        controller.afterLogin(res);

        String cookie = response.getHeader("Set-Cookie");
        Assert.assertNotNull("登录成功必须种会话 cookie（authorize 整页导航靠它取票）", cookie);
        Assert.assertTrue(cookie.contains("INKHUB_TOKEN=abcDEF123:0:000001"));
        Assert.assertTrue(cookie.contains("Path=/"));
        Assert.assertTrue(cookie.contains("HttpOnly"));
        Assert.assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    public void noAccessTokenWritesNoCookie() {
        MockHttpServletResponse response = bindWebContext();
        LoginRes res = new LoginRes();
        res.setCustomerNumber("000001");

        controller.afterLogin(res);

        Assert.assertNull("无 accessToken（失败/无会话形态）不得种 cookie", response.getHeader("Set-Cookie"));
    }

    @Test
    public void fillTokenRunsBeforeCookieSoTicketMatchesFinalResponse() {
        MockHttpServletResponse response = bindWebContext();
        LoginFillTokenInf inf = Mockito.mock(LoginFillTokenInf.class);
        Mockito.doAnswer(invocation -> {
            ((LoginRes) invocation.getArgument(0)).setAccessToken("final-token:0:000001");
            return null;
        }).when(inf).fillToken(Mockito.any(LoginRes.class));
        ReflectionTestUtils.setField(controller, "fillTokenInfList", Collections.singletonList(inf));
        LoginRes res = new LoginRes();
        res.setAccessToken("initial-token:0:000001");

        controller.afterLogin(res);

        String cookie = response.getHeader("Set-Cookie");
        Assert.assertNotNull(cookie);
        Assert.assertTrue("cookie 必须用 fillToken 之后的最终票", cookie.contains("INKHUB_TOKEN=final-token:0:000001"));
        Assert.assertFalse(cookie.contains("initial-token"));
    }

    @Test
    public void nonWebContextIsSilentlySkipped() {
        RequestContextHolder.resetRequestAttributes();
        LoginRes res = new LoginRes();
        res.setAccessToken("abc:0:000001");

        // 单测/异步线程没有 Servlet 上下文：不得抛异常，afterLogin 原职责（fillToken/日志）不受影响
        controller.afterLogin(res);
    }
}