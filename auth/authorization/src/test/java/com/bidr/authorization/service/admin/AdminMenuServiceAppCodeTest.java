package com.bidr.authorization.service.admin;

import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleMenuService;
import com.bidr.authorization.service.permit.PermitService;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Title: AdminMenuServiceAppCodeTest
 * Description: 管理面菜单 appCode 过滤与新增落列离线单测（方案B 棒3 §4.4，纯 JUnit4＋Mockito，
 * @Resource 字段反射注入）——
 * <ul>
 *     <li>过滤腿：tree/main/sub 查询把可选 appCode 透传到查询正源（'epc-ai' 等值/缺省 null）；</li>
 *     <li>树装配腿：收窄后的行照旧做 pid 空补 grandId 再建树（既有行为不变）；</li>
 *     <li>add 落列腿：请求体携带 appCode 原样落库；未携带 ⇒ 继承父行 app_code
 *         （顶部菜单无父行 ⇒ 平台自身 NULL）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class AdminMenuServiceAppCodeTest {

    private static final String APP = "epc-ai";

    private AcMenuService acMenuService;
    private AcRoleMenuService acRoleMenuService;
    private SysConfigCacheService sysConfigCacheService;
    private PermitService permitService;
    private AdminMenuService adminMenuService;

    @Before
    public void setUp() throws Exception {
        acMenuService = Mockito.mock(AcMenuService.class);
        acRoleMenuService = Mockito.mock(AcRoleMenuService.class);
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        permitService = Mockito.mock(PermitService.class);
        adminMenuService = new AdminMenuService();
        inject("acMenuService", acMenuService);
        inject("acRoleMenuService", acRoleMenuService);
        inject("sysConfigCacheService", sysConfigCacheService);
        inject("permitService", permitService);
        Mockito.when(permitService.isAdmin()).thenReturn(true);
    }

    private void inject(String name, Object value) throws Exception {
        Field field = AdminMenuService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adminMenuService, value);
    }

    private static AcMenu menu(long id, Long pid, Long grandId, String appCode) {
        AcMenu m = new AcMenu();
        m.setMenuId(id);
        m.setPid(pid);
        m.setGrandId(grandId);
        m.setAppCode(appCode);
        m.setShowOrder((int) id);
        m.setTitle("菜单" + id);
        return m;
    }

    // ==================== 过滤腿：appCode 透传到查询正源 ====================

    /** tree 查询：appCode='epc-ai' 只把该 app 行交给建树；缺省传 null＝平台自身。 */
    @Test
    public void menuTreePassesAppCodeDownAndBuildsNarrowedTree() {
        Mockito.when(acMenuService.getAllMenu(true, APP)).thenReturn(Arrays.asList(
                menu(2, null, null, APP), menu(3, 2L, null, APP)));
        List<MenuTreeRes> tree = adminMenuService.getMenuTree(APP);
        Assert.assertEquals("收窄后一棵顶部", 1, tree.size());
        Assert.assertEquals("子节点挂上", 1, tree.get(0).getChildren().size());
        Mockito.verify(acMenuService).getAllMenu(true, APP);

        adminMenuService.getMenuTree();
        Mockito.verify(acMenuService).getAllMenu(true, null);
    }

    /** main/tree 与 sub/tree 同样透传 appCode。 */
    @Test
    public void mainAndSubTreePassAppCodeDown() {
        Mockito.when(acMenuService.getMainMenu(APP)).thenReturn(Arrays.asList(menu(1, null, null, APP)));
        Mockito.when(acMenuService.getSubMenu(1L, APP)).thenReturn(Arrays.asList(menu(2, null, null, APP)));

        Assert.assertEquals(1, adminMenuService.getMainMenuTree(APP).size());
        Assert.assertEquals(1, adminMenuService.getSubMenuTree(reqOf(1L), APP).size());
        Mockito.verify(acMenuService).getMainMenu(APP);
        Mockito.verify(acMenuService).getSubMenu(1L, APP);
    }

    private static com.bidr.authorization.vo.menu.MenuTreeReq reqOf(Long menuId) {
        com.bidr.authorization.vo.menu.MenuTreeReq req = new com.bidr.authorization.vo.menu.MenuTreeReq();
        req.setMenuId(menuId);
        return req;
    }

    // ==================== add 落列腿 ====================

    /** 请求体显式携带 appCode ⇒ 原样落库（不被父继承覆盖）。 */
    @Test
    public void addMenuPersistsExplicitAppCode() {
        stubInsertPath();
        AcMenu entity = menu(0L, 2L, 1L, APP);

        adminMenuService.addMenu(entity, MenuTypeDict.SUB_MENU);

        Assert.assertEquals("显式 appCode 必须原样落列", APP, captureInserted().getAppCode());
    }

    /** 未携带 appCode ⇒ 继承父行（pid 命中）的 app_code。 */
    @Test
    public void addMenuInheritsParentAppCodeWhenAbsent() {
        stubInsertPath();
        Mockito.when(acMenuService.selectById(2L)).thenReturn(menu(2, null, 1L, APP));
        AcMenu entity = menu(0L, 2L, 1L, null);

        adminMenuService.addMenu(entity, MenuTypeDict.SUB_MENU);

        Assert.assertEquals("未携带时必须继承父行 app_code", APP, captureInserted().getAppCode());
    }

    /** 顶部菜单（无 pid 无 grandId）未携带 ⇒ 平台自身（NULL），且不查父行。 */
    @Test
    public void addMainMenuWithoutAppCodeStaysPlatform() {
        stubInsertPath();
        AcMenu entity = menu(0L, null, null, null);

        adminMenuService.addMenu(entity, MenuTypeDict.MENU);

        Assert.assertNull("顶部菜单未携带 ⇒ 平台自身", captureInserted().getAppCode());
        Mockito.verify(acMenuService, Mockito.never()).selectById(Mockito.anyLong());
    }

    /** pid 空时按 grandId 找父行继承（侧边菜单挂在顶部之下的形态）。 */
    @Test
    public void addMenuInheritsByGrandIdWhenPidAbsent() {
        stubInsertPath();
        Mockito.when(acMenuService.selectById(1L)).thenReturn(menu(1, null, null, APP));
        AcMenu entity = menu(0L, null, 1L, null);

        adminMenuService.addMenu(entity, MenuTypeDict.CONTENT);

        Assert.assertEquals("pid 空按 grandId 继承", APP, captureInserted().getAppCode());
    }

    /** insert 路径公共桩：排序计数 0、落库回填 menuId、角色绑定/admin 角色 id 桩掉 */
    private void stubInsertPath() {
        Mockito.when(acMenuService.countByPid(Mockito.any())).thenReturn(0);
        Mockito.when(acMenuService.countByGrandId(Mockito.any())).thenReturn(0);
        Mockito.doAnswer(invocation -> {
            AcMenu saving = invocation.getArgument(0);
            saving.setMenuId(99L);
            return true;
        }).when(acMenuService).insert(Mockito.any(AcMenu.class));
        Mockito.when(acMenuService.updateById(Mockito.any(AcMenu.class))).thenReturn(true);
        Mockito.when(acRoleMenuService.insert(Mockito.any(com.bidr.authorization.dao.entity.AcRoleMenu.class)))
                .thenReturn(true);
        Mockito.when(sysConfigCacheService.getParamLong(Mockito.anyString())).thenReturn(1L);
    }

    private AcMenu captureInserted() {
        ArgumentCaptor<AcMenu> captor = ArgumentCaptor.forClass(AcMenu.class);
        Mockito.verify(acMenuService).insert(captor.capture());
        return captor.getValue();
    }
}
