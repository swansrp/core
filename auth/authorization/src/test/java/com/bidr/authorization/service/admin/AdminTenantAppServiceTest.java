package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.dao.repository.AcTenantAppService;
import com.bidr.authorization.vo.tenantapp.BindTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppRes;
import com.bidr.authorization.vo.tenantapp.UnbindTenantAppReq;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Date;

/**
 * Title: AdminTenantAppServiceTest
 * Description: 租户应用开通 admin 面服务离线腿（方案B 棒1，照 AdminPartnerServiceTest 形态：
 * mock 仓储、不起 Spring）。覆盖：query 行带出 appName（两步查非 SQL join）、
 * 空页不触子系统查询、bind/unbind 透传守卫到仓储（守卫与幂等逻辑本体在
 * {@code AcTenantAppServiceTest}，本类只验编排薄层不吞参数）。
 *
 * @author Sharp
 * @since 2026-11-07
 */
public class AdminTenantAppServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final String APP_CODE = "app-1";
    private static final Date EXPIRE_AT = new Date(1760000000000L);

    static {
        // fillAppName 在 mock 的 acSubsystemService 上补真 wrapper（lambda 列名解析需 TableInfo 缓存）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcSubsystem.class);
    }

    private AdminTenantAppService service(AcTenantAppService tenantAppService, AcSubsystemService subsystemService) {
        return new AdminTenantAppService(tenantAppService, subsystemService);
    }

    /** query 顺风腿：开通行经 app_code 两步查带出子系统显示名（appName）。 */
    @Test
    public void queryFillsAppNameFromSubsystem() {
        AcTenantAppService tenantAppService = Mockito.mock(AcTenantAppService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);

        AcTenantApp row = new AcTenantApp();
        row.setTenantId(TENANT_ID);
        row.setAppCode(APP_CODE);
        row.setStatus(AcTenantAppService.STATUS_ENABLED);
        Page<AcTenantApp> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(row));
        Mockito.when(tenantAppService.queryTenantApp(Mockito.any(QueryTenantAppReq.class))).thenReturn(page);
        Mockito.when(subsystemService.getQueryWrapper())
                .thenAnswer(invocation -> Wrappers.lambdaQuery(AcSubsystem.class));
        AcSubsystem subsystem = new AcSubsystem();
        subsystem.setAppCode(APP_CODE);
        subsystem.setName("示例子系统");
        Mockito.when(subsystemService.select(Mockito.<Wrapper<AcSubsystem>>any()))
                .thenReturn(Collections.singletonList(subsystem));

        QueryTenantAppReq req = new QueryTenantAppReq();
        req.setTenantId(TENANT_ID);
        Page<QueryTenantAppRes> res = service(tenantAppService, subsystemService).query(req);

        Assert.assertEquals(1, res.getRecords().size());
        QueryTenantAppRes first = res.getRecords().get(0);
        Assert.assertEquals("实体列平铺不丢", APP_CODE, first.getAppCode());
        Assert.assertEquals("实体列平铺不丢", TENANT_ID, first.getTenantId());
        Assert.assertEquals("appName 必须带出 ac_subsystem.name", "示例子系统", first.getAppName());
    }

    /** 空页腿：无开通行 ⇒ 不触子系统查询（fillAppName 早退）。 */
    @Test
    public void querySkipsSubsystemLookupWhenNoRows() {
        AcTenantAppService tenantAppService = Mockito.mock(AcTenantAppService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Page<AcTenantApp> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        Mockito.when(tenantAppService.queryTenantApp(Mockito.any(QueryTenantAppReq.class))).thenReturn(page);

        Page<QueryTenantAppRes> res = service(tenantAppService, subsystemService).query(new QueryTenantAppReq());

        Assert.assertTrue(res.getRecords().isEmpty());
        Mockito.verifyNoInteractions(subsystemService);
    }

    /** bind 透传腿：请求三字段（tenantId/appCode/expireAt 含 null=长期）原样进仓储守卫口。 */
    @Test
    public void bindPassesThroughToRepoGuard() {
        AcTenantAppService tenantAppService = Mockito.mock(AcTenantAppService.class);
        AdminTenantAppService underTest = service(tenantAppService, Mockito.mock(AcSubsystemService.class));

        BindTenantAppReq req = new BindTenantAppReq();
        req.setTenantId(TENANT_ID);
        req.setAppCode(APP_CODE);
        req.setExpireAt(EXPIRE_AT);
        underTest.bind(req);

        Mockito.verify(tenantAppService).bind(TENANT_ID, APP_CODE, EXPIRE_AT);
    }

    /** unbind 透传腿：两字段原样进仓储软删口。 */
    @Test
    public void unbindPassesThroughToRepo() {
        AcTenantAppService tenantAppService = Mockito.mock(AcTenantAppService.class);
        AdminTenantAppService underTest = service(tenantAppService, Mockito.mock(AcSubsystemService.class));

        UnbindTenantAppReq req = new UnbindTenantAppReq();
        req.setTenantId(TENANT_ID);
        req.setAppCode(APP_CODE);
        underTest.unbind(req);

        Mockito.verify(tenantAppService).unbind(TENANT_ID, APP_CODE);
    }
}
