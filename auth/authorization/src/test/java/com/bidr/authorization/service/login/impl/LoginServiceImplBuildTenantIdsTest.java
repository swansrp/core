package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.service.tenant.TenantBindOnLoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.tenant.TenantResolver;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: LoginServiceImplBuildTenantIdsTest
 * Description: buildTenantIds 离线单测（#65 B′ 补腿）——Mockito 直构不起 Spring。
 * <p>钉口径：有绑定⇒TENANT_IDS+TENANT_ID=首个绑定（既有行为逐字不动，B′ 腿不走）；
 * 零绑定平台超管+租户模式开⇒🔴 仅写 TENANT_ID=首个有效租户（绝不写 TENANT_IDS——写宽即推翻
 * TenantMyService.elevated 零绑定前腿＝齿轮自毁）；零绑定非超管／listValid 空／开关关⇒两键均不写。
 * 超管判据＝登录时点 DB 现查 roleMap × sys_config ACCOUNT_ADMIN_ROLE_ID ×
 * PermitService.containsAdminRole 纯函数（登录时点 token 未装配，isAdmin() 不可用）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-13
 */
public class LoginServiceImplBuildTenantIdsTest {

    private static final Long USER_ID = 2554L;
    private static final Long ADMIN_ROLE_ID = 1L;
    private static final Long TENANT_ID = 9L;

    private AcTenantService acTenantService;
    private AcUserTenantService acUserTenantService;
    private SysConfigCacheService frameCacheService;
    private TokenService tokenService;
    private LoginServiceImpl service;
    private TokenInfo accessToken;

    @Before
    public void setUp() {
        acTenantService = Mockito.mock(AcTenantService.class);
        acUserTenantService = Mockito.mock(AcUserTenantService.class);
        frameCacheService = Mockito.mock(SysConfigCacheService.class);
        tokenService = Mockito.mock(TokenService.class);
        Mockito.when(frameCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID))
                .thenReturn(ADMIN_ROLE_ID);
        // LoginServiceImpl 全构造参 mock（11 参，@RequiredArgsConstructor 声明序）；测试面只驱动 buildTenantIds
        service = new LoginServiceImpl(
                Mockito.mock(AcUserService.class),
                acTenantService,
                acUserTenantService,
                frameCacheService,
                tokenService,
                Mockito.mock(AcUserRoleMenuService.class),
                Mockito.mock(CreateUserService.class),
                Mockito.mock(HttpServletRequest.class),
                Mockito.mock(TenantBindOnLoginService.class),
                Mockito.mock(RegisterModeService.class),
                Mockito.mock(TenantResolver.class));
        accessToken = new TokenInfo();
    }

    // ==================== B′ 补腿四态＋开关关 ====================

    @Test
    public void zeroBindAdminWithLineOnWritesOnlyTenantId() {
        // #65 进门形态：仅烤 TENANT_ID=首个有效租户（listValid 按 id 升序首行确定）
        service.setTenantLineEnabled(true);
        Mockito.when(acUserTenantService.listTenantIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());
        Mockito.when(acTenantService.listValid())
                .thenReturn(Arrays.asList(tenant(5L), tenant(TENANT_ID)));

        service.buildTenantIds(USER_ID, adminRoleMap(), accessToken);

        Mockito.verify(tokenService).putItem(accessToken, TokenItem.TENANT_ID.name(), 5L);
        // 🔴 齿轮红线：绝不写 TENANT_IDS（写宽即推翻 elevated() 零绑定前腿）
        Mockito.verify(tokenService, Mockito.never())
                .putItem(Mockito.eq(accessToken), Mockito.eq(TokenItem.TENANT_IDS.name()), Mockito.any());
        Mockito.verifyNoMoreInteractions(tokenService);
        // 不写绑定行：零绑定形态保留（本方法职责仅烤 token）
        Mockito.verify(acUserTenantService, Mockito.never())
                .insertIfAbsent(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void zeroBindNonAdminWritesNeitherKey() {
        service.setTenantLineEnabled(true);
        Mockito.when(acUserTenantService.listTenantIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());

        service.buildTenantIds(USER_ID, new HashMap<>(0), accessToken);

        Mockito.verifyNoInteractions(tokenService);
        Mockito.verify(acTenantService, Mockito.never()).listValid();
    }

    @Test
    public void boundUserKeepsLegacyBothKeysAndSkipsBPrimeLeg() {
        // 既有行为逐字不动：有绑定⇒TENANT_IDS+TENANT_ID=首个绑定，B′ 腿不走。
        // 🔴 开关须显式开（review I-1）：否则 never()).listValid() 由开关短路兜底＝空真钉，
        // 护不住「有绑定分支提前 return」这一结构件（被删时 B′ 腿会覆写已烤 TENANT_ID）
        service.setTenantLineEnabled(true);
        List<Long> bound = Arrays.asList(7L, TENANT_ID);
        Mockito.when(acUserTenantService.listTenantIdsByUserId(USER_ID)).thenReturn(bound);

        service.buildTenantIds(USER_ID, adminRoleMap(), accessToken);

        Mockito.verify(tokenService).putItem(accessToken, TokenItem.TENANT_IDS.name(), bound);
        Mockito.verify(tokenService).putItem(accessToken, TokenItem.TENANT_ID.name(), 7L);
        Mockito.verifyNoMoreInteractions(tokenService);
        Mockito.verify(acTenantService, Mockito.never()).listValid();
        Mockito.verify(frameCacheService, Mockito.never())
                .getParamLong(Mockito.any(com.bidr.kernel.constant.param.Param.class));
    }

    @Test
    public void zeroBindAdminEmptyValidListWritesNothingAndDoesNotThrow() {
        // 防越界：listValid 空列表 ⇒ 两键不写、不抛
        service.setTenantLineEnabled(true);
        Mockito.when(acUserTenantService.listTenantIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());
        Mockito.when(acTenantService.listValid()).thenReturn(Collections.emptyList());

        service.buildTenantIds(USER_ID, adminRoleMap(), accessToken);

        Mockito.verifyNoInteractions(tokenService);
    }

    @Test
    public void zeroBindAdminWithLineOffWritesNothing() {
        // 开关关＝非租户交付形态：B′ 补腿惰性，连 listValid 都不查
        service.setTenantLineEnabled(false);
        Mockito.when(acUserTenantService.listTenantIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());

        service.buildTenantIds(USER_ID, adminRoleMap(), accessToken);

        Mockito.verifyNoInteractions(tokenService);
        Mockito.verify(acTenantService, Mockito.never()).listValid();
        Mockito.verify(frameCacheService, Mockito.never())
                .getParamLong(Mockito.any(com.bidr.kernel.constant.param.Param.class));
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

    private static AcTenant tenant(Long id) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName("租户" + id);
        tenant.setValid("1");
        return tenant;
    }
}
