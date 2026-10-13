package com.bidr.authorization.controller.login;

import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.impl.SsoLoginServiceImpl;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

/**
 * Title: SsoLoginControllerTest
 * Description: SSO 登录口收尾链 afterLogin 等价性三腿（纯 Mockito，不起 Spring 容器，M15 棒0 C11）。
 * <p>兑现 plan §6 风险 2 的承诺：新口的收尾链与 {@code LoginController.afterLogin} 行为等价——
 * ① 全部 {@link LoginFillTokenInf} 都被 fillToken；② SPI 列表 null/空不抛；
 * ③ service.login 先于 fillToken、controller 返回值即 service 产物（InOrder 断言）。
 * 控制器维持 {@code @Resource}/{@code @Autowired(required=false)} 字段注入形态
 * （与既有 LoginController 同机制，C6 不引入容器依赖），测试侧用 ReflectionTestUtils 装配。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
public class SsoLoginControllerTest {

    private SsoLoginServiceImpl ssoLoginService;
    private TokenService tokenService;
    private SsoLoginController controller;

    @Before
    public void setUp() {
        ssoLoginService = Mockito.mock(SsoLoginServiceImpl.class);
        tokenService = Mockito.mock(TokenService.class);
        controller = new SsoLoginController();
        ReflectionTestUtils.setField(controller, "ssoLoginService", ssoLoginService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        Mockito.when(tokenService.getTokenValue()).thenReturn(new HashMap<String, Object>());
    }

    @Test
    public void allFillTokenInfsAreInvoked() {
        LoginFillTokenInf infA = Mockito.mock(LoginFillTokenInf.class);
        LoginFillTokenInf infB = Mockito.mock(LoginFillTokenInf.class);
        ReflectionTestUtils.setField(controller, "fillTokenInfList", Arrays.asList(infA, infB));
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(ssoLoginService.login()).thenReturn(res);

        LoginRes returned = controller.login("any-id-token");

        Mockito.verify(infA).fillToken(res);
        Mockito.verify(infB).fillToken(res);
        Assert.assertSame(res, returned);
    }

    @Test
    public void nullOrEmptySpiListDoesNotThrow() {
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(ssoLoginService.login()).thenReturn(res);

        ReflectionTestUtils.setField(controller, "fillTokenInfList", null);
        Assert.assertSame(res, controller.login(null));

        ReflectionTestUtils.setField(controller, "fillTokenInfList", Collections.emptyList());
        Assert.assertSame(res, controller.login(null));
    }

    @Test
    public void serviceLoginHappensBeforeFillTokenAndReturnIsServiceProduct() {
        LoginFillTokenInf inf = Mockito.mock(LoginFillTokenInf.class);
        ReflectionTestUtils.setField(controller, "fillTokenInfList", Collections.singletonList(inf));
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(ssoLoginService.login()).thenReturn(res);

        LoginRes returned = controller.login("x");

        InOrder inOrder = Mockito.inOrder(ssoLoginService, inf);
        inOrder.verify(ssoLoginService).login();
        inOrder.verify(inf).fillToken(res);
        Mockito.verifyNoMoreInteractions(inf);
        // 返回即 service 产物：controller 不改写/不重签
        Assert.assertSame(res, returned);
    }
}
