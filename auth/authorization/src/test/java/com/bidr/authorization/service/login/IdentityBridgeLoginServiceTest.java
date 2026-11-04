package com.bidr.authorization.service.login;

import com.bidr.authorization.service.login.impl.IdentityBridgeLoginService;
import com.bidr.authorization.service.login.impl.SsoLoginServiceImpl;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.identity.IdentityBridge;
import com.bidr.authorization.identity.BridgeUserPrincipal;
import com.bidr.authorization.identity.IdentityLoginCmd;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Title: IdentityBridgeLoginServiceTest
 * Description: SPI 登录装配离线单测（T1.1 棒4）——四条腿：桥在→凭 principal.username 铸态
 * （issueFor 复用不复制分支）；无桥 bean→回落 SsoUserHandler 链（ObjectProvider 空安全，
 * plsintec 形态护栏）；principal 无用户名→业务拒绝；多桥无 @Primary→"实现冲突"业务拒绝；
 * 桥自身装配故障→原样上浮不伪装（与 SsoLoginServiceImplTest 同一边界口径）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class IdentityBridgeLoginServiceTest {

    private ObjectProvider<IdentityBridge> bridgeProvider;
    private IdentityBridge bridge;
    private SsoLoginServiceImpl ssoLoginService;
    private IdentityBridgeLoginService service;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        bridgeProvider = Mockito.mock(ObjectProvider.class);
        bridge = Mockito.mock(IdentityBridge.class);
        ssoLoginService = Mockito.mock(SsoLoginServiceImpl.class);
        service = new IdentityBridgeLoginService(bridgeProvider, ssoLoginService);
    }

    private static BridgeUserPrincipal principal(String username) {
        BridgeUserPrincipal principal = new BridgeUserPrincipal();
        principal.username = username;
        return principal;
    }

    /** 桥在：principal.username 交 issueFor 铸产品登录态（桥不签 JWT，内核铸态） */
    @Test
    public void bridgePresentCastsLoginStateFromPrincipalUsername() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
        Mockito.when(bridge.login(Mockito.any(IdentityLoginCmd.class))).thenReturn(principal("zhangsan"));
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(ssoLoginService.issueFor("zhangsan")).thenReturn(res);

        Assert.assertSame(res, service.login(new IdentityLoginCmd()));
        Mockito.verify(ssoLoginService).issueFor("zhangsan");
        Mockito.verify(ssoLoginService, Mockito.never()).login();
    }

    /** 无桥 bean：回落 SsoUserHandler 链（plsintec 形态护栏，不 NPE 不炸启动） */
    @Test
    public void fallsBackToSsoUserHandlerChainWhenNoBridge() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(null);
        LoginRes res = Mockito.mock(LoginRes.class);
        Mockito.when(ssoLoginService.login()).thenReturn(res);

        Assert.assertSame(res, service.login(new IdentityLoginCmd()));
        Mockito.verify(ssoLoginService).login();
        Mockito.verify(ssoLoginService, Mockito.never()).issueFor(Mockito.anyString());
        Mockito.verifyNoInteractions(bridge);
    }

    /** principal 无用户名（桥验票失败静默形态）→ 业务拒绝，不进铸态 */
    @Test
    public void blankPrincipalUsernameRejected() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
        Mockito.when(bridge.login(Mockito.any(IdentityLoginCmd.class))).thenReturn(principal("  "));
        try {
            service.login(new IdentityLoginCmd());
            Assert.fail("principal 无用户名必须业务拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("身份桥未返回用户身份"));
        }
        Mockito.verify(ssoLoginService, Mockito.never()).issueFor(Mockito.anyString());
    }

    /** 多桥无 @Primary：业务拒绝文案即根因（与 SsoUserHandler 冲突腿同口径） */
    @Test
    public void multipleBridgesWithoutPrimaryRejected() {
        Mockito.when(bridgeProvider.getIfAvailable())
                .thenThrow(new NoUniqueBeanDefinitionException(IdentityBridge.class, "b1", "b2"));
        try {
            service.login(new IdentityLoginCmd());
            Assert.fail("多桥冲突必须业务拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("IdentityBridge 实现冲突"));
        }
        Mockito.verifyNoInteractions(ssoLoginService);
    }

    /** 桥自身装配故障：原样上浮，不得伪装成业务拒绝（窄捕获口径与 SsoLoginServiceImpl 一致） */
    @Test
    public void bridgeAssemblyFailureSurfacesAsSystemError() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenThrow(new BeanCreationException("boom"));
        try {
            service.login(new IdentityLoginCmd());
            Assert.fail("桥装配故障必须上浮系统错误");
        } catch (BeanCreationException expected) {
            Assert.assertEquals("boom", expected.getMessage());
        }
    }
}
