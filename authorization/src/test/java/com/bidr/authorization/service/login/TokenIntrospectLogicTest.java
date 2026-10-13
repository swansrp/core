package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.TokenIntrospectRes;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.DateUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: TokenIntrospectLogicTest
 * Description: token 回查判定矩阵（纯 Mockito + MockHttpServletRequest，离线）。
 * <p>覆盖：无效→明细空；有效但调用方无登录态→明细空；有效且调用方已登录→明细齐（含剩余寿命）；
 * 归属用户查不到→留空不报错；坏串→框架口径业务拒绝（PA_PARAM_FORMAT）；
 * 可解码但未知 type 段→同归坏串拒绝且不触令牌存储；
 * caller 头脏串/合法形状但服务端不认→降级为未登录（三态照常、明细空、不抛）；
 * caller 段抛非 ServiceException→原样上浮不降级（容错宽度收窄的负方向）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
public class TokenIntrospectLogicTest {

    private TokenService tokenService;
    private AcUserService acUserService;
    private SsoTokenIntrospectService service;

    private final String targetToken =
            AuthTokenUtil.getToken(AuthTokenUtil.buildToken("tk-target", TokenType.WEB_ACCESS_TOKEN, "10001", 1200));
    private final String callerToken =
            AuthTokenUtil.getToken(AuthTokenUtil.buildToken("tk-caller", TokenType.WEB_ACCESS_TOKEN, "20002", 1200));

    @Before
    public void setUp() {
        tokenService = Mockito.mock(TokenService.class);
        acUserService = Mockito.mock(AcUserService.class);
        service = new SsoTokenIntrospectService(tokenService, acUserService);
    }

    @Test
    public void invalidTokenYieldsNoDetails() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertFalse(res.isValid());
        Assert.assertEquals("WEB_ACCESS_TOKEN", res.getTokenType());
        assertDetailsEmpty(res);
    }

    @Test
    public void invalidTokenWithLoggedInCallerStillYieldsNoDetails() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class)))
                .thenAnswer(invocation -> {
                    TokenInfo ti = invocation.getArgument(0);
                    return "tk-caller".equals(ti.getToken());
                });
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertFalse(res.isValid());
        assertDetailsEmpty(res);
    }

    @Test
    public void validTokenWithoutCallerLoginYieldsNoDetails() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertTrue(res.isValid());
        assertDetailsEmpty(res);
        // 随修③：调用方未登录时不得触达 ac_user 查询（分层判据＝明细填充被 callerLogin 硬闸住）
        Mockito.verify(acUserService, Mockito.never()).getByCustomerNumber(Mockito.anyString());
    }

    @Test
    public void validTokenWithLoggedInCallerFillsDetails() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(true);
        AcUser acUser = new AcUser();
        acUser.setUserId(7L);
        acUser.setName("张三");
        Mockito.when(acUserService.getByCustomerNumber("10001")).thenReturn(acUser);
        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put(TokenItem.TIMESTAMP.name(), DateUtil.formatDate(new Date(), DateUtil.DATE_TIME_NORMAL));
        tokenMap.put(TokenItem.EXPIRED.name(), 1200);
        Mockito.when(tokenService.getTokenValue(Mockito.any(TokenInfo.class))).thenReturn(tokenMap);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertTrue(res.isValid());
        Assert.assertEquals("10001", res.getCustomerNumber());
        Assert.assertEquals(Long.valueOf(7L), res.getUserId());
        Assert.assertEquals("张三", res.getName());
        Assert.assertNotNull(res.getExpireSeconds());
        Assert.assertTrue(res.getExpireSeconds() > 1100L && res.getExpireSeconds() <= 1200L);
    }

    @Test
    public void missingAcUserLeavesDetailsBlankWithoutError() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(true);
        Mockito.when(acUserService.getByCustomerNumber("10001")).thenReturn(null);
        Mockito.when(tokenService.getTokenValue(Mockito.any(TokenInfo.class)))
                .thenReturn(new HashMap<String, Object>());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertTrue(res.isValid());
        Assert.assertEquals("10001", res.getCustomerNumber());
        Assert.assertNull(res.getUserId());
        Assert.assertNull(res.getName());
        Assert.assertNull(res.getExpireSeconds());
    }

    @Test
    public void garbageTokenStringRejectedAsParamFormat() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            service.introspect("!!!!not-a-token!!!!", request);
            Assert.fail("坏串必须按框架口径业务拒绝（PA_PARAM_FORMAT）");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("安全令牌"));
        }
    }

    @Test
    public void unknownTypeSegmentRejectedAsParamFormatWithoutTouchingTokenStore() {
        // 可解码（三段齐）但 type 段命中不到 TokenType 枚举 → 归坏串态：
        // 必须在 verifyToken 之前业务拒绝（杜绝 getKey→getPrefix 的 NPE），且不触任何令牌存储
        String unknownTypeToken = Base64Util.encode("tk-unknown:NO_SUCH_TYPE:10001");
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            service.introspect(unknownTypeToken, request);
            Assert.fail("未知 type 段必须按框架口径业务拒绝（PA_PARAM_FORMAT）");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("安全令牌"));
        }
        Mockito.verify(tokenService, Mockito.never()).verifyToken(Mockito.any(TokenInfo.class));
        Mockito.verify(tokenService, Mockito.never()).getTokenValue(Mockito.any(TokenInfo.class));
    }

    @Test
    public void dirtyCallerHeaderDegradesToNotLoggedInWithoutThrowing() {
        // caller 头容错腿：脏串（非三段）在 extractToken 内抛 PA_PARAM_FORMAT，也必须降级为未登录，
        // 三态判定照常返回（target 有效），身份明细保持为空，不得把异常透出去
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, "Bearer abc");

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertTrue(res.isValid());
        assertDetailsEmpty(res);
    }

    @Test
    public void wellFormedCallerTokenUnknownToServerDegradesToNotLoggedIn() {
        // caller 头合法形状但服务端不认（verifyToken(caller)=false）→ callerLogin=false、明细空
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class)))
                .thenAnswer(invocation -> {
                    TokenInfo ti = invocation.getArgument(0);
                    return "tk-target".equals(ti.getToken());
                });
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);

        TokenIntrospectRes res = service.introspect(targetToken, request);
        Assert.assertTrue(res.isValid());
        assertDetailsEmpty(res);
    }

    @Test
    public void callerProbeRuntimeFailurePropagatesNotDegraded() {
        // C13 口径的负方向（review#4 S-1）：caller 段只把 ServiceException 降级为"未登录"；
        // 其它运行时异常（如存储层故障）必须原样上浮，不得被容错宽度吞成假的未登录信号
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class)))
                .thenAnswer(invocation -> {
                    TokenInfo ti = invocation.getArgument(0);
                    if ("tk-caller".equals(ti.getToken())) {
                        throw new IllegalStateException("token 存储故障");
                    }
                    return true;
                });
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);
        try {
            service.introspect(targetToken, request);
            Assert.fail("非 ServiceException 的 caller 段故障必须上浮，不得降级成未登录");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("token 存储故障", expected.getMessage());
        }
    }

    private void assertDetailsEmpty(TokenIntrospectRes res) {
        Assert.assertNull(res.getCustomerNumber());
        Assert.assertNull(res.getUserId());
        Assert.assertNull(res.getName());
        Assert.assertNull(res.getExpireSeconds());
    }
}
