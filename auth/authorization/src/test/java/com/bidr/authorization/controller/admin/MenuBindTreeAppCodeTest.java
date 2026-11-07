package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: MenuBindTreeAppCodeTest
 * Description: role/user/group/dept 四组菜单绑定控制器的树查询 appCode 过滤离线单测
 * （方案B 棒3 §4.4，纯 JUnit4＋Mockito，spy 桩掉 getBindList 偺装绑定面）——
 * 绑定树查询端点（/menu/tree）加可选 appCode 后：缺省＝只出平台自身行，
 * appCode='epc-ai'＝只出该子系统行；绑定关系本身仍按 menu_id（桩面不验，仓储层零改动）。
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class MenuBindTreeAppCodeTest {

    private static final String APP = "epc-ai";

    /** 混合绑定面：平台根1+子2、子系统根3+子4、别app 根5 */
    private static List<AcMenu> mixedBinds() {
        return new ArrayList<>(Arrays.asList(
                menu(1, null, null, null), menu(2, 1L, null, null),
                menu(3, null, null, APP), menu(4, 3L, null, APP),
                menu(5, null, null, "other-app")));
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

    private static void assertAllApp(List<MenuTreeRes> tree, String expectedApp) {
        List<MenuTreeRes> flat = new ArrayList<>();
        collect(tree, flat);
        Assert.assertFalse("树不应为空", flat.isEmpty());
        for (MenuTreeRes node : flat) {
            Assert.assertEquals("绑定树按 appCode 过滤（menuId=" + node.getMenuId() + "）",
                    expectedApp, node.getAppCode());
        }
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

    // ==================== role（entityId=Long，getBindList(Object) 桩面） ====================

    /** 角色绑定树缺省＝只出平台行（1/2），子系统与别 app 行全收窄掉。 */
    @Test
    public void roleMenuTreeDefaultShowsPlatformOnly() {
        AdminRoleMenuBindController spy = Mockito.spy(new AdminRoleMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.<Object>any());

        List<MenuTreeRes> tree = spy.getMenuTree(8L, null);

        Assert.assertEquals("缺省只出平台两行（根1+子2）", 2, count(tree));
        assertAllApp(tree, null);
    }

    /** 角色绑定树 appCode='epc-ai'＝只出该子系统行（3/4）。 */
    @Test
    public void roleMenuTreeNarrowsToRequestedApp() {
        AdminRoleMenuBindController spy = Mockito.spy(new AdminRoleMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.<Object>any());

        List<MenuTreeRes> tree = spy.getMenuTree(8L, APP);

        Assert.assertEquals("appCode 腿只出该子系统两行（根3+子4）", 2, count(tree));
        assertAllApp(tree, APP);
    }

    // ==================== user / group / dept（entityId=String，getBindList(String) 桩面） ====================

    @Test
    public void userMenuTreeDefaultShowsPlatformOnly() {
        AdminUserMenuBindController spy = Mockito.spy(new AdminUserMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.anyString());

        List<MenuTreeRes> tree = spy.getMenuTree("u1", null);

        Assert.assertEquals(2, count(tree));
        assertAllApp(tree, null);
    }

    @Test
    public void userMenuTreeNarrowsToRequestedApp() {
        AdminUserMenuBindController spy = Mockito.spy(new AdminUserMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.anyString());

        List<MenuTreeRes> tree = spy.getMenuTree("u1", APP);

        Assert.assertEquals(2, count(tree));
        assertAllApp(tree, APP);
    }

    @Test
    public void groupMenuTreeDefaultShowsPlatformOnly() {
        AdminGroupMenuBindController spy = Mockito.spy(new AdminGroupMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.anyString());

        List<MenuTreeRes> tree = spy.getMenuTree("g1", null);

        Assert.assertEquals(2, count(tree));
        assertAllApp(tree, null);
    }

    @Test
    public void deptMenuTreeDefaultShowsPlatformOnly() {
        AdminDeptMenuBindController spy = Mockito.spy(new AdminDeptMenuBindController());
        Mockito.doReturn(mixedBinds()).when(spy).getBindList(Mockito.anyString());

        List<MenuTreeRes> tree = spy.getMenuTree("d1", null);

        Assert.assertEquals(2, count(tree));
        assertAllApp(tree, null);
    }

    private static int count(List<MenuTreeRes> tree) {
        List<MenuTreeRes> flat = new ArrayList<>();
        collect(tree, flat);
        return flat.size();
    }
}
