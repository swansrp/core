package com.bidr.authorization.service.tenant;

import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.tenant.TenantResolver;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Title: TenantBindOnLoginServiceTest
 * Description: 登录补绑钩子（T1.1 棒3）离线单测——Mockito 直构不起 Spring。
 * <p>钉口径：租户模式关钩子整体惰性；已有绑定跳过；平台档（超管）零绑定不绑不拒
 * （判据＝登录时点 DB 角色集 × sys_config ACCOUNT_ADMIN_ROLE_ID，与 PermitService.containsAdminRole
 * 同一纯函数——登录时点 token 未装配，isAdmin() 不可用）；存量零绑定老号补绑一次
 * （幂等落 ac_user_tenant，is_admin 恒 false）；resolver 空／编码在库无行（漂移）拒登 fail-closed。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class TenantBindOnLoginServiceTest {

    private static final Long USER_ID = 2554L;
    private static final Long ADMIN_ROLE_ID = 1L;
    private static final Long TENANT_ID = 9L;
    private static final String USER_NAME = "legacyUser";
    private static final String CODE = "T1";

    private AcTenantService acTenantService;
    private AcUserTenantService acUserTenantService;
    private SysConfigCacheService sysConfigCacheService;
    private TenantResolver tenantResolver;
    private TenantBindOnLoginService service;

    @Before
    public void setUp() {
        acTenantService = Mockito.mock(AcTenantService.class);
        acUserTenantService = Mockito.mock(AcUserTenantService.class);
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        tenantResolver = Mockito.mock(TenantResolver.class);
        Mockito.when(sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID)).thenReturn(ADMIN_ROLE_ID);
        Mockito.when(acUserTenantService.listByUserId(USER_ID)).thenReturn(Collections.emptyList());
        service = new TenantBindOnLoginService(tenantResolver, acTenantService, acUserTenantService,
                sysConfigCacheService);
    }

    // ==================== 登录钩子四态 ====================

    @Test
    public void legacyUnboundUserGetsBoundOnLogin() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode(USER_NAME)).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(tenant(TENANT_ID));

        service.bindOnLogin(USER_ID, USER_NAME, new HashMap<>(0));

        Mockito.verify(acUserTenantService).insertIfAbsent(USER_ID, TENANT_ID, Boolean.FALSE);
    }

    @Test
    public void platformAdminStaysUnbound() {
        // 种子超管零绑定旁路定稿：不绑不拒，连 resolver 都不碰
        service.setTenantLineEnabled(true);

        service.bindOnLogin(USER_ID, USER_NAME, adminRoleMap());

        Mockito.verifyNoInteractions(tenantResolver);
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void alreadyBoundUserSkipsHook() {
        // 补绑只做一次：已有绑定直接返回（非超管也跳，判据顺序＝绑定优先于角色）
        service.setTenantLineEnabled(true);
        Mockito.when(acUserTenantService.listByUserId(USER_ID))
                .thenReturn(Collections.singletonList(relation(7L)));

        service.bindOnLogin(USER_ID, USER_NAME, new HashMap<>(0));

        Mockito.verifyNoInteractions(tenantResolver);
        Mockito.verifyNoInteractions(sysConfigCacheService);
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void emptyResolverTenantModeOnRejectsLogin() {
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode(USER_NAME)).thenReturn(Optional.empty());

        try {
            service.bindOnLogin(USER_ID, USER_NAME, new HashMap<>(0));
            Assert.fail("租户模式下零绑定且解析不到归属必须拒登（fail-closed）");
        } catch (ServiceException e) {
            Assert.assertTrue("拒登信息须带 username 便于排查",
                    String.valueOf(e.getMessage()).contains(USER_NAME));
        }
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // ==================== 边界腿 ====================

    @Test
    public void tenantModeOffHookInert() {
        // 非租户交付形态：登录链零感知，不绑不拒不查
        service.setTenantLineEnabled(false);

        service.bindOnLogin(USER_ID, USER_NAME, new HashMap<>(0));

        Mockito.verifyNoInteractions(acUserTenantService, tenantResolver, sysConfigCacheService);
    }

    @Test
    public void resolvedButMissingTenantRowRejectsLogin() {
        // 放行即零绑定旁路形态：命中编码但库里无行（漂移）同拒
        service.setTenantLineEnabled(true);
        Mockito.when(tenantResolver.resolveTenantCode(USER_NAME)).thenReturn(Optional.of(CODE));
        Mockito.when(acTenantService.getByCode(CODE)).thenReturn(null);

        try {
            service.bindOnLogin(USER_ID, USER_NAME, new HashMap<>(0));
            Assert.fail("编码在库无行（漂移）必须拒登");
        } catch (ServiceException e) {
            Assert.assertTrue(String.valueOf(e.getMessage()).contains(USER_NAME));
        }
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // ==================== 夹具 ====================

    private Map<Long, RoleInfo> adminRoleMap() {
        RoleInfo adminRole = new RoleInfo();
        adminRole.setRoleId(ADMIN_ROLE_ID);
        adminRole.setRoleName("系统管理员");
        Map<Long, RoleInfo> roleInfoMap = new HashMap<>(1);
        roleInfoMap.put(ADMIN_ROLE_ID, adminRole);
        return roleInfoMap;
    }

    private static AcUserTenant relation(Long tenantId) {
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(USER_ID);
        relation.setTenantId(tenantId);
        relation.setIsAdmin(Boolean.FALSE);
        return relation;
    }

    private static AcTenant tenant(Long id) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName("租户甲");
        tenant.setCode(CODE);
        tenant.setValid("1");
        return tenant;
    }
}
