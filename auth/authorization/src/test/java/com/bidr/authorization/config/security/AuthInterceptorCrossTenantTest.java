package com.bidr.authorization.config.security;

import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.captcha.CaptchaService;
import com.bidr.authorization.service.sms.MsgVerificationService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.tenant.CrossTenant;
import com.bidr.authorization.tenant.TenantExemptIdentityFill;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuthInterceptor @CrossTenant 消费迁移等价腿（棒1 租户豁免治理，离线单测）。
 *
 * <p>0.4b 时消费逻辑在业务侧拦截器（注解扫描＋置平台档＋WARN＋身份注入）；2026-10-24
 * 豁免治理迁 core（ADR 0001 裁定 5）——本类钉死迁移后的框架面契约：
 * <ol>
 *   <li>方法/类注解命中＋已认证 → {@code TenantContext.isPlatform()==true}（读侧豁免位），
 *       且身份接线 SPI 恰好回调一次、租户槽不被清（置位不清值，与原实现一致）；</li>
 *   <li>无注解端点零影响（不置位、不回调）；</li>
 *   <li>未认证不触发（豁免在鉴权链之后生效，fail-closed）；</li>
 *   <li>宿主无 SPI 实现时照常置位（框架面独立于业务面）；</li>
 *   <li>afterCompletion 既有 {@code TenantContext.remove()} 收走平台档（清理复用核对）。</li>
 * </ol>
 * 业务身份面升格（身份键写入）归 ERP 侧 {@code TenantExemptIdentityFill} 实现自证，
 * 不在本模块视野内（core 不感知业务键）。</p>
 *
 * @author coder (棒1, 租户豁免治理 2026-10-24)
 */
public class AuthInterceptorCrossTenantTest {

    private static WebApplicationContext staticCtxForBeanUtil;

    private AuthInterceptor interceptor;
    private AuthInterceptor cleanHostInterceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    /** SPI 回调计数（每次测试重置） */
    private final AtomicInteger fillCalls = new AtomicInteger();

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/erp/admin/test/thing");
        response = new MockHttpServletResponse();

        // BeanUtil.getActiveProfile 在 enableCrossDomain 里被静态调用——挂一个最小 Web 上下文
        StaticWebApplicationContext wac = new StaticWebApplicationContext();
        wac.getEnvironment().setActiveProfiles("test");
        wac.getBeanFactory().registerSingleton("authLogin", Mockito.mock(AuthLogin.class));
        wac.refresh();
        staticCtxForBeanUtil = wac;
        BeanUtil.setContext(wac);

        @SuppressWarnings("unchecked")
        ObjectProvider<TenantExemptIdentityFill> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable())
                .thenAnswer(inv -> (TenantExemptIdentityFill) fillCalls::incrementAndGet);

        interceptor = new AuthInterceptor(wac, Mockito.mock(TokenService.class),
                Mockito.mock(CaptchaService.class), Mockito.mock(MsgVerificationService.class), provider);

        // "宿主无 SPI 实现"形态：getIfAvailable 返回 null
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantExemptIdentityFill> emptyProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
        cleanHostInterceptor = new AuthInterceptor(wac, Mockito.mock(TokenService.class),
                Mockito.mock(CaptchaService.class), Mockito.mock(MsgVerificationService.class), emptyProvider);
    }

    @After
    public void tearDown() {
        AccountContext.remove();
        TenantContext.remove();
        TokenHolder.remove();
        ClientTypeHolder.remove();
    }

    @AfterClass
    public static void resetBeanUtilContext() throws Exception {
        // 复位静态上下文，防污染同 JVM 后续测试类
        Field ctx = BeanUtil.class.getDeclaredField("ctx");
        ctx.setAccessible(true);
        ctx.set(null, null);
        staticCtxForBeanUtil = null;
    }

    /** 建一个已认证态（等价于鉴权链 validate 填充后的形态）＋core 租户槽占用（证明置位不清槽） */
    private void loginWithTenantSlots() {
        AccountInfo info = new AccountInfo();
        info.setUserId(7L);
        info.setUserName("op");
        AccountContext.set(info);
        TenantContext.set(Collections.singletonList(5L));
        TenantContext.setCurrentTenantId(5L);
    }

    private HandlerMethod handler(String method) throws Exception {
        if ("listed".equals(method)) {
            return new HandlerMethod(new TypeAnnotatedEndpoint(), TypeAnnotatedEndpoint.class.getMethod("listed"));
        }
        return new HandlerMethod(new MethodAnnotatedEndpoint(), MethodAnnotatedEndpoint.class.getMethod(method));
    }

    @Test
    public void methodAnnotation_marksPlatform_fillsIdentityViaSpi_keepsTenantSlots() throws Exception {
        loginWithTenantSlots();
        Assert.assertTrue(interceptor.preHandle(request, response, handler("cross")));
        Assert.assertTrue("方法注解生效：读侧豁免位必须置上", TenantContext.isPlatform());
        Assert.assertEquals("身份接线 SPI 恰好回调一次", 1, fillCalls.get());
        Assert.assertEquals("置平台档不清租户集槽（与 0.4b 原实现一致）",
                Collections.singletonList(5L), TenantContext.getTenantIds());
        Assert.assertEquals("当前租户槽同理不清", Long.valueOf(5L), TenantContext.currentTenantId());
    }

    @Test
    public void typeAnnotation_alsoMarksPlatform() throws Exception {
        loginWithTenantSlots();
        Assert.assertTrue(interceptor.preHandle(request, response, handler("listed")));
        Assert.assertTrue("类注解兜底生效", TenantContext.isPlatform());
        Assert.assertEquals(1, fillCalls.get());
    }

    @Test
    public void noAnnotation_endpointUntouched() throws Exception {
        loginWithTenantSlots();
        Assert.assertTrue(interceptor.preHandle(request, response, handler("normal")));
        Assert.assertFalse("无注解不得置平台档", TenantContext.isPlatform());
        Assert.assertEquals("无注解不得回调身份接线", 0, fillCalls.get());
        Assert.assertEquals(Long.valueOf(5L), TenantContext.currentTenantId());
    }

    @Test
    public void unauthenticated_doesNotTrigger() throws Exception {
        AccountContext.remove();
        Assert.assertTrue(interceptor.preHandle(request, response, handler("cross")));
        Assert.assertFalse("未认证不触发（豁免在鉴权链之后生效）", TenantContext.isPlatform());
        Assert.assertEquals(0, fillCalls.get());
    }

    @Test
    public void hostWithoutSpiImpl_stillMarksPlatform() throws Exception {
        loginWithTenantSlots();
        Assert.assertTrue(cleanHostInterceptor.preHandle(request, response, handler("cross")));
        Assert.assertTrue("框架面独立于业务面：无实现也置位", TenantContext.isPlatform());
    }

    @Test
    public void afterCompletion_clearsPlatformMark() throws Exception {
        loginWithTenantSlots();
        HandlerMethod cross = handler("cross");
        Assert.assertTrue(interceptor.preHandle(request, response, cross));
        Assert.assertTrue(TenantContext.isPlatform());
        interceptor.afterCompletion(request, response, cross, null);
        Assert.assertFalse("请求收尾必须收走平台档（AuthAdmin 腿同款清理复用）", TenantContext.isPlatform());
    }

    /** 方法级注解端点：cross() 命中 / normal() 对照 */
    static class MethodAnnotatedEndpoint {
        @CrossTenant
        public void cross() {
        }

        public void normal() {
        }
    }

    /** 类级注解端点：listed() 兜底命中 */
    @CrossTenant
    static class TypeAnnotatedEndpoint {
        public void listed() {
        }
    }
}
