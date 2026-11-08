package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.mapper.AcSubsystemMapper;
import com.bidr.kernel.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Title: AcSubsystemServiceTest
 * Description: 子系统注册表仓储离线腿（方案B 棒2）——不起 Spring、不连库，mock 伙伴仓储。
 * 覆盖三面：①保存校验（partner 不存在拒 / 存在过 / 未绑定 null 放行）；②删除守卫
 * （有菜单拒无菜单过＋守卫 wrapper 真针）；③getByPartnerId（null 安全＋wrapper 真针）。
 * <p>离线形态照 {@link AcSsoRedirectServiceTest}：写腿用 spy 桩掉需 baseMapper 的 DB 落点
 * （insert 腿桩 {@code getBaseMapper()}、delete 腿桩 {@code deleteById}——离线无
 * SqlSessionFactory，真调 NPE 是边界不是坏），判据腿直接断言包可见 wrapper 构造器的
 * SQL 形态——lambda 列名解析需静态块先装 TableInfo 缓存。</p>
 * DB 真值（uk_partner_id 撞键、FK 兜底缺失行为）不属本类，属启动/真机腿。
 *
 * @author Sharp
 * @since 2026-11-06
 */
public class AcSubsystemServiceTest {

    private static final long PARTNER_ID = 9L;
    private static final String APP_CODE = "app-1";

    static {
        // 离线建 TableInfo 缓存：getSqlSegment() 解析 lambda 列名要用它（AcSsoRedirectServiceTest 同款）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcSubsystem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcMenu.class);
    }

    private AcSubsystemService service(AcPartnerService partnerService, AcMenuService menuService) {
        return new AcSubsystemService(partnerService, menuService);
    }

    private AcSubsystem entityWithPartner(Long partnerId) {
        AcSubsystem entity = new AcSubsystem();
        entity.setAppCode(APP_CODE);
        entity.setName("示例子系统");
        entity.setPartnerId(partnerId);
        return entity;
    }

    // ==================== 保存校验：partner_id 非空时对应 ac_partner 必须存在 ====================

    /** partner 不存在 ⇒ 拒（AC_PARTNER_NOT_EXISTED），且不触写库。 */
    @Test
    public void insertRejectsWhenPartnerMissing() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        Mockito.when(partnerService.existedById(PARTNER_ID)).thenReturn(false);
        AcSubsystemService underTest = service(partnerService, Mockito.mock(AcMenuService.class));

