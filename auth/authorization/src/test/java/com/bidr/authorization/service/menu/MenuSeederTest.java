package com.bidr.authorization.service.menu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleMenuService;
import com.bidr.authorization.service.menu.MenuDeclaration.Node;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: MenuSeederTest
 * Description: 菜单种子器离线腿（menu-seed appCode 支持棒）——不起 Spring、不连库，mock 仓储层。
 * 覆盖两面：①判重 wrapper 真针（包可见 static 构造器断言 SQL 形态，照 {@code AcSubsystemServiceTest}
 * 家规：lambda 列名解析需静态块先装 TableInfo 缓存）——声明 appCode ⇒ app_code 等值且参数带原值、
 * 缺省 ⇒ app_code IS NULL（平台自身，与既有全 NULL 行兼容）；②insertRow 落列真针（捕获实体断言
 * app_code 按声明落列/缺省 null＋新行绑 admin 角色）；③run() 全链冒烟（classpath 真声明走 mock
 * 仓储离线播种：缺省声明全落 NULL、每新行绑一次 admin 角色）。
 * DB 真值幂等（重启第二次应零新增）不属本类，交真机腿（见 MenuSeeder 类注释）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-08
 */
public class MenuSeederTest {

    private static final String APP_CODE = "app-x";
    private static final long ADMIN_ROLE_ID = 9L;

    static {
        // 离线建 TableInfo 缓存：getSqlSegment() 解析 lambda 列名要用它（AcSubsystemServiceTest 同款）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcMenu.class);
    }

    /** 手搭声明节点（Node 构造器包可见；同包直建，免去 yml 样板） */
    private static Node node(String title, String path, String appCode) {
        Node node = new Node(true, "unit-test");
        node.title = title;
        node.path = path;
        node.appCode = appCode;
        return node;
    }

    // ==================== 判重 wrapper 真针：app_code 维度 ====================

    /** 声明 appCode ⇒ topnav 判重收窄到 app_code 等值，参数表带声明原值。 */
    @Test
    public void topNavWrapperDeclaredAppCodeFiltersByEquality() {
        LambdaQueryWrapper<AcMenu> wrapper = MenuSeeder.topNavWrapper(node("应用导航", "appNav", APP_CODE));
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("判重必须含 app_code 维度: " + sql, sql.contains("app_code"));
        Assert.assertFalse("声明 appCode ⇒ 等值而非 IS NULL: " + sql, sql.contains("app_code IS NULL"));
        Assert.assertTrue("判重基础形态 pid IS NULL 须保留: " + sql, sql.contains("pid IS NULL"));
        Assert.assertTrue("判重基础形态 title∨path 须保留: " + sql, sql.contains("OR"));
        Assert.assertTrue("参数表必须带 appCode 原值",
                wrapper.getParamNameValuePairs().containsValue(APP_CODE));
    }

    /** 缺省不声明 ⇒ topnav 判重 app_code IS NULL＝平台自身（既有全 NULL 行为逐字节等价的前提）。 */
    @Test
    public void topNavWrapperWithoutAppCodeMatchesOnlyNullRows() {
        LambdaQueryWrapper<AcMenu> wrapper = MenuSeeder.topNavWrapper(node("系统管理", "SystemManage", null));
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("缺省声明 ⇒ app_code IS NULL: " + sql, sql.contains("app_code IS NULL"));
        Assert.assertFalse("未声明不得出现 app_code 等值: " + sql, sql.contains("app_code ="));
    }

    /** 声明 appCode ⇒ 下钻判重同样收窄 app_code 等值，pid/grand_id/path 基础形态保留。 */
    @Test
    public void childWrapperDeclaredAppCodeFiltersByEquality() {
        Node child = node("应用页", "p1", APP_CODE);
        LambdaQueryWrapper<AcMenu> wrapper = MenuSeeder.childWrapper(child, 7L, 3L);
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("判重必须含 app_code 维度: " + sql, sql.contains("app_code"));
        Assert.assertFalse("声明 appCode ⇒ 等值而非 IS NULL: " + sql, sql.contains("app_code IS NULL"));
        Assert.assertTrue("下钻形态 grand_id 须保留: " + sql, sql.contains("grand_id"));
        Assert.assertTrue("下钻形态 path 须保留: " + sql, sql.contains("path"));
        Assert.assertFalse("有父 id ⇒ 走等值而非 IS NULL: " + sql, sql.contains("pid IS NULL"));
        Assert.assertTrue("参数表必须带父 id 与 appCode 原值",
                wrapper.getParamNameValuePairs().containsValue(7L)
                        && wrapper.getParamNameValuePairs().containsValue(APP_CODE));
    }

