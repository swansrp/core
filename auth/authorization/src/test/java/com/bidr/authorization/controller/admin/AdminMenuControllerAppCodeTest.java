package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.service.admin.AdminMenuService;
import com.bidr.authorization.service.permit.MenuService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.Collections;

/**
 * Title: AdminMenuControllerAppCodeTest
 * Description: 管理面菜单控制器 appCode 接线离线单测（方案B 棒3 §4.4，纯 JUnit4＋Mockito，
 * @Resource 字段反射注入）——
 * <ul>
 *     <li>/tree、/user/permit 端点把可选 appCode 透传 service（缺省 null＝平台自身）；</li>
 *     <li>基类 {@code GET /tree/data} 覆写点：getAllData 经当前请求读可选 appCode 参数
 *         （基类端点无参签名不能加同路径映射参数），无请求上下文＝缺省平台自身。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class AdminMenuControllerAppCodeTest {

    private static final String APP = "epc-ai";

    private AdminMenuService adminMenuService;
    private MenuService menuService;
    private AdminMenuController controller;

    @Before
    public void setUp() throws Exception {
        adminMenuService = Mockito.mock(AdminMenuService.class);
        menuService = Mockito.mock(MenuService.class);
        controller = new AdminMenuController();
        inject("adminMenuService", adminMenuService);
        inject("menuService", menuService);
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void inject(String name, Object value) throws Exception {
        Field field = AdminMenuController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static void bindRequest(String appCode) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter("appCode")).thenReturn(appCode);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** /tree 端点：可选 appCode 透传 AdminMenuService.getMenuTree。 */
    @Test
    public void menuTreeEndpointPassesAppCode() {
        Mockito.when(adminMenuService.getMenuTree(APP)).thenReturn(Collections.emptyList());
        Assert.assertTrue(controller.getMenuTree(APP).isEmpty());
        Mockito.verify(adminMenuService).getMenuTree(APP);

        controller.getMenuTree(null);
        Mockito.verify(adminMenuService).getMenuTree(null);
    }

    /** /user/permit 端点：可选 appCode 透传 MenuService.getMenuTree(customerNumber, appCode)。 */
    @Test
    public void userPermitEndpointPassesAppCode() {
        Mockito.when(menuService.getMenuTree("000001", APP)).thenReturn(Collections.emptyList());
        Assert.assertTrue(controller.getUserPermit("000001", APP).isEmpty());
        Mockito.verify(menuService).getMenuTree("000001", APP);
    }

    /** 基类 /tree/data 覆写点：请求带 appCode ⇒ getAllData(appCode)。 */
    @Test
    public void treeDataOverrideReadsAppCodeFromRequest() {
        bindRequest(APP);
        Mockito.when(adminMenuService.getAllData(APP)).thenReturn(Collections.<AcMenu>emptyList());

        controller.getAllData();

        Mockito.verify(adminMenuService).getAllData(APP);
    }

    /** 覆写点缺省腿：无 appCode 参数/无请求上下文 ⇒ getAllData(null)＝平台自身。 */
    @Test
    public void treeDataOverrideDefaultsToPlatformWithoutParam() {
        bindRequest(null);
        Mockito.when(adminMenuService.getAllData(null)).thenReturn(Collections.<AcMenu>emptyList());
        controller.getAllData();
        Mockito.verify(adminMenuService).getAllData(null);

        // 无请求上下文（定时任务/离线形态）同样缺省平台自身
        RequestContextHolder.resetRequestAttributes();
        controller.getAllData();
        Mockito.verify(adminMenuService, Mockito.times(2)).getAllData(null);
    }
}