        try {
            underTest.insert(entityWithPartner(PARTNER_ID));
            Assert.fail("partner 不存在必须拒存");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PARTNER_NOT_EXISTED, e.getErrCode());
        }
    }

    /** partner 存在 ⇒ 放行到写库（真实 insert 体跑 validatePartnerBound；baseMapper 落桩，离线无 SqlSessionFactory）。 */
    @Test
    public void insertPassesWhenPartnerExists() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        Mockito.when(partnerService.existedById(PARTNER_ID)).thenReturn(true);
        AcSubsystemService spy = Mockito.spy(service(partnerService, Mockito.mock(AcMenuService.class)));
        stubInsertMapper(spy);

        Assert.assertTrue(spy.insert(entityWithPartner(PARTNER_ID)));
        Mockito.verify(partnerService).existedById(PARTNER_ID);
    }

    /** partner_id=null（未绑定）⇒ 真实 insert 体校验直通，不查 ac_partner（uk 多 NULL 合法）。 */
    @Test
    public void insertAllowsUnboundNullPartnerWithoutLookup() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService spy = Mockito.spy(service(partnerService, Mockito.mock(AcMenuService.class)));
        stubInsertMapper(spy);

        Assert.assertTrue(spy.insert(entityWithPartner(null)));
        Mockito.verifyNoInteractions(partnerService);
    }

    // ==================== 删除守卫：ac_menu 有 app_code 命中则拒 ====================

    /** 有菜单引用 ⇒ 拒（AC_SUBSYSTEM_HAS_MENU），且不触删行。 */
    @Test
    public void deleteGuardRejectsWhenMenuReferencesAppCode() {
        AcMenuService menuService = Mockito.mock(AcMenuService.class);
        stubMenuWrapper(menuService);
        Mockito.when(menuService.existed(Mockito.<Wrapper<AcMenu>>any())).thenReturn(true);
        AcSubsystemService spy = Mockito.spy(service(Mockito.mock(AcPartnerService.class), menuService));
        Mockito.doReturn(true).when(spy).deleteById(APP_CODE);

        try {
            spy.deleteWithGuard(APP_CODE);
            Assert.fail("菜单被引用必须拒删");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_SUBSYSTEM_HAS_MENU, e.getErrCode());
        }
        Mockito.verify(spy, Mockito.never()).deleteById(APP_CODE);
    }

    /** 无菜单引用 ⇒ 守卫过、删行放行。 */
    @Test
    public void deleteGuardPassesWhenNoMenuReferences() {
        AcMenuService menuService = Mockito.mock(AcMenuService.class);
        stubMenuWrapper(menuService);
        Mockito.when(menuService.existed(Mockito.<Wrapper<AcMenu>>any())).thenReturn(false);
        AcSubsystemService spy = Mockito.spy(service(Mockito.mock(AcPartnerService.class), menuService));
        Mockito.doReturn(true).when(spy).deleteById(APP_CODE);

        Assert.assertTrue(spy.deleteWithGuard(APP_CODE));
        Mockito.verify(spy).deleteById(APP_CODE);
    }

    /** app_code 空 ⇒ 拒（不触库不触守卫）。 */
    @Test
    public void deleteGuardRejectsBlankAppCodeWithoutTouchingRepo() {
        AcMenuService menuService = Mockito.mock(AcMenuService.class);
        AcSubsystemService underTest = service(Mockito.mock(AcPartnerService.class), menuService);

        Assert.assertFalse(underTest.deleteWithGuard(""));
        Mockito.verifyNoInteractions(menuService);
    }

    /**
     * 守卫真针：wrapper 必须按 {@code ac_menu.app_code} 等值过滤——删掉生产里那条
     * {@code eq(getAppCode, ...)} 会让守卫退化成「ac_menu 非空即拒删」，本腿翻红。
     */
    @Test
    public void deleteGuardWrapperFiltersByAppCodeEquality() {
        LambdaQueryWrapper<AcMenu> wrapper = service(Mockito.mock(AcPartnerService.class), new AcMenuService())
                .menuAppCodeWrapper(APP_CODE);
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("守卫条件必须含 app_code: " + sql, sql.contains("app_code"));
        Assert.assertEquals("守卫条件必须是单条等值: " + sql, 1, countOccurrences(sql, "="));
        Assert.assertTrue("参数表必须带 app_code 原值",
                wrapper.getParamNameValuePairs().containsValue(APP_CODE));
    }

    // ==================== getByPartnerId：null 安全＋wrapper 真针 ====================

    /** partnerId=null ⇒ 返回 null 不抛、零仓储交互（null 安全）。 */
    @Test
    public void getByPartnerIdIsNullSafe() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcMenuService menuService = Mockito.mock(AcMenuService.class);

        Assert.assertNull(service(partnerService, menuService).getByPartnerId(null));
        Mockito.verifyNoInteractions(partnerService, menuService);
    }

    /** 真针：partner_id 单条等值（渠道删除守卫的判据只此一处）。 */
    @Test
    public void getByPartnerIdWrapperCarriesSingleEquality() {
        LambdaQueryWrapper<AcSubsystem> wrapper = service(Mockito.mock(AcPartnerService.class),
                Mockito.mock(AcMenuService.class)).partnerIdWrapper(PARTNER_ID);
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("查询条件必须含 partner_id: " + sql, sql.contains("partner_id"));
        Assert.assertEquals("查询条件必须是单条等值: " + sql, 1, countOccurrences(sql, "="));
        Assert.assertTrue("参数表必须带 partner_id 值",
                wrapper.getParamNameValuePairs().containsValue(PARTNER_ID));
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = text.indexOf(token);
        while (idx != -1) {
            count++;
            idx = text.indexOf(token, idx + token.length());
        }
        return count;
    }

    /**
     * 写腿（S-2）：真实 insert 体要跑 validatePartnerBound，DB 端只可桩在 mapper——
     * save 由 {@code BaseSqlRepo.insert} 经 {@code super.save}（invokespecial，Mockito 子类
     * mock maker 拦不到，桩 save 打不中已实证 NPE）调到；而 {@code ServiceImpl.save} 里
     * {@code getBaseMapper()} 是虚调用可桩——回 mock AcSubsystemMapper（insert 回 1 ⇒
     * retBool=true），校验照跑、零落库。桩点必须落具体 mapper 类型：泛型擦除在
     * {@code getBaseMapper()} 调用点插 checkcast AcSubsystemMapper，裸 MyBaseMapper mock 过不了。
     */
    private static void stubInsertMapper(AcSubsystemService spy) {
        AcSubsystemMapper mapper = Mockito.mock(AcSubsystemMapper.class);
        Mockito.when(mapper.insert(Mockito.any(AcSubsystem.class))).thenReturn(1);
        Mockito.doReturn(mapper).when(spy).getBaseMapper();
    }

    /**
     * 守卫腿的菜单 mock 补桩：{@code menuAppCodeWrapper} 经 {@code acMenuService.getQueryWrapper()}
     * 构造 wrapper，裸 mock 默认返回 null ⇒ NPE——补一个真 wrapper 构造（离线可用，
     * TableInfo 已在静态块装缓存）， {@code existed} 判定仍由 mock 控制。
     */
    private static void stubMenuWrapper(AcMenuService menuService) {
        Mockito.when(menuService.getQueryWrapper())
                .thenAnswer(invocation -> Wrappers.lambdaQuery(AcMenu.class));
    }
}
