package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.config.TenantDataGate;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;

/**
 * Title: AuthLoginDevAdminContextTest
 * Description: dev 冒烟令牌上下文接线守卫（纯 JUnit4，不起 Spring、不连库）。
 * <p>🔴 2026-11-02 修：{@code buildDevAdminContext} 原先只 set {@link AccountContext}，
 * 未接线 {@link TenantContext}——&& 口径下内置管理员（无租户绑定、非平台档）访问 ac_user
 * 会被拼恒假 1=0，冒烟腿读到空表。本测试钉死：dev 令牌必须置平台档，且数据闸据此旁路；
 * 非 dev profile 与错误令牌必须不命中（负对照）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class AuthLoginDevAdminContextTest {

    private AuthLogin authLogin;

    @Before
    public void setUp() throws Exception {
        authLogin = new AuthLogin(mock(TokenService.class), mock(SysConfigCacheService.class));
        setActiveProfiles("dev");
    }

    @After
    public void tearDown() {
        AccountContext.remove();
        TenantContext.remove();
        TokenHolder.remove();
    }

    private void setActiveProfiles(String profiles) throws Exception {
        Field field = AuthLogin.class.getDeclaredField("activeProfiles");
        field.setAccessible(true);
        field.set(authLogin, profiles);
    }

    private boolean invokeBuildDevAdminContext(String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(RequestConst.TOKEN, headerValue);
        }
        Method method = AuthLogin.class.getDeclaredMethod("buildDevAdminContext", HttpServletRequest.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(authLogin, request);
    }

    /** 正针：dev 令牌 ⇒ 账号上下文 + 平台档同时就位，数据闸对 ac_user 原样放行 */
    @Test
    public void devSmokeTokenMarksPlatformTierAndDataGateBypasses() throws Exception {
        Assert.assertTrue("dev profile + 固定令牌必须命中免登", invokeBuildDevAdminContext(AuthLogin.DEV_ADMIN_TOKEN));
        Assert.assertNotNull("必须构建账号上下文", AccountContext.get());
        Assert.assertTrue("🔴 内置管理员无租户绑定，必须置平台档，否则 && 口径下 ac_user 读恒假",
                TenantContext.isPlatform());
        Assert.assertNull("内置管理员不伪造租户集（平台档旁路）", TenantContext.getTenantIds());
        Expression where = CCJSqlParserUtil.parseCondExpression("status = '1'");
        Assert.assertSame("平台档必须让数据闸原样放行",
                where, TenantDataGate.appendGate(where, "ac_user", "ac_user"));
    }

    /** 负对照①：非 dev profile ⇒ 令牌不生效（免登口不得在 pre/prod 打开） */
    @Test
    public void nonDevProfileRejectsDevToken() throws Exception {
        setActiveProfiles("pre,prod");
        Assert.assertFalse("非 dev profile 必须拒绝 dev 冒烟令牌",
                invokeBuildDevAdminContext(AuthLogin.DEV_ADMIN_TOKEN));
        Assert.assertNull("未命中不得留下账号上下文", AccountContext.get());
        Assert.assertFalse("未命中不得置平台档", TenantContext.isPlatform());
    }

    /** 负对照②：dev profile 但令牌不匹配 ⇒ 不命中（证明命中判据读的是固定令牌而非恒真） */
    @Test
    public void wrongTokenDoesNotBypass() throws Exception {
        Assert.assertFalse("错误令牌不得免登", invokeBuildDevAdminContext("bm90LWEtdG9rZW4="));
        Assert.assertNull("未命中不得留下账号上下文", AccountContext.get());
        Assert.assertFalse("未命中不得置平台档", TenantContext.isPlatform());
    }
}
