package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.login.RoleBindService;
import com.bidr.authorization.tenant.TenantResolver;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

/**
 * Title: CreateUserServiceImplTest
 * Description: 建号漏斗租户赋籍（T1.1 棒3）离线单测——Mockito 直构不起 Spring。
 * <p>钉口径：三链（MDM 同步/飞书同步→AcAccount 重载；SSO JIT/注册/手机号→6 参重载）
 * 在漏斗内经 {@link TenantResolver} 赋租户——命中即幂等落 ac_user_tenant（is_admin 恒 false）、
 * 编码在库无行（配置漂移）拒建且零写入、解析空＋租户模式开拒建（fail-closed）、
 * 解析空＋模式关＝非租户形态合法不绑。拒建判定在落库前：抛错时连 ac_user 都不插。</p>
 * <p>同步链循环侧预检 {@code isTenantAssignable} 两态同钉（跳过逻辑依赖它与漏斗同判据）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class CreateUserServiceImplTest {

    private static final Long USER_ID = 88L;
    private static final Long TENANT_ID = 9L;
    private static final String CODE = "T1";

    private AcUserService acUserService;
    private AcTenantService acTenantService;
    private AcUserTenantService acUserTenantService;
    private TenantResolver tenantResolver;
    private CreateUserServiceImpl service;

    @Before
    public void setUp() {
        acUserService = Mockito.mock(AcUserService.class);
        acTenantService = Mockito.mock(AcTenantService.class);
        acUserTenantService = Mockito.mock(AcUserTenantService.class);
        tenantResolver = Mockito.mock(TenantResolver.class);
        // insert 回填主键（真实链由 MyBatis-Plus 回填），落籍需要 userId
        Mockito.when(acUserService.insert(Mockito.any(AcUser.class))).thenAnswer(inv -> {
            inv.getArgument(0, AcUser.class).setUserId(USER_ID);
            return true;
        });
        service = new CreateUserServiceImpl(
                acUserService,
                Mockito.mock(SysConfigCacheService.class),
                Mockito.mock(RoleBindService.class),
                Mockito.mock(AcUserDeptService.class),
                tenantResolver,
                acTenantService,
                acUserTenantService);
    }

    // ==================== 建号漏斗四态 ====================

    @Test
    public void resolvedTenantBindsOnCreate() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("ssoUser")).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(tenant(TENANT_ID));

        AcUser user = service.createUser("ssoUser", "md5hash", "名", null, null, null);

        Assert.assertNotNull(user);
        Mockito.verify(acUserTenantService).insertIfAbsent(USER_ID, TENANT_ID, Boolean.FALSE);
    }

    @Test
    public void accountOverloadBindsTenantForSyncChain() {
        // MDM/飞书同步链的 AcAccount 重载同判据
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("mdmUser")).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(tenant(TENANT_ID));

        AcAccount account = new AcAccount();
        account.setId("MDM001");
        account.setUserName("mdmUser");
        account.setDepartment("D1");
        AcUser user = service.createUser(account);

        Assert.assertNotNull(user);
        Mockito.verify(acUserTenantService).insertIfAbsent(USER_ID, TENANT_ID, Boolean.FALSE);
    }

    @Test
    public void missingTenantRowRejectsCreateWithZeroWrite() {
        // 配置说有租户而库里没有＝A/B 库漂移，宁可拒建；拒建判定在落库前 ⇒ 连 ac_user 都不插
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("ssoUser")).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(null);

        try {
            service.createUser("ssoUser", "md5hash", "名", null, null, null);
            Assert.fail("编码在库无行必须拒建");
        } catch (IllegalStateException e) {
            Assert.assertTrue(String.valueOf(e.getMessage()).contains(CODE));
        }
        Mockito.verify(acUserService, Mockito.never()).insert(Mockito.any(AcUser.class));
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void emptyResolverTenantModeOnRejectsCreate() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("ssoUser")).thenReturn(Optional.empty());

        try {
            service.createUser("ssoUser", "md5hash", "名", null, null, null);
            Assert.fail("租户模式开启下解析不到归属必须拒建（fail-closed）");
        } catch (IllegalStateException e) {
            Assert.assertTrue("异常信息须带 username 便于排查",
                    String.valueOf(e.getMessage()).contains("ssoUser"));
        }
        Mockito.verify(acUserService, Mockito.never()).insert(Mockito.any(AcUser.class));
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void emptyResolverTenantModeOffCreatesWithoutBinding() {
        // 非租户交付形态：解析空＝合法不绑，建号照常
        service.setTenantLineEnabled(false);
        Mockito.when(tenantResolver.resolveTenantCode("ssoUser")).thenReturn(Optional.empty());

        AcUser user = service.createUser("ssoUser", "md5hash", "名", null, null, null);

        Assert.assertNotNull(user);
        Mockito.verify(acUserService).insert(Mockito.any(AcUser.class));
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // ==================== 同步链预检两态（跳过逻辑依赖它与漏斗同判据） ====================

    @Test
    public void tenantAssignableTrueWhenResolvedAndPresent() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("mdmUser")).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(tenant(TENANT_ID));

        Assert.assertTrue(service.isTenantAssignable("mdmUser"));
    }

    @Test
    public void tenantAssignableFalseWhenUnresolvedInTenantMode() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode("mdmUser")).thenReturn(Optional.empty());

        Assert.assertFalse("租户模式下解析不到归属＝同步循环应跳过该身份", service.isTenantAssignable("mdmUser"));
    }

    // ==================== 夹具 ====================

    private static AcTenant tenant(Long id) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName("租户甲");
        tenant.setCode(CODE);
        tenant.setValid("1");
        return tenant;
    }
}