    /** 缺省声明＋导航直挂（parentId=null）⇒ pid IS NULL ∧ app_code IS NULL 双 NULL 形态。 */
    @Test
    public void childWrapperWithoutAppCodeTopLevelChildMatchesOnlyNullRows() {
        LambdaQueryWrapper<AcMenu> wrapper = MenuSeeder.childWrapper(node("平台页", "p1", null), null, 3L);
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("导航直挂形态 pid IS NULL 须保留: " + sql, sql.contains("pid IS NULL"));
        Assert.assertTrue("缺省声明 ⇒ app_code IS NULL: " + sql, sql.contains("app_code IS NULL"));
    }

    // ==================== insertRow 落列真针：实体 appCode＋绑 admin 角色 ====================

    /** 声明 appCode ⇒ 新插行落列该值，且照现状绑 admin 角色。 */
    @Test
    public void insertRowFallsDeclaredAppCodeColumnAndBindsAdminRole() {
        AcMenuService menuService = Mockito.mock(AcMenuService.class);
        AcRoleMenuService roleMenuService = Mockito.mock(AcRoleMenuService.class);
        MenuSeeder underTest = new MenuSeeder(menuService, roleMenuService,
                Mockito.mock(SysConfigCacheService.class));

        AcMenu row = underTest.insertRow(node("应用页", "p1", APP_CODE), 7L, 3L,
                MenuTypeDict.SUB_MENU.getValue(), ADMIN_ROLE_ID);

        Assert.assertEquals("声明 appCode ⇒ 落列该值", APP_CODE, row.getAppCode());
        ArgumentCaptor<AcMenu> rowCaptor = ArgumentCaptor.forClass(AcMenu.class);
        Mockito.verify(menuService).insert(rowCaptor.capture());
        Assert.assertSame("落库实体即返回实体", row, rowCaptor.getValue());
        ArgumentCaptor<AcRoleMenu> bindCaptor = ArgumentCaptor.forClass(AcRoleMenu.class);
        Mockito.verify(roleMenuService).insert(bindCaptor.capture());
        Assert.assertEquals("新行绑 admin 角色", Long.valueOf(ADMIN_ROLE_ID), bindCaptor.getValue().getRoleId());
        Assert.assertEquals("播种来源备注", MenuSeeder.REMARK, row.getRemark());
    }

    /** 缺省声明 ⇒ 新插行 app_code 落 NULL＝平台自身（兼容零影响的落列面）。 */
    @Test
    public void insertRowWithoutAppCodeStaysNullForPlatformSelf() {
        MenuSeeder underTest = new MenuSeeder(Mockito.mock(AcMenuService.class),
                Mockito.mock(AcRoleMenuService.class), Mockito.mock(SysConfigCacheService.class));

        AcMenu row = underTest.insertRow(node("平台页", "p2", null), null, null,
                MenuTypeDict.MENU.getValue(), ADMIN_ROLE_ID);

        Assert.assertNull("缺省声明 ⇒ app_code NULL", row.getAppCode());
    }

    // ==================== run() 全链冒烟：classpath 真声明离线播种 ====================

    /**
     * classpath 真声明（均未声明 appCode）走 mock 仓储全链播种：每节点判重（空库全 miss）→ 插行 →
     * 绑角色——所有落库实体 app_code 全 NULL（兼容零影响的端到端针）＋每新行绑一次 admin 角色。
     */
    @Test
    public void runSeedsClasspathDeclarationsAsPlatformRowsWithAdminBinding() throws Exception {
        AcMenuService menuService = Mockito.mock(AcMenuService.class);
        AcRoleMenuService roleMenuService = Mockito.mock(AcRoleMenuService.class);
        SysConfigCacheService configCacheService = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(configCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID))
                .thenReturn(ADMIN_ROLE_ID);

        new MenuSeeder(menuService, roleMenuService, configCacheService).run(null);

        ArgumentCaptor<AcMenu> rowCaptor = ArgumentCaptor.forClass(AcMenu.class);
        Mockito.verify(menuService, Mockito.atLeastOnce()).insert(rowCaptor.capture());
        Assert.assertFalse("classpath 上必须有声明可种（auth 份必在）", rowCaptor.getAllValues().isEmpty());
        for (AcMenu row : rowCaptor.getAllValues()) {
            Assert.assertNull("既有声明无 appCode ⇒ 全部落 NULL（兼容零影响）", row.getAppCode());
        }
        ArgumentCaptor<AcRoleMenu> bindCaptor = ArgumentCaptor.forClass(AcRoleMenu.class);
        Mockito.verify(roleMenuService, Mockito.atLeastOnce()).insert(bindCaptor.capture());
        Assert.assertEquals("每个新插行绑一次 admin 角色",
                rowCaptor.getAllValues().size(), bindCaptor.getAllValues().size());
        for (AcRoleMenu bind : bindCaptor.getAllValues()) {
            Assert.assertEquals(Long.valueOf(ADMIN_ROLE_ID), bind.getRoleId());
        }
    }
}
