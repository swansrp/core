package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

/**
 * Title: SsoTenantModeGateTest
 * Description: SSO/IdM 首登的租户模式闸离线守卫（2026-10-27 钉三口径 → 2026-11-11 拍板「SSO
 * 登录账号默认绑定 bidr 租户」后建号闸改为租户正源语义）：
 * ①建号闸＝tenant+正源在位放行（JIT 建号由 createUser 漏斗按 identity.tenant.code 赋籍）/tenant+正源
 * 缺位仍拒（fail-closed）/closed 一律拒/open 保持原行为；②tenant 下存量 SSO/REGISTER 类型零归属
 * 账号拒绝登录（不动）；③SYSTEM/ACCOUNT 与 open 模式不误伤（不动）。纯 static 直断，不起 Spring、不连库。
 *
 * @author Sharp
 * @since 2026-10-27
 */
public class SsoTenantModeGateTest {

    /** mode＝yml {@code my.auth.register-mode}（唯一来源；null/空白归一为 open）。 */
    private static LoginServiceImpl service(String mode, AcUserTenantService relationService) {
        return service(mode, relationService, java.util.Optional.empty());
    }

    /** tenantResolver 可注入租户正源形态（有值=identity.tenant.code 已配置）。 */
    private static LoginServiceImpl service(String mode, AcUserTenantService relationService,
                                            java.util.Optional<String> tenantCode) {
        com.bidr.authorization.tenant.TenantResolver resolver = Mockito.mock(com.bidr.authorization.tenant.TenantResolver.class);
        Mockito.when(resolver.resolveTenantCode(Mockito.any())).thenReturn(tenantCode);
        LoginServiceImpl impl = new LoginServiceImpl(
                Mockito.mock(AcUserService.class),
                Mockito.mock(AcTenantService.class),
                relationService,
                Mockito.mock(SysConfigCacheService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(AcUserRoleMenuService.class),
                Mockito.mock(CreateUserService.class),
                Mockito.mock(HttpServletRequest.class),
                Mockito.mock(com.bidr.authorization.service.tenant.TenantBindOnLoginService.class),
                new RegisterModeService(),
                resolver);
        impl.setRegisterModeYml(mode);
        return impl;
    }

    private static AcUserTenantService relationService(List<AcUserTenant> rows) {
        AcUserTenantService svc = Mockito.mock(AcUserTenantService.class);
        Mockito.when(svc.listByUserId(Mockito.any())).thenReturn(rows);
        return svc;
    }

    private static AcUser user(String userType) {
        AcUser user = new AcUser();
        user.setUserId(9L);
        user.setUserType(userType);
        return user;
    }

    // ==================== ① 建号前闸 ====================

    @Test
    public void openModeAllowsSsoAutoCreate() {
        service("open", relationService(Collections.emptyList())).assertSsoAutoCreateAllowed();
    }

    /** 2026-11-11 拍板：tenant+租户正源在位 → SSO JIT 建号放行（漏斗按 identity.tenant.code 赋籍）。 */
    @Test
    public void tenantModeAllowsSsoAutoCreateWithTenantSource() {
        service("tenant", relationService(Collections.emptyList()), java.util.Optional.of("bidr"))
                .assertSsoAutoCreateAllowed();
    }

    /** 正源缺位（identity.tenant.code 未配置）→ 维持 fail-closed 拒绝（零归属旁路闸）。 */
    @Test
    public void tenantModeDeniesSsoAutoCreateWithoutTenantSource() {
        assertThrows(ServiceException.class,
                () -> service("tenant", relationService(Collections.emptyList())).assertSsoAutoCreateAllowed());
    }

    @Test
    public void closedModeDeniesSsoAutoCreate() {
        assertThrows(ServiceException.class,
                () -> service("closed", relationService(Collections.emptyList()),
                        java.util.Optional.of("bidr")).assertSsoAutoCreateAllowed());
    }

    // ==================== ② 登录前闸 ====================

    @Test
    public void tenantModeDeniesTenantlessSsoAccount() {
        LoginServiceImpl svc = service("tenant", relationService(Collections.emptyList()));
        assertThrows(ServiceException.class, () -> svc.assertSsoTenantReady(user(UserTypeDict.SSO.getValue())));
    }

    @Test
    public void tenantModeDeniesTenantlessRegisterAccount() {
        LoginServiceImpl svc = service("tenant", relationService(Collections.emptyList()));
        assertThrows(ServiceException.class, () -> svc.assertSsoTenantReady(user(UserTypeDict.REGISTER.getValue())));
    }

    @Test
    public void tenantModeAllowsSsoAccountWithTenant() {
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(9L);
        relation.setTenantId(3L);
        service("tenant", relationService(Collections.singletonList(relation)))
                .assertSsoTenantReady(user(UserTypeDict.SSO.getValue()));
    }

    // ==================== ③ 不误伤系统级/open ====================

    @Test
    public void tenantModeAllowsSystemAccountWithoutTenant() {
        service("tenant", relationService(Collections.emptyList()))
                .assertSsoTenantReady(user(UserTypeDict.SYSTEM.getValue()));
    }

    @Test
    public void openModeAllowsTenantlessSsoAccount() {
        service("open", relationService(Collections.emptyList()))
                .assertSsoTenantReady(user(UserTypeDict.SSO.getValue()));
    }

    @Test
    public void nullUserIsNoop() {
        try {
            service("tenant", relationService(Collections.emptyList())).assertSsoTenantReady(null);
        } catch (RuntimeException e) {
            fail("null user 应放行（无账号可判）：" + e.getMessage());
        }
    }
}