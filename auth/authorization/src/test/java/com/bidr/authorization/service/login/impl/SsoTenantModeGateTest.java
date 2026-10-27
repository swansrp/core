package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.dao.entity.SysConfig;
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
 * Description: SSO/IdM 首登的租户模式闸离线守卫（2026-10-27 epc×墨枢联调）——钉三条口径：
 * ①tenant/closed 下禁止自动建号（杜绝零租户归属＝数据旁路）；②tenant 下存量 SSO/REGISTER
 * 类型零归属账号拒绝登录；③SYSTEM/ACCOUNT 与 open 模式保持原行为（系统管理级旁路语义不误伤）。
 * 纯 static 直断，不起 Spring、不连库。
 *
 * @author Sharp
 * @since 2026-10-27
 */
public class SsoTenantModeGateTest {

    private static LoginServiceImpl service(String mode, AcUserTenantService relationService) {
        SysConfigCacheService cache = Mockito.mock(SysConfigCacheService.class);
        if (mode != null) {
            SysConfig row = new SysConfig();
            row.setConfigKey(AccountParam.ACCOUNT_REGISTER_MODE.name());
            row.setConfigValue(mode);
            Mockito.when(cache.getCache(AccountParam.ACCOUNT_REGISTER_MODE.name())).thenReturn(row);
        }
        return new LoginServiceImpl(
                Mockito.mock(AcUserService.class),
                Mockito.mock(AcTenantService.class),
                relationService,
                cache,
                Mockito.mock(TokenService.class),
                Mockito.mock(AcUserRoleMenuService.class),
                Mockito.mock(CreateUserService.class),
                Mockito.mock(HttpServletRequest.class));
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

    @Test
    public void tenantModeDeniesSsoAutoCreate() {
        assertThrows(ServiceException.class,
                () -> service("tenant", relationService(Collections.emptyList())).assertSsoAutoCreateAllowed());
    }

    @Test
    public void closedModeDeniesSsoAutoCreate() {
        assertThrows(ServiceException.class,
                () -> service("closed", relationService(Collections.emptyList())).assertSsoAutoCreateAllowed());
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