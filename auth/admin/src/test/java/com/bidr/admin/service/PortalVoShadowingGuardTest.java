package com.bidr.admin.service;

import com.bidr.admin.manage.user.vo.UserAdminRes;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.PackageScanUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.ApiModelProperty;
import org.reflections.Reflections;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Title: PortalVoShadowingGuardTest
 * Description: Portal VO 字段遮蔽守卫测试（列配置正源化 2026-10-17 的配套纪律针）
 * <p>
 * 列配置（sys_portal_column）的唯一正源＝VO 字段声明顺序＋Portal 注解。
 * ReflectionUtil.getFields 是"子类优先＋按名首见去重"：子类重复声明父类字段会把该列整体前移。
 * 带 @ApiModelProperty/Portal 注解的重复声明是既有合法惯用（如 PortalAssociateRes 给实体列定序），
 * 不带任何显示注解的重复声明则是粗心复制——无声改列序、丢显示元数据，本测试钉死为违规。
 * UserAdminRes 清空（字段全部下沉 UserRes，禁止再加）是 2026-10-17 裁定，另立一针。</p>
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Test
public class PortalVoShadowingGuardTest {

    /**
     * 扫描下限（防死针）：core 现有 @AdminPortal 且实现 AdminControllerInf 的控制器远多于此数；
     * 若扫描机制失效（包名/注解改动），命中数跌破下限即失败，而不是"空扫描算通过"。
     */
    private static final int SCAN_FLOOR = 10;

    public void portalVoAnnotatedShadowingOnly() {
        Reflections reflections = PackageScanUtil.reflections("com.bidr");
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(AdminPortal.class);
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (Class<?> controller : controllers) {
            if (!AdminControllerInf.class.isAssignableFrom(controller)) {
                continue;
            }
            Class<?> voClass = ReflectionUtil.getSuperClassGenericType(controller, 1);
            if (voClass == null) {
                continue;
            }
            scanned++;
            violations.addAll(detectBareShadows(voClass));
        }
        assertTrue(scanned >= SCAN_FLOOR,
                "守卫针失效：仅扫描到 " + scanned + " 个 Portal VO（下限 " + SCAN_FLOOR + "），请检查 @AdminPortal 扫描面");
        assertTrue(violations.isEmpty(),
                "以下 Portal VO 子类无注解地重复声明了祖先字段，会把该列前移并丢失祖先侧显示配置：\n"
                        + String.join("\n", violations));
    }

    /**
     * 2026-10-17 裁定：用户管理页列配置正源＝UserRes 声明序；UserAdminRes 只作泛型参数，禁止承载字段。
     */
    public void userAdminResMustStayEmpty() {
        List<String> declared = new ArrayList<>();
        for (Field field : UserAdminRes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                declared.add(field.getName());
            }
        }
        assertEquals(declared, new ArrayList<String>(),
                "UserAdminRes 已清空（字段全部下沉 UserRes）；新增/回写会破坏列序正源，请把字段加到 UserRes 并排在正确位置");
    }

    /**
     * 正对照腿：合成一个无注解遮蔽父类字段的类，证明检测器本身还活着（空扫描不是通过）；
     * 另一合成类带 @ApiModelProperty 重复声明，证明合法惯用不被误杀。
     */
    public void detectorMustCatchSyntheticBareShadow() {
        List<String> hits = detectBareShadows(BareShadowChild.class);
        assertTrue(hits.size() == 1 && hits.get(0).contains("dup"),
                "守卫针失效：检测器抓不到合成无注解遮蔽，实际=" + hits);
        assertTrue(detectBareShadows(LicensedShadowChild.class).isEmpty(),
                "检测器误杀带 @ApiModelProperty 的合法重复声明");
    }

    static class ShadowParent {
        String dup;
        String onlyParent;
    }

    static class BareShadowChild extends ShadowParent {
        String dup;
    }

    static class LicensedShadowChild extends ShadowParent {
        @ApiModelProperty(value = "dup")
        String dup;
    }

    private static List<String> detectBareShadows(Class<?> voClass) {
        List<String> violations = new ArrayList<>();
        Map<String, String> ancestorFields = new HashMap<>();
        for (Class<?> c = voClass.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                ancestorFields.putIfAbsent(field.getName(), c.getSimpleName());
            }
        }
        for (Field field : voClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String owner = ancestorFields.get(field.getName());
            if (owner == null || hasDisplayIntent(field)) {
                continue;
            }
            violations.add(voClass.getName() + " 无注解重复声明祖先 " + owner + " 的字段 " + field.getName());
        }
        return violations;
    }

    private static boolean hasDisplayIntent(Field field) {
        if (field.isAnnotationPresent(ApiModelProperty.class)) {
            return true;
        }
        for (java.lang.annotation.Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().startsWith("Portal")) {
                return true;
            }
        }
        return false;
    }
}
