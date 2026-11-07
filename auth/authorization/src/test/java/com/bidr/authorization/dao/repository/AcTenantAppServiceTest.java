package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.authorization.dao.mapper.AcTenantAppMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Title: AcTenantAppServiceTest
 * Description: 租户级应用开通仓储离线腿（方案B 棒1）——不起 Spring、不连库，mock 依赖仓储。
 * 覆盖四面：①绑定守卫（app 未注册拒/租户不存在拒）；②bind 幂等（无行新插/撞 UK 含软删行复活更新
 * 而非新插）；③unbind 软删（wrapper 真针：按逻辑主键置 valid='0'）；④开通判定谓词
 * {@code activatedFor}（null 安全＋wrapper 真针：四条件＋两时间窗）。
 * <p>离线形态照 {@link AcSubsystemServiceTest}：写腿用 spy 桩掉需 baseMapper 的 DB 落点
 * （{@code getBaseMapper()} 桩 mapper、撞键查询桩 {@code getByTenantApp}——离线无
 * SqlSessionFactory，真调 NPE 是边界不是坏），判据腿直接断言包可见 wrapper 构造器的
 * SQL 形态——lambda 列名解析需静态块先装 TableInfo 缓存。</p>
 * DB 真值（uk_tenant_app 撞键、expire_at 时间窗边界）不属本类，属启动/真机腿。
 *
 * @author Sharp
 * @since 2026-11-07
 */
