package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: LoginRegisterModeTest
 * Description: 自助注册三态闸的离线守卫——ACCOUNT_REGISTER_MODE（open/tenant/closed）归一逻辑
 * 纯 static 直断、闸行为与租户绑定腿走 Mockito 直穿 {@code assertRegisterAllowed}/{@code bindRegisterTenant}
 * （包可见离线口），不起 Spring、不连库。钉两条口径：①未知/缺省必须回落 open＝共享兼容
 * （参数写错不得把存量开放注册项目突然锁死）；②闸五态（closed 拒/tenant 缺码拒/错码拒/命中放行/open 放行）
 * 与 is_admin 恒 false 的裁定针。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class LoginRegisterModeTest {

    private static LoginServiceImpl service(String mode, AcTenantService tenantRepo) {
        SysConfigCacheService cache = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(cache.getParamValueAvail(AccountParam.ACCOUNT_REGISTER_MODE.name())).thenReturn(mode);
        return new LoginServiceImpl(
                Mockito.mock(AcUserService.class),
                tenantRepo,
                Mockito.mock(AcUserTenantService.class),
                cache,
                Mockito.mock(TokenService.class),
                Mockito.mock(AcUserRoleMenuService.class),
                Mockito.mock(CreateUserService.class),
                Mockito.mock(HttpServletRequest.class));
    }

    private static AcTenant tenant(long id) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName("租户甲");
        tenant.setValid("1");
        return tenant;
    }

    @Test
    public void explicitModesNormalizeVerbatim() {
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("open"));
        Assert.assertEquals("tenant", LoginServiceImpl.normalizeRegisterMode("tenant"));
        Assert.assertEquals("closed", LoginServiceImpl.normalizeRegisterMode("closed"));
    }

    @Test
    public void caseAndWhitespaceTolerated() {
        Assert.assertEquals("tenant", LoginServiceImpl.normalizeRegisterMode(" Tenant "));
        Assert.assertEquals("closed", LoginServiceImpl.normalizeRegisterMode("CLOSED"));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("Open"));
    }

    @Test
    public void unknownOrMissingFallsBackToOpen() {
        // 共享兼容口径：参数缺行/写错回落开放注册，绝不静默锁死存量项目
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode(null));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode(""));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("  "));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("yes"));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("1"));
        Assert.assertEquals("open", LoginServiceImpl.normalizeRegisterMode("TENANT_ONLY"));
    }

    // ==================== 闸行为五腿（reviewer C 轴补：闸本体零覆盖的修复） ====================

    @Test
    public void closedModeDeniesAnyRegistration() {
        LoginServiceImpl service = service("closed", Mockito.mock(AcTenantService.class));
        try {
            service.assertRegisterAllowed(null);
            Assert.fail("closed 态必须拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(String.valueOf(e.getMessage()).contains("已关闭"));
        }
    }

    @Test
    public void tenantModeDeniesMissingCode() {
        LoginServiceImpl service = service("tenant", Mockito.mock(AcTenantService.class));
        try {
            service.assertRegisterAllowed("   ");
            Assert.fail("tenant 态缺码必须拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(String.valueOf(e.getMessage()).contains("租户注册链接"));
        }
    }

    @Test
    public void tenantModeDeniesInvalidCode() {
        AcTenantService repo = Mockito.mock(AcTenantService.class);
        Mockito.when(repo.getByCode("BADCODE1")).thenReturn(null);
        LoginServiceImpl service = service("tenant", repo);
        try {
            service.assertRegisterAllowed("BADCODE1");
            Assert.fail("tenant 态错码必须拒绝");
        } catch (ServiceException e) {
            // 文案不得泄露字段语义（不提"租户/码/停用"），统一按链接失效口径
            String msg = String.valueOf(e.getMessage());
            Assert.assertTrue("应按链接失效口径拒绝，实际：" + msg, msg.contains("注册链接无效"));
            Assert.assertFalse("不得出现'租户'字样（泄露字段语义）", msg.contains("租户"));
            Assert.assertFalse("不得出现'停用'字样（确认存在性）", msg.contains("停用"));
        }
    }

    @Test
    public void tenantModeWithValidCodePasses() {
        AcTenantService repo = Mockito.mock(AcTenantService.class);
        Mockito.when(repo.getByCode("GOODCODE")).thenReturn(tenant(7L));
        LoginServiceImpl service = service("tenant", repo);
        AcTenant tenant = service.assertRegisterAllowed(" GOODCODE ");
        Assert.assertNotNull("命中码放行并带出租户", tenant);
        Assert.assertEquals(7L, tenant.getId().longValue());
    }

    @Test
    public void openModeWithoutCodePassesAndBoundTenantSkipped() {
        LoginServiceImpl service = service("open", Mockito.mock(AcTenantService.class));
        Assert.assertNull("open 态无码＝全局账号不挂租户", service.assertRegisterAllowed(null));
    }

    @Test
    public void openModeWithValidCodeAlsoPasses() {
        // /register/CODE 链接在 open 态同样可用：码给了就校验并出租户（三态表承诺）
        AcTenantService repo = Mockito.mock(AcTenantService.class);
        Mockito.when(repo.getByCode("GOODCODE")).thenReturn(tenant(7L));
        LoginServiceImpl service = service("open", repo);
        AcTenant tenant = service.assertRegisterAllowed("GOODCODE");
        Assert.assertNotNull("open 态带有效码同样放行并出租户", tenant);
        Assert.assertEquals(7L, tenant.getId().longValue());
    }

    // ==================== 绑定腿（is_admin 恒 false 的裁定针） ====================

    @Test
    public void bindRegisterTenantInsertsPlainMembership() {
        AcUserTenantService relationRepo = Mockito.mock(AcUserTenantService.class);
        LoginServiceImpl service = new LoginServiceImpl(
                Mockito.mock(AcUserService.class),
                Mockito.mock(AcTenantService.class),
                relationRepo,
                Mockito.mock(SysConfigCacheService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(AcUserRoleMenuService.class),
                Mockito.mock(CreateUserService.class),
                Mockito.mock(HttpServletRequest.class));
        service.bindRegisterTenant(100L, tenant(7L));
        ArgumentCaptor<AcUserTenant> captor = ArgumentCaptor.forClass(AcUserTenant.class);
        Mockito.verify(relationRepo).insert(captor.capture());
        Assert.assertEquals("挂关系必须指向命中租户", 7L, captor.getValue().getTenantId().longValue());
        Assert.assertEquals("绑定用户", 100L, captor.getValue().getUserId().longValue());
        Assert.assertEquals("is_admin 恒 false（注册入口不得自授管理员）", Boolean.FALSE, captor.getValue().getIsAdmin());
    }
}

