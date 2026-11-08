package com.bidr.authorization.controller.openapi;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.MenuService;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

/**
 * Title: MenuTreeControllerTest
 * Description: 菜单开放口解析链离线单测（方案B 棒3 §4.1，纯 JUnit4＋Mockito）——
 * {@code GET /open-api/v1/menu/tree?user=...} 的归属应用解析只认 partner 身份、
 * 不吃请求参数。四腿全覆盖：
 * <ul>
 *     <li>正常腿：PARTNER_ID → ac_subsystem（uk_partner_id）→ app_code，透传收窄取树；</li>
 *     <li>fallback 腿：子系统未注册/未烤 PARTNER_ID ⇒ 过渡期按烤入 APP_KEY 当 app_code；</li>
 *     <li>多条 fail-closed 腿：getByPartnerId 命中多行（MP getOne 抛 MybatisPlusException）⇒ 拒；</li>
 *     <li>解析空拒腿：两路皆空 ⇒ 拒（不得回落平台自身）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class MenuTreeControllerTest {

    private static final long PARTNER_ID = 9L;
    private static final String APP_CODE = "epc-ai";
    private static final String APP_KEY = "epc-partner-key";
    private static final String USER = "000001";

    private AcSubsystemService acSubsystemService;
    private MenuService menuService;
    private MenuTreeController controller;

    @Before
    public void setUp() {
        acSubsystemService = Mockito.mock(AcSubsystemService.class);
        menuService = Mockito.mock(MenuService.class);
        controller = new MenuTreeController(acSubsystemService, menuService);
        AccountContext.set(new AccountInfo());
    }

    @After
    public void tearDown() {
        AccountContext.remove();
    }

    private static void bake(TokenItem item, Object value) {
        AccountContext.addExtraData(item.name(), value);
    }

    private static AcSubsystem subsystem(String appCode) {
        AcSubsystem subsystem = new AcSubsystem();
        subsystem.setAppCode(appCode);
        subsystem.setPartnerId(PARTNER_ID);
        return subsystem;
    }

    /** 正常腿：PARTNER_ID 命中子系统 → app_code 透传收窄取树；PARTNER_ID 字符串形态同判。 */
    @Test
    public void resolvesAppCodeFromPartnerBoundSubsystem() {
        bake(TokenItem.PARTNER_ID, PARTNER_ID);
        Mockito.when(acSubsystemService.getByPartnerId(PARTNER_ID)).thenReturn(subsystem(APP_CODE));
        Mockito.when(menuService.getMenuTree(USER, APP_CODE)).thenReturn(Collections.emptyList());

        List<MenuTreeRes> tree = controller.menuTree(USER);

        Assert.assertTrue(tree.isEmpty());
        Mockito.verify(menuService).getMenuTree(USER, APP_CODE);
        Mockito.verify(acSubsystemService).getByPartnerId(PARTNER_ID);
    }

    /** PARTNER_ID 烤成字符串的容错腿：按数字解析后同走子系统行。 */
    @Test
    public void stringPartnerIdIsTolerated() {
        bake(TokenItem.PARTNER_ID, String.valueOf(PARTNER_ID));
        Mockito.when(acSubsystemService.getByPartnerId(PARTNER_ID)).thenReturn(subsystem(APP_CODE));
        Mockito.when(menuService.getMenuTree(USER, APP_CODE)).thenReturn(Collections.emptyList());

        controller.menuTree(USER);

        Mockito.verify(menuService).getMenuTree(USER, APP_CODE);
    }

    /** fallback 腿①：子系统未注册（getByPartnerId null）⇒ 过渡期按 APP_KEY 当 app_code。 */
    @Test
    public void fallsBackToAppKeyWhenSubsystemMissing() {
        bake(TokenItem.PARTNER_ID, PARTNER_ID);
        bake(TokenItem.APP_KEY, APP_KEY);
        Mockito.when(acSubsystemService.getByPartnerId(PARTNER_ID)).thenReturn(null);
        Mockito.when(menuService.getMenuTree(USER, APP_KEY)).thenReturn(Collections.emptyList());

        controller.menuTree(USER);

        Mockito.verify(menuService).getMenuTree(USER, APP_KEY);
    }

    /** fallback 腿②：未烤 PARTNER_ID（老 AuthPartner 形态）⇒ 不触子系统表，直接 APP_KEY。 */
    @Test
    public void fallsBackToAppKeyWhenPartnerIdAbsent() {
        bake(TokenItem.APP_KEY, APP_KEY);
        Mockito.when(menuService.getMenuTree(USER, APP_KEY)).thenReturn(Collections.emptyList());

        Assert.assertEquals(APP_KEY, controller.resolveAppCode());
        Mockito.verifyNoInteractions(acSubsystemService);
    }

    /** 多条 fail-closed 腿：uk_partner_id 破防（getByPartnerId 多行抛 MybatisPlusException）⇒ 拒，不回落。 */
    @Test
    public void multipleSubsystemRowsFailClosed() {
        bake(TokenItem.PARTNER_ID, PARTNER_ID);
        bake(TokenItem.APP_KEY, APP_KEY);
        Mockito.when(acSubsystemService.getByPartnerId(PARTNER_ID))
                .thenThrow(new MybatisPlusException("One record is expected, but the query result is multiple records"));

        try {
            controller.menuTree(USER);
            Assert.fail("多条绑定必须 fail-closed 拒");
        } catch (ServiceException e) {
            Assert.assertTrue("拒绝文案应直陈多条绑定：" + e.getMessage(),
                    e.getMessage().contains("绑定多条子系统"));
        }
        Mockito.verifyNoInteractions(menuService);
    }

    /** 解析空拒腿：子系统与 APP_KEY 两路皆空 ⇒ 拒（不得回落平台自身，不吃请求参数）。 */
    @Test
    public void emptyResolutionIsRejectedFailClosed() {
        try {
            controller.menuTree(USER);
            Assert.fail("解析不出归属应用必须拒");
        } catch (ServiceException e) {
            Assert.assertTrue("拒绝文案应直陈无法解析归属应用：" + e.getMessage(),
                    e.getMessage().contains("无法解析调用方归属应用"));
        }
        Mockito.verifyNoInteractions(menuService);
    }

    /** 解析优先级：子系统命中时 APP_KEY 在场也不抢道（fallback 只补空）。 */
    @Test
    public void subsystemHitTakesPriorityOverAppKey() {
        bake(TokenItem.PARTNER_ID, PARTNER_ID);
        bake(TokenItem.APP_KEY, APP_KEY);
        Mockito.when(acSubsystemService.getByPartnerId(PARTNER_ID)).thenReturn(subsystem(APP_CODE));

        Assert.assertEquals(APP_CODE, controller.resolveAppCode());
    }
}
