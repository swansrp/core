package com.bidr.authorization.service.tenant;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.TenantMyVO;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: TenantMyServiceTest
 * Description: 框架内置「我的租户」清单/切换的离线单测——真 ThreadLocal 上下文
 * （AccountContext/TenantContext 直设直清）＋构造注入 Mockito 桩，不起 Spring。
 * <p>覆盖：成员模式清单装配（对齐/current/admin 标/停用租户不展示）／系统管理级与平台档
 * 全量腿（含成员关系行 isAdmin 标真）／switch 四态：成员只改 TENANT_ID（零位移）、
 * 平台档切非成员租户两键同改（数据闸跟切）、非成员拒、停用租户拒、空参拒。</p>
 * <p>🔴 提权档判定两腿各有用例：平台档（matchesAdminRole×sys_config 角色参数）
 * 与系统管理级（登录烤集为空）——10-20 撤回的死路（isPlatform() 切面位）不在被测面。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-21
 */
public class TenantMyServiceTest {

    private static final Long USER_ID = 2554L;
    private static final Long ADMIN_ROLE_ID = 1L;

    private AcTenantService acTenantService;
    private AcUserTenantService acUserTenantService;
    private SysConfigCacheService sysConfigCacheService;
    private TokenService tokenService;
    private TenantMyService service;

    private final AcTenant t1 = tenant(1L, "设计院A", "T1", "1");
    private final AcTenant t2 = tenant(2L, "施工单位B", "T2", "1");
    private final AcTenant t3Disabled = tenant(3L, "已停用C", "T3", "0");

    @Before
    public void setUp() {
        acTenantService = Mockito.mock(AcTenantService.class);
        acUserTenantService = Mockito.mock(AcUserTenantService.class);
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        tokenService = Mockito.mock(TokenService.class);
        service = new TenantMyService(acTenantService, acUserTenantService, sysConfigCacheService, tokenService);

        Mockito.when(acTenantService.listValid()).thenReturn(Arrays.asList(t1, t2));
        Mockito.when(acTenantService.getById(1L)).thenReturn(t1);
        Mockito.when(acTenantService.getById(2L)).thenReturn(t2);
        Mockito.when(acTenantService.getById(3L)).thenReturn(t3Disabled);
        Mockito.when(sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID)).thenReturn(ADMIN_ROLE_ID);

