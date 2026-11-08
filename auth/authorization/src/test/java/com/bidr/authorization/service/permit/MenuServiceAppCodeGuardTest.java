package com.bidr.authorization.service.permit;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcDeptMenuService;
import com.bidr.authorization.dao.repository.AcGroupMenuService;
import com.bidr.authorization.dao.repository.AcUserMenuService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: MenuServiceAppCodeGuardTest
 * Description: 门户菜单 app_code 防漏闸离线单测（方案B 棒3 §4.1，纯 JUnit4＋Mockito）——
 * 门户 {@code /web/menu/**} 全部查询缺省只出平台自身（app_code IS NULL），子系统菜单
 * （'epc-ai' 等）一行都不许漏进门户树/列表。mock 四路（role/dept/group/user）返回
 * NULL 与非 NULL 混合行，断言收窄在聚合前生效（四路任何一路挂的子系统菜单都进不了门户）。
 * 开放口收窄腿（appCode='epc-ai'）同场验证：只留等值行、别 app 行不残留。
 * <p>user 不存在腿沿用 getMenuTree 既有业务拒绝（同口径回归）。</p>
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class MenuServiceAppCodeGuardTest {

    private static final String CUSTOMER = "000001";
    private static final String APP = "epc-ai";

    private AcUserRoleMenuService acUserRoleMenuService;
    private AcDeptMenuService acDeptMenuService;
    private AcGroupMenuService acGroupMenuService;
    private AcUserMenuService acUserMenuService;
    private AcUserService acUserService;
    private MenuService menuService;

    @Before
    public void setUp() {
        acUserRoleMenuService = Mockito.mock(AcUserRoleMenuService.class);
        acDeptMenuService = Mockito.mock(AcDeptMenuService.class);
        acGroupMenuService = Mockito.mock(AcGroupMenuService.class);
        acUserMenuService = Mockito.mock(AcUserMenuService.class);
        acUserService = Mockito.mock(AcUserService.class);
        menuService = new MenuService(acUserRoleMenuService, acDeptMenuService, acGroupMenuService,
                acUserMenuService, acUserService);

        AcUser user = new AcUser();
        user.setUserId(7L);
        user.setCustomerNumber(CUSTOMER);
        Mockito.when(acUserService.getByCustomerNumber(CUSTOMER)).thenReturn(user);

        AccountInfo info = new AccountInfo();
        info.setUserId(7L);
        info.setCustomerNumber(CUSTOMER);
        AccountContext.set(info);
    }

    @After
    public void tearDown() {
        AccountContext.remove();
    }

    private static AcMenu menu(long id, Long pid, Long grandId, String appCode) {
        AcMenu m = new AcMenu();
        m.setMenuId(id);
        m.setPid(pid);
        m.setGrandId(grandId);
        m.setAppCode(appCode);
        m.setShowOrder((int) id);
        return m;
    }

    /** 四路各挂不同归属的菜单：role=平台1+子系统根2/子3、dept=平台4、group=别app 5、user=平台6 */
    private void stubFourPathsMixed() {
        Mockito.when(acUserRoleMenuService.getAllMenu(Mockito.eq(CUSTOMER), Mockito.any()))
                .thenReturn(Arrays.asList(menu(1, null, null, null), menu(2, null, null, APP), menu(3, 2L, null, APP)));
        Mockito.when(acDeptMenuService.getAllMenu(Mockito.eq(7L), Mockito.any()))
                .thenReturn(Arrays.asList(menu(4, null, null, null)));
        Mockito.when(acGroupMenuService.getAllMenu(Mockito.eq(7L), Mockito.any()))
                .thenReturn(Arrays.asList(menu(5, null, null, "other-app")));
        Mockito.when(acUserMenuService.getAllMenu(Mockito.eq(CUSTOMER), Mockito.any()))
                .thenReturn(Arrays.asList(menu(6, null, null, null)));
    }

    private static List<MenuTreeRes> flatten(List<MenuTreeRes> tree) {
        List<MenuTreeRes> all = new ArrayList<>();
        collect(tree, all);
        return all;
    }

    private static void collect(List<MenuTreeRes> nodes, List<MenuTreeRes> sink) {
        if (nodes == null) {
            return;
        }
        for (MenuTreeRes node : nodes) {
            sink.add(node);
            collect(node.getChildren(), sink);
        }
    }

    // ==================== 守卫闸（§4.1 防漏）：门户缺省不含任何非 NULL app_code 行 ====================

    /** 门户权限树缺省（getMenuTree(customerNumber)）＝只出平台自身，四路挂的子系统行全被收窄掉。 */
    @Test
    public void portalPermitTreeDefaultExcludesSubsystemMenus() {
        stubFourPathsMixed();
        List<MenuTreeRes> tree = menuService.getMenuTree(CUSTOMER);
        List<MenuTreeRes> flat = flatten(tree);
        Assert.assertEquals("缺省只出平台自身三行（role/dept/user 三路各一）", 3, flat.size());
        for (MenuTreeRes node : flat) {
            Assert.assertNull("门户树不得出现子系统菜单行（menuId=" + node.getMenuId() + "）",
                    node.getAppCode());
        }
    }

    /** 门户 /list（getMenuList，仅 role 路）缺省同样只出平台行——空集时维持既有「没有配置权限」拒绝。 */
    @Test
    public void portalPermitListDefaultExcludesSubsystemMenus() {
        Mockito.when(acUserRoleMenuService.getAllMenu(Mockito.eq(CUSTOMER), Mockito.any()))
                .thenReturn(Arrays.asList(menu(1, null, null, null), menu(2, 1L, null, APP)));
        List<?> list = menuService.getMenuList();
        Assert.assertEquals("门户列表缺省只出平台一行", 1, list.size());
    }

    /** 门户 /main/tree、/sub/tree、/content/tree 缺省腿逐一收窄（role 路单源）。 */
    @Test
    public void portalMainSubContentTreesDefaultExcludesSubsystemMenus() {
        Mockito.when(acUserRoleMenuService.getMainMenu(Mockito.eq(CUSTOMER), Mockito.any()))
                .thenReturn(Arrays.asList(menu(1, null, null, null), menu(2, null, null, APP)));
        Mockito.when(acUserRoleMenuService.getSubMenu(Mockito.eq(CUSTOMER), Mockito.any(), Mockito.eq(1L)))
                .thenReturn(Arrays.asList(menu(3, null, null, null), menu(4, null, null, APP)));
        Mockito.when(acUserRoleMenuService.getContent(Mockito.eq(CUSTOMER), Mockito.any(), Mockito.eq(3L)))
                .thenReturn(Arrays.asList(menu(5, null, null, null), menu(6, null, null, APP)));

        Assert.assertEquals("main/tree 缺省只出平台行", 1, flatten(menuService.getMainMenuTree()).size());
        com.bidr.authorization.vo.menu.MenuTreeReq req = new com.bidr.authorization.vo.menu.MenuTreeReq();
        req.setMenuId(1L);
        Assert.assertEquals("sub/tree 缺省只出平台行", 1, flatten(menuService.getSubMenuTree(req)).size());
        req.setMenuId(3L);
        Assert.assertEquals("content/tree 缺省只出平台行", 1, flatten(menuService.getContentTree(req)).size());
    }

    // ==================== 开放口收窄腿：appCode='epc-ai' 只留等值行 ====================

    /** appCode 收窄版：只出该子系统行，平台行与别 app 行都不残留。 */
    @Test
    public void openApiNarrowsToRequestedAppOnly() {
        stubFourPathsMixed();
        List<MenuTreeRes> tree = menuService.getMenuTree(CUSTOMER, APP);
        List<MenuTreeRes> flat = flatten(tree);
        Assert.assertEquals("收窄后只剩该 app 两行", 2, flat.size());
        for (MenuTreeRes node : flat) {
            Assert.assertEquals("收窄后只许等值行", APP, node.getAppCode());
        }
    }

    // ==================== user 不存在：空菜单兜底（erp 线 11-03 裁定，2026-11-08 合并裁决） ====================

    /**
     * 用户不存在 ⇒ 空菜单而非异常，且不触四路查询。
     * <p>与 origin 框架仓「守卫 A＝沿用既有业务拒绝」的分歧裁决：erp 线数据闸对「登录态但无租户绑定」
     * 的用户恒假（ac_user 查不到），该态是无权限不是系统错误，抛异常＝合法登录态吃 500（2026-11-03
     * 用户裁定，11-05 合并已确认保留防再撞）。单测层「真不存在」与「被闸遮蔽」同形（getByCustomerNumber
     * 均返 null），服务只取一种行为——按较新裁定走空集合；上游化时随 core push 拍板再与守卫 A 对齐。</p>
     */
    @Test
    public void unknownUserGetsEmptyMenuWithoutQueries() {
        List<MenuTreeRes> tree = menuService.getMenuTree("nobody", APP);
        Assert.assertTrue("用户不存在必须空菜单而非异常", tree == null || tree.isEmpty());
        Mockito.verifyNoInteractions(acUserRoleMenuService, acDeptMenuService, acGroupMenuService,
                acUserMenuService);
    }
}
