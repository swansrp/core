package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.dao.entity.AcMenu;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: AcMenuServiceAppCodeWrapperTest
 * Description: 菜单查询正源的 app_code 谓词真针（方案B 棒3 §4.4，离线不起 Spring 不连库，
 * 形态照 {@link AcSubsystemServiceTest}）——mainMenu/allMenu/subMenu/content 四路查询条件
 * 必须带归属应用谓词：appCode 空 ⇒ {@code app_code IS NULL}（平台自身存量行为），
 * 非空 ⇒ {@code app_code = ?}。删掉任一路的 {@code applyAppCode} 挂线会让该路漏出他 app
 * 菜单，对应腿翻红。
 *
 * @author Sharp
 * @since 2026/11/06
 */
public class AcMenuServiceAppCodeWrapperTest {

    private static final String APP = "epc-ai";

    static {
        // 离线建 TableInfo 缓存：getSqlSegment() 解析 lambda 列名要用它（AcSubsystemServiceTest 同款）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcMenu.class);
    }

    private final AcMenuService service = new AcMenuService();

    // ==================== 四路查询条件：非空 ⇒ app_code 等值 ====================

    @Test
    public void mainMenuWrapperNarrowsByAppCodeEquality() {
        assertEqualityNarrowed(service.mainMenuWrapper(APP));
    }

    @Test
    public void allMenuWrapperNarrowsByAppCodeEquality() {
        assertEqualityNarrowed(service.allMenuWrapper(true, APP));
        // 非管理员腿的既有 menuId/grandId 条件不受收窄挂线影响，仍在场
        Assert.assertTrue("非管理员腿应保留既有 menuId 条件",
                service.allMenuWrapper(false, APP).getSqlSegment().contains("menu_id"));
    }

    @Test
    public void subMenuWrapperNarrowsByAppCodeEquality() {
        assertEqualityNarrowed(service.subMenuWrapper(1L, APP));
    }

    @Test
    public void contentWrapperNarrowsByAppCodeEquality() {
        assertEqualityNarrowed(service.contentWrapper(1L, APP));
    }

    // ==================== 四路查询条件：空/缺省 ⇒ app_code IS NULL（平台自身） ====================

    @Test
    public void mainMenuWrapperDefaultsToPlatformNull() {
        assertNullNarrowed(service.mainMenuWrapper(null));
        assertNullNarrowed(service.mainMenuWrapper(""));
    }

    @Test
    public void allMenuWrapperDefaultsToPlatformNull() {
        assertNullNarrowed(service.allMenuWrapper(true, null));
        assertNullNarrowed(service.allMenuWrapper(true, ""));
    }

    @Test
    public void subMenuWrapperDefaultsToPlatformNull() {
        assertNullNarrowed(service.subMenuWrapper(1L, null));
    }

    @Test
    public void contentWrapperDefaultsToPlatformNull() {
        assertNullNarrowed(service.contentWrapper(1L, null));
    }

    // ==================== 判据 ====================

    private static void assertEqualityNarrowed(LambdaQueryWrapper<AcMenu> wrapper) {
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("查询条件必须含 app_code: " + sql, sql.contains("app_code"));
        Assert.assertFalse("非空 appCode 不许走 IS NULL 谓词: " + sql, sql.contains("app_code IS NULL"));
        Assert.assertTrue("参数表必须带 app_code 原值",
                wrapper.getParamNameValuePairs().containsValue(APP));
    }

    private static void assertNullNarrowed(LambdaQueryWrapper<AcMenu> wrapper) {
        String sql = wrapper.getSqlSegment();
        Assert.assertTrue("缺省/空必须带 app_code IS NULL 谓词: " + sql, sql.contains("app_code IS NULL"));
        Assert.assertFalse("缺省/空不许带 app_code 等值谓词: " + sql, sql.contains("app_code ="));
    }
}