public class AcTenantAppServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final String APP_CODE = "app-1";
    private static final Date NOW = new Date(1760000000000L);

    static {
        // 离线建 TableInfo 缓存：getSqlSegment()/getSqlSet() 解析 lambda 列名要用它（AcSubsystemServiceTest 同款）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcTenantApp.class);
    }

    private AcTenantAppService service(AcSubsystemService subsystemService, AcTenantService tenantService) {
        return new AcTenantAppService(subsystemService, tenantService);
    }

    /** app 守卫放行的 mock（租户守卫另配）。 */
    private AcSubsystemService passSubsystem() {
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(subsystemService.existedById(APP_CODE)).thenReturn(true);
        return subsystemService;
    }

    /** 租户守卫放行的 mock。 */
    private AcTenantService passTenant() {
        AcTenantService tenantService = Mockito.mock(AcTenantService.class);
        Mockito.when(tenantService.existedById(TENANT_ID)).thenReturn(true);
        return tenantService;
    }

    // ==================== 绑定守卫：app 未注册拒 / 租户不存在拒 ====================

    /** app_code 未注册于 ac_subsystem ⇒ 拒（AC_APP_NOT_EXISTED），且守卫顺序在前、不触租户校验。 */
    @Test
    public void bindRejectsWhenAppNotRegistered() {
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class); // existedById 默认 false
        AcTenantService tenantService = Mockito.mock(AcTenantService.class);
        AcTenantAppService underTest = service(subsystemService, tenantService);

        try {
            underTest.bind(TENANT_ID, APP_CODE, null);
            Assert.fail("app 未注册必须拒绑");
        } catch (ServiceException e) {
            Assert.assertEquals(AcTenantAppService.AcTenantAppErr.AC_APP_NOT_EXISTED, e.getErrCode());
        }
        Mockito.verifyNoInteractions(tenantService);
    }

    /** tenant_id 不存在于 ac_tenant ⇒ 拒（AC_TENANT_NOT_AVAILABLE，沿用注册表既有码）。 */
    @Test
    public void bindRejectsWhenTenantMissing() {
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(subsystemService.existedById(APP_CODE)).thenReturn(true);
        AcTenantService tenantService = Mockito.mock(AcTenantService.class); // existedById 默认 false
        AcTenantAppService underTest = service(subsystemService, tenantService);

        try {
            underTest.bind(TENANT_ID, APP_CODE, null);
            Assert.fail("租户不存在必须拒绑");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_AVAILABLE, e.getErrCode());
        }
    }

    // ==================== bind 幂等 upsert：无行新插 / 撞 UK 复活更新 ====================

    /** 无同键行 ⇒ 新插（status=ENABLED、valid='1'、expireAt 透传），不走 update。 */
    @Test
    public void bindInsertsWhenNoRowExists() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        AcTenantAppMapper mapper = stubWriteMapper(spy);
        Mockito.doReturn(null).when(spy).getByTenantApp(TENANT_ID, APP_CODE);

        spy.bind(TENANT_ID, APP_CODE, NOW);

        ArgumentCaptor<AcTenantApp> captor = ArgumentCaptor.forClass(AcTenantApp.class);
        Mockito.verify(mapper).insert(captor.capture());
        AcTenantApp inserted = captor.getValue();
        Assert.assertEquals(TENANT_ID, inserted.getTenantId());
        Assert.assertEquals(APP_CODE, inserted.getAppCode());
        Assert.assertEquals("新插行 status 必须是 ENABLED", AcTenantAppService.STATUS_ENABLED, inserted.getStatus());
        Assert.assertEquals("新插行 valid 必须是 '1'", CommonConst.YES, inserted.getValid());
        Assert.assertEquals("expireAt 必须透传", NOW, inserted.getExpireAt());
        Mockito.verify(mapper, Mockito.never()).update(Mockito.any(), Mockito.any());
    }

    /**
     * 撞 UK（含 valid='0' 软删行）⇒ 复活/更新该行而非新插：按物理主键定位，重写
     * status/expire_at/valid 三列（expire_at 显式重写＝null 覆盖旧到期改长期）。
     */
    @Test
    public void bindResurrectsExistingRowIncludingSoftDeleted() {
        AcTenantApp existing = new AcTenantApp();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setAppCode(APP_CODE);
        existing.setStatus(AcTenantAppService.STATUS_DISABLED);
        existing.setValid(CommonConst.NO); // 软删行也必须复活
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        AcTenantAppMapper mapper = stubWriteMapper(spy);
        Mockito.doReturn(existing).when(spy).getByTenantApp(TENANT_ID, APP_CODE);

        spy.bind(TENANT_ID, APP_CODE, null); // null=改长期：必须显式重写 expire_at 覆盖旧到期

        Mockito.verify(mapper, Mockito.never()).insert(Mockito.any(AcTenantApp.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AcTenantApp>> wrapperCaptor =
                (ArgumentCaptor<Wrapper<AcTenantApp>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
        Mockito.verify(mapper).update(Mockito.any(), wrapperCaptor.capture());
        LambdaUpdateWrapper<AcTenantApp> wrapper = (LambdaUpdateWrapper<AcTenantApp>) (Object) wrapperCaptor.getValue();
        String sqlSet = wrapper.getSqlSet();
        Assert.assertTrue("复活腿必须重写 status: " + sqlSet, sqlSet.contains("status"));
        Assert.assertTrue("复活腿必须重写 expire_at（null 覆盖旧到期＝改长期）: " + sqlSet, sqlSet.contains("expire_at"));
        Assert.assertTrue("复活腿必须复活软删位 valid: " + sqlSet, sqlSet.contains("valid"));
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("复活腿必须按物理主键 id 定位行: " + sql, sql.contains("id ="));
        Assert.assertTrue("status 实参必须是 ENABLED", wrapper.getParamNameValuePairs().containsValue(AcTenantAppService.STATUS_ENABLED));
        Assert.assertTrue("valid 实参必须是 '1'", wrapper.getParamNameValuePairs().containsValue(CommonConst.YES));
    }

    // ==================== unbind：按逻辑主键软删，静默幂等 ====================

    /** unbind wrapper 真针：tenant_id+app_code 等值定位、只置 valid='0'；0 行受影响＝静默幂等不报错。 */
    @Test
    public void unbindSoftDeletesByTenantAppKey() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        AcTenantAppMapper mapper = stubWriteMapper(spy);
        Mockito.when(mapper.update(Mockito.any(), Mockito.any())).thenReturn(0); // 行不存在 ⇒ 0 行受影响

        spy.unbind(TENANT_ID, APP_CODE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AcTenantApp>> wrapperCaptor =
                (ArgumentCaptor<Wrapper<AcTenantApp>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
        Mockito.verify(mapper).update(Mockito.any(), wrapperCaptor.capture());
        LambdaUpdateWrapper<AcTenantApp> wrapper = (LambdaUpdateWrapper<AcTenantApp>) (Object) wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("软删必须按 tenant_id 定位: " + sql, sql.contains("tenant_id"));
        Assert.assertTrue("软删必须按 app_code 定位: " + sql, sql.contains("app_code"));
        Assert.assertTrue("软删只置 valid 一列: " + wrapper.getSqlSet(), wrapper.getSqlSet().contains("valid"));
        Assert.assertTrue("软删位必须是 '0'", wrapper.getParamNameValuePairs().containsValue(CommonConst.NO));
    }

    // ==================== activatedFor：null 安全＋wiring＋谓词真针 ====================

    /** tenantId/appCode 空 ⇒ false（null 安全，不触库不触依赖仓储）。 */
    @Test
    public void activatedForIsNullSafe() {
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        AcTenantService tenantService = Mockito.mock(AcTenantService.class);

        Assert.assertFalse(service(subsystemService, tenantService).activatedFor(null, APP_CODE, NOW));
        Assert.assertFalse(service(subsystemService, tenantService).activatedFor(TENANT_ID, "", NOW));
        Mockito.verifyNoInteractions(subsystemService, tenantService);
    }

    /** wiring 腿：判据命中（existed=true）⇒ true——谓词与 existed 查询口必须接通。 */
    @Test
    public void activatedForReturnsTrueWhenRowMatches() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        // spy 桩法必须 doReturn：when(spy.x()) 会先真调 existed()（offline 无 mapper ⇒ NPE）
        Mockito.doReturn(true).when(spy).existed(Mockito.<Wrapper<AcTenantApp>>any());

        Assert.assertTrue(spy.activatedFor(TENANT_ID, APP_CODE, NOW));
        Mockito.verify(spy).existed(Mockito.<Wrapper<AcTenantApp>>any());
    }

    /**
     * 谓词真针：wrapper 必须钉齐四条件两窗口——tenant_id/app_code/status/valid 等值
     * ＋ (expire_at IS NULL OR expire_at&gt;now) ＋ (start_at IS NULL OR start_at&lt;=now)。
     * 删掉任一条件＝业务仓复用面口径漂移，本腿翻红。
     */
    @Test
    public void activatedForWrapperPinsFullPredicate() {
        LambdaQueryWrapper<AcTenantApp> wrapper =
                service(passSubsystem(), passTenant()).activatedWrapper(TENANT_ID, APP_CODE, NOW);
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("谓词必须含 tenant_id: " + sql, sql.contains("tenant_id"));
        Assert.assertTrue("谓词必须含 app_code: " + sql, sql.contains("app_code"));
        Assert.assertTrue("谓词必须钉 status=ENABLED: " + sql, sql.contains("status"));
        Assert.assertTrue("谓词必须钉软删位 valid: " + sql, sql.contains("valid"));
        Assert.assertTrue("谓词必须含到期窗口 expire_at: " + sql, sql.contains("expire_at"));
        Assert.assertTrue("谓词必须含生效窗口 start_at: " + sql, sql.contains("start_at"));
        Assert.assertTrue("时间窗必须保留 NULL 分支（空=不限）: " + sql, sql.contains("IS NULL"));
        Assert.assertTrue("时间窗必须是 OR 分支: " + sql, sql.contains("OR"));
        Assert.assertTrue("窗口比较必须带 now 实参", wrapper.getParamNameValuePairs().containsValue(NOW));
        Assert.assertTrue("status 实参必须是 ENABLED", wrapper.getParamNameValuePairs().containsValue(AcTenantAppService.STATUS_ENABLED));
        Assert.assertTrue("valid 实参必须是 '1'", wrapper.getParamNameValuePairs().containsValue(CommonConst.YES));
    }

    /**
     * 批量面 wrapper 形态钉（棒2 review I-1）：生产调用点只有两个 list 方法（open-api 可见集/
     * 门户 visible()），时间窗防线不能只钉在 activatedFor 路径——批量方法若漏挂 activatedConds
     * （退回只挂 status+valid 等值的旧形态），本腿翻红。桩点＝{@code select(Wrapper)}（虚调用
     * 可桩，离线不触库），捕获实参断言 SQL 形态。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void listActivatedTenantsWrapperPinsFullPredicate() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        Mockito.doReturn(java.util.Collections.emptyList()).when(spy).select(Mockito.any(Wrapper.class));
        spy.listActivatedTenants(APP_CODE, NOW);
        ArgumentCaptor<Wrapper<AcTenantApp>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        Mockito.verify(spy).select(captor.capture());
        LambdaQueryWrapper<AcTenantApp> wrapper = (LambdaQueryWrapper<AcTenantApp>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("批量腿必须含 app_code 等值: " + sql, sql.contains("app_code"));
        Assert.assertFalse("按应用列租户，不得挂 tenant_id 等值: " + sql, sql.contains("tenant_id"));
        Assert.assertTrue("批量腿必须钉 status=ENABLED: " + sql, sql.contains("status"));
        Assert.assertTrue("批量腿必须钉软删位 valid: " + sql, sql.contains("valid"));
        Assert.assertTrue("批量腿必须含到期窗口 expire_at: " + sql, sql.contains("expire_at"));
        Assert.assertTrue("批量腿必须含生效窗口 start_at: " + sql, sql.contains("start_at"));
        Assert.assertTrue("时间窗必须保留 NULL 分支: " + sql, sql.contains("IS NULL"));
        Assert.assertTrue("时间窗必须是 OR 分支: " + sql, sql.contains("OR"));
        Assert.assertTrue("窗口比较必须带 now 实参", wrapper.getParamNameValuePairs().containsValue(NOW));
        Assert.assertTrue("status 实参必须是 ENABLED", wrapper.getParamNameValuePairs().containsValue(AcTenantAppService.STATUS_ENABLED));
        Assert.assertTrue("valid 实参必须是 '1'", wrapper.getParamNameValuePairs().containsValue(CommonConst.YES));
    }

    /** 批量腿镜像钉：按租户列应用（tenant_id 等值在座、app_code 等值缺席），时间窗四条件同源。 */
    @Test
    @SuppressWarnings("unchecked")
    public void listActivatedAppCodesWrapperPinsFullPredicate() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        Mockito.doReturn(java.util.Collections.emptyList()).when(spy).select(Mockito.any(Wrapper.class));
        spy.listActivatedAppCodes(TENANT_ID, NOW);
        ArgumentCaptor<Wrapper<AcTenantApp>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        Mockito.verify(spy).select(captor.capture());
        LambdaQueryWrapper<AcTenantApp> wrapper = (LambdaQueryWrapper<AcTenantApp>) captor.getValue();
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("批量腿必须含 tenant_id 等值: " + sql, sql.contains("tenant_id"));
        Assert.assertFalse("按租户列应用，不得挂 app_code 等值: " + sql, sql.contains("app_code"));
        Assert.assertTrue("批量腿必须钉 status=ENABLED: " + sql, sql.contains("status"));
        Assert.assertTrue("批量腿必须钉软删位 valid: " + sql, sql.contains("valid"));
        Assert.assertTrue("批量腿必须含到期窗口 expire_at: " + sql, sql.contains("expire_at"));
        Assert.assertTrue("批量腿必须含生效窗口 start_at: " + sql, sql.contains("start_at"));
        Assert.assertTrue("时间窗必须保留 NULL 分支: " + sql, sql.contains("IS NULL"));
        Assert.assertTrue("时间窗必须是 OR 分支: " + sql, sql.contains("OR"));
        Assert.assertTrue("窗口比较必须带 now 实参", wrapper.getParamNameValuePairs().containsValue(NOW));
    }

    /** 批量面 null 早退（不触库）：appCode 空/tenantId null ⇒ 空集且 select 零调用。 */
    @Test
    public void listActivatedBulkNullSafeSkipsDb() {
        AcTenantAppService spy = Mockito.spy(service(passSubsystem(), passTenant()));
        Assert.assertTrue(spy.listActivatedTenants(null, NOW).isEmpty());
        Assert.assertTrue(spy.listActivatedTenants("", NOW).isEmpty());
        Assert.assertTrue(spy.listActivatedAppCodes(null, NOW).isEmpty());
        Mockito.verify(spy, Mockito.never()).select(Mockito.any(Wrapper.class));
    }

    /**
     * 写腿（S-2，照 AcSubsystemServiceTest.stubInsertMapper 纪律）：真实 bind/unbind 体要跑守卫，
     * DB 端只可桩在 mapper——save/update 由 {@code BaseSqlRepo} 经 {@code super}（invokespecial，
     * mock 子类拦不到）调到，而 {@code getBaseMapper()} 是虚调用可桩——回 mock AcTenantAppMapper
     * （insert/update 回 1），校验照跑、零落库。撞键查询 {@code getByTenantApp} 同理桩在 service 面。
     */
    private static AcTenantAppMapper stubWriteMapper(AcTenantAppService spy) {
        AcTenantAppMapper mapper = Mockito.mock(AcTenantAppMapper.class);
        Mockito.when(mapper.insert(Mockito.any(AcTenantApp.class))).thenReturn(1);
        Mockito.when(mapper.update(Mockito.any(), Mockito.any())).thenReturn(1);
        Mockito.doReturn(mapper).when(spy).getBaseMapper();
        return mapper;
    }
}
