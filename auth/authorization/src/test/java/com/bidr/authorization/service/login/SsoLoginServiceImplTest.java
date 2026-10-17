package com.bidr.authorization.service.login;

import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.account.AuthAccountService;
import com.bidr.authorization.service.login.impl.SsoLoginServiceImpl;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.Md5Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;

/**
 * Title: SsoLoginServiceImplTest
 * Description: SSO 登录编排三分支 + SPI 缺席/空用户名/多实现冲突拒绝腿（纯 Mockito，离线）。
 * <p>与框架侧 bidr 形态既有 SSO 服务的三分支一一对应；拒绝腿验证 plsintec 等无 idm
 * 形态下"未接入SSO身份源"业务拒绝语义（文案断言，不含 HTTP 状态码字样）。
 * SPI 取用策略＝ObjectProvider.getIfAvailable()（M15 棒0 C9 修订口径）：
 * null＝缺席拒绝、有唯一 bean＝原逻辑、抛 NoUniqueBeanDefinitionException（多实现且无 @Primary）＝"实现冲突"拒绝；
 * 其余 BeansException（SPI 自身装配故障）与 {@code resolveUserName()} 抛出的运行时异常原样上浮，
 * 不得伪装成业务拒绝（两腿各钉一边）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
public class SsoLoginServiceImplTest {

    private LoginService loginService;
    private AuthAccountService accountService;
    private CreateUserService createUserService;
    private AcUserService acUserService;
    private ObjectProvider<SsoUserHandler> handlerProvider;
    private SsoLoginServiceImpl service;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        loginService = Mockito.mock(LoginService.class);
        accountService = Mockito.mock(AuthAccountService.class);
        createUserService = Mockito.mock(CreateUserService.class);
        acUserService = Mockito.mock(AcUserService.class);
        handlerProvider = Mockito.mock(ObjectProvider.class);
        service = new SsoLoginServiceImpl(loginService, accountService, createUserService, acUserService,
                handlerProvider);
    }

    private void givenUserName(String userName) {
        SsoUserHandler handler = Mockito.mock(SsoUserHandler.class);
        Mockito.when(handler.resolveUserName()).thenReturn(userName);
        Mockito.when(handlerProvider.getIfAvailable()).thenReturn(handler);
    }

    @Test
    public void acUserHitGoesToLogin() {
        givenUserName("u1");
        AcUser acUser = new AcUser();
        Mockito.when(acUserService.getUserByUserName("u1")).thenReturn(acUser);
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(loginService.login(acUser)).thenReturn(res);

        Assert.assertSame(res, service.login());
        Mockito.verify(loginService).login(acUser);
        Mockito.verify(loginService, Mockito.never()).loginOrReg(Mockito.any(AcAccount.class));
        Mockito.verify(createUserService, Mockito.never()).createUser(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void accountHitGoesToLoginOrReg() {
        givenUserName("u2");
        Mockito.when(acUserService.getUserByUserName("u2")).thenReturn(null);
        AcAccount account = new AcAccount();
        Mockito.when(accountService.getAccountByUserName("u2")).thenReturn(account);
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(loginService.loginOrReg(account)).thenReturn(res);

        Assert.assertSame(res, service.login());
        Mockito.verify(loginService).loginOrReg(account);
        Mockito.verify(createUserService, Mockito.never()).createUser(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void freshUserCreatedWithSsoTypeThenLogin() {
        givenUserName("u3");
        Mockito.when(acUserService.getUserByUserName("u3")).thenReturn(null);
        Mockito.when(accountService.getAccountByUserName("u3")).thenReturn(null);
        AcUser newUser = new AcUser();
        Mockito.when(createUserService.createUser("u3", Md5Util.MD5("u3"))).thenReturn(newUser);
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(loginService.login(newUser)).thenReturn(res);

        Assert.assertSame(res, service.login());
        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(loginService).login(captor.capture());
        Assert.assertEquals(UserTypeDict.SSO.getValue(), captor.getValue().getUserType());
    }

    @Test
    public void missingHandlerRejectsWithoutNpe() {
        Mockito.when(handlerProvider.getIfAvailable()).thenReturn(null);
        try {
            service.login();
            Assert.fail("SPI 缺席必须走业务拒绝，不得放行/不得 NPE");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("未接入SSO身份源"));
            // 随修①：缺席腿与"解析不出身份"腿文案互斥（负向断言钉死两段拆分不串台）
            Assert.assertFalse(e.getMessage().contains("SSO身份源未返回用户身份"));
        }
        Mockito.verifyNoInteractions(loginService, accountService, createUserService, acUserService);
    }

    @Test
    public void multipleHandlersRejectAsConflict() {
        // C9 修订腿：多候选且无 @Primary 时 getIfAvailable() 抛 NoUniqueBeanDefinitionException
        // → 转"SsoUserHandler 实现冲突"业务拒绝，且不得触碰任何下游服务
        Mockito.when(handlerProvider.getIfAvailable()).thenThrow(
                new NoUniqueBeanDefinitionException(SsoUserHandler.class, Arrays.asList("h1", "h2")));
        try {
            service.login();
            Assert.fail("多实现无法消歧必须按实现冲突业务拒绝，不得静默取其一");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("SsoUserHandler 实现冲突"));
            // 冲突腿文案与缺席腿互斥：根因不同，不得都报"未接入"
            Assert.assertFalse(e.getMessage().contains("未接入SSO身份源"));
        }
        Mockito.verifyNoInteractions(loginService, accountService, createUserService, acUserService);
    }

    /**
     * 窄捕获边界（review#3 S-1）：只有 NoUniqueBeanDefinitionException 转业务拒绝。SPI 实现自身
     * 装配故障必须原样上浮成系统错误——把它伪装成"SsoUserHandler 实现冲突"会掩盖真实根因。
     */
    @Test
    public void spiBeanCreationFailurePropagatesAsSystemErrorNotBusinessRejection() {
        Mockito.when(handlerProvider.getIfAvailable()).thenThrow(
                new BeanCreationException("idmSsoUserHandler", "SPI 实现自身依赖解析失败"));
        try {
            service.login();
            Assert.fail("SPI 自身装配故障必须原样上浮，不得转成业务拒绝信封");
        } catch (ServiceException e) {
            Assert.fail("不得把 BeanCreationException 伪装成业务拒绝: " + e.getMessage());
        } catch (BeanCreationException expected) {
            Assert.assertEquals("idmSsoUserHandler", expected.getBeanName());
        }
        Mockito.verifyNoInteractions(loginService, accountService, createUserService, acUserService);
    }

    /**
     * SPI 实现内部故障（{@code resolveUserName()} 抛运行时异常）必须原样上浮，不得被折成三段业务拒绝
     * 中的任何一段——"未接入/实现冲突/身份未返回"都只描述编排自身的判定，替别人的实现报错会掩盖根因。
     */
    @Test
    public void spiRuntimeFailurePropagatesNotBusinessRejection() {
        SsoUserHandler handler = Mockito.mock(SsoUserHandler.class);
        Mockito.when(handler.resolveUserName()).thenThrow(new IllegalStateException("SPI 实现内部故障"));
        Mockito.when(handlerProvider.getIfAvailable()).thenReturn(handler);
        try {
            service.login();
            Assert.fail("resolveUserName() 的运行时异常必须原样上浮");
        } catch (ServiceException e) {
            Assert.fail("不得把 SPI 内部故障伪装成 SSO 业务拒绝: " + e.getMessage());
        } catch (IllegalStateException expected) {
            Assert.assertEquals("SPI 实现内部故障", expected.getMessage());
        }
        Mockito.verifyNoInteractions(loginService, accountService, createUserService, acUserService);
    }

    @Test
    public void blankUserNameRejectsWithSsoMessage() {
        givenUserName("  ");
        try {
            service.login();
            Assert.fail("解析不出用户名必须走业务拒绝");
        } catch (ServiceException e) {
            // F5 拆分口径：handler 在但未返回身份＝独立文案，与"未接入SSO身份源"（bean 缺席腿）区分
            Assert.assertTrue(e.getMessage().contains("SSO身份源未返回用户身份"));
            Assert.assertFalse(e.getMessage().contains("未接入SSO身份源"));
        }
    }
}