        AccountInfo account = new AccountInfo();
        account.setUserId(USER_ID);
        account.setRoleInfoMap(new HashMap<>(0));
        AccountContext.set(account);
    }

    @After
    public void tearDown() {
        AccountContext.remove();
        TenantContext.remove();
    }

    // ==================== 清单：成员模式 ====================

    @Test
    public void memberListAlignedAndFlagged() {
        TenantContext.set(Arrays.asList(1L, 2L));
        TenantContext.setCurrentTenantId(1L);
        Mockito.when(acUserTenantService.listByUserId(USER_ID))
                .thenReturn(Arrays.asList(relation(1L, false), relation(2L, true)));

        List<TenantMyVO> rows = service.listMy();

        Assert.assertEquals(2, rows.size());
        TenantMyVO first = rows.get(0);
        Assert.assertEquals(Long.valueOf(1L), first.getTenantId());
        Assert.assertEquals("设计院A", first.getName());
        Assert.assertEquals(Boolean.FALSE, first.getIsAdmin());
        Assert.assertEquals(Boolean.TRUE, first.getCurrent());
        Assert.assertEquals(Boolean.TRUE, rows.get(1).getIsAdmin());
        Assert.assertEquals(Boolean.FALSE, rows.get(1).getCurrent());
    }

    @Test
    public void memberListSkipsDisabledTenantRelations() {
        TenantContext.set(Collections.singletonList(3L));
        Mockito.when(acUserTenantService.listByUserId(USER_ID))
                .thenReturn(Collections.singletonList(relation(3L, false)));

        List<TenantMyVO> rows = service.listMy();

        Assert.assertTrue("指向停用租户的关系行不得出现", rows.isEmpty());
    }

    // ==================== 清单：提权档全量 ====================

    @Test
    public void systemLevelListFullTenants() {
        // 不设 TenantContext（烤集为 null＝系统管理级旁路档）
        Mockito.when(acUserTenantService.listByUserId(USER_ID)).thenReturn(Collections.emptyList());

        List<TenantMyVO> rows = service.listMy();

        Assert.assertEquals("系统管理级必须看到全部有效租户", 2, rows.size());
        Assert.assertEquals(Boolean.FALSE, rows.get(0).getIsAdmin());
        Mockito.verify(acUserTenantService).listByUserId(USER_ID);
    }

    @Test
    public void platformTierListFullTenantsAndFlagsMemberAdmin() {
        TenantContext.set(Collections.singletonList(1L));
        TenantContext.setCurrentTenantId(1L);
        grantAdminRole();
        Mockito.when(acUserTenantService.listByUserId(USER_ID))
                .thenReturn(Collections.singletonList(relation(1L, true)));

        List<TenantMyVO> rows = service.listMy();

        Assert.assertEquals("平台档必须看到全部有效租户（含非成员租户）", 2, rows.size());
        Assert.assertEquals(Boolean.TRUE, rows.get(0).getIsAdmin());
        Assert.assertEquals(Boolean.FALSE, rows.get(1).getIsAdmin());
        Assert.assertEquals(Boolean.TRUE, rows.get(0).getCurrent());
    }

    // ==================== 切换 ====================

    @Test
    public void memberSwitchRewritesOnlyTenantId() {
        TenantContext.set(Arrays.asList(1L, 2L));
        Mockito.when(acUserTenantService.getRelation(USER_ID, 2L)).thenReturn(relation(2L, false));

        service.switchTenant(2L);

        Mockito.verify(tokenService).putItem(TokenItem.TENANT_ID.name(), 2L);
        Mockito.verify(tokenService, Mockito.never()).putItem(Mockito.eq(TokenItem.TENANT_IDS.name()), Mockito.any());
        Assert.assertEquals(Long.valueOf(2L), TenantContext.currentTenantId());
    }

    @Test
    public void platformSwitchNonMemberTenantRewritesBothKeys() {
        TenantContext.set(Collections.singletonList(1L));
        grantAdminRole();
        // 非成员：getRelation 无行

        service.switchTenant(2L);

        Mockito.verify(tokenService).putItem(TokenItem.TENANT_ID.name(), 2L);
        Mockito.verify(tokenService).putItem(TokenItem.TENANT_IDS.name(), Collections.singletonList(2L));
        Assert.assertEquals(Long.valueOf(2L), TenantContext.currentTenantId());
    }

    @Test
    public void systemLevelSwitchRewritesBothKeys() {
        // 不设 TenantContext＝系统管理级
        service.switchTenant(1L);

        Mockito.verify(tokenService).putItem(TokenItem.TENANT_ID.name(), 1L);
        Mockito.verify(tokenService).putItem(TokenItem.TENANT_IDS.name(), Collections.singletonList(1L));
    }

    @Test
    public void switchRejectsNonMemberSilently() {
        TenantContext.set(Arrays.asList(1L, 2L));
        Mockito.when(acUserTenantService.getRelation(USER_ID, 2L)).thenReturn(null);

        try {
            service.switchTenant(2L);
            Assert.fail("非成员切换必须业务拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_MEMBER, e.getErrCode());
        }
        Mockito.verify(tokenService, Mockito.never()).putItem(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void switchRejectsDisabledTenant() {
        TenantContext.set(Collections.singletonList(1L));
        grantAdminRole();

        try {
            service.switchTenant(3L);
            Assert.fail("停用租户必须拒绝（提权档也不例外）");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_AVAILABLE, e.getErrCode());
        }
        Mockito.verify(tokenService, Mockito.never()).putItem(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void switchRejectsNullParam() {
        try {
            service.switchTenant(null);
            Assert.fail("租户 id 空参必须拒绝");
        } catch (ServiceException ignored) {
            // 文案口径＝参数格式
        }
        Mockito.verify(tokenService, Mockito.never()).putItem(Mockito.anyString(), Mockito.any());
    }

    // ==================== 夹具 ====================

    private void grantAdminRole() {
        RoleInfo adminRole = new RoleInfo();
        adminRole.setRoleId(ADMIN_ROLE_ID);
        adminRole.setRoleName("系统管理员");
        Map<Long, RoleInfo> roleInfoMap = new HashMap<>(1);
        roleInfoMap.put(ADMIN_ROLE_ID, adminRole);
        AccountContext.get().setRoleInfoMap(roleInfoMap);
    }

    private static AcUserTenant relation(Long tenantId, boolean isAdmin) {
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(USER_ID);
        relation.setTenantId(tenantId);
        relation.setIsAdmin(isAdmin);
        return relation;
    }

    private static AcTenant tenant(Long id, String name, String code, String valid) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName(name);
        tenant.setCode(code);
        tenant.setValid(valid);
        return tenant;
    }
}
