package com.bidr.admin.service;

import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.portal.PortalAnnotationUtil;
import com.bidr.kernel.constant.dict.portal.anno.PortalDictField;
import com.bidr.kernel.constant.dict.portal.anno.PortalEntityField;
import com.bidr.kernel.constant.dict.portal.anno.PortalMoneyField;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Title: PortalAnnotationDualGuardTest
 * Description: Portal 注解双口径守卫针。
 * <p>
 * 列配置注解正源在 kernel anno 包，历史契约在 com.bidr.admin.config（业务 VO 大量该包 import，
 * 两包各持一份同名定义）。消费方必须经 {@link PortalAnnotationUtil} 双认两包，否则业务侧挂镜像
 * 注解的列会静默丢配置。三腿：镜像腿（业务契约）／正源腿（core 内 VO 契约）／负向腿（针活着）。
 * 镜像与正源成员签名一致性由镜像腿的取值断言顺带钉死——两包漂移时委托取成员会抛
 * "定义已漂移"，即为本针见血处。</p>
 *
 * @author Sharp
 * @since 2026-10-18
 */
@Test
public class PortalAnnotationDualGuardTest {

    static class DualVo {
        @com.bidr.admin.config.PortalDictField(ActiveStatusDict.class)
        private String mirrorDictCol;

        @PortalDictField(ActiveStatusDict.class)
        private String portalDictCol;

        @com.bidr.admin.config.PortalEntityField(entity = String.class, field = "code", aggregation = true)
        private String mirrorEntityCol;

        @com.bidr.admin.config.PortalMoneyField(unit = 7000, fix = 3)
        private String mirrorMoneyCol;

        private String bareCol;
    }

    private static Field field(String name) {
        try {
            return DualVo.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void mirrorAnnotatedFieldResolvesToPortalType() {
        PortalDictField dict = PortalAnnotationUtil.fetch(field("mirrorDictCol"),
                com.bidr.admin.config.PortalDictField.class);
        assertTrue(dict != null, "admin 镜像注解须被双识别拾取");
        assertEquals(dict.value(), ActiveStatusDict.class);
        MetaDict meta = dict.value().getAnnotation(MetaDict.class);
        assertTrue(meta != null, "镜像 value() 委托后仍应读到字典元注解");
    }

    @Test
    public void portalAnnotatedFieldStillResolves() {
        assertTrue(PortalAnnotationUtil.has(field("portalDictCol"),
                com.bidr.admin.config.PortalDictField.class), "kernel 正源腿不得被镜像改造打断");
    }

    @Test
    public void mirrorMembersDelegate() {
        PortalEntityField entity = PortalAnnotationUtil.fetch(field("mirrorEntityCol"),
                com.bidr.admin.config.PortalEntityField.class);
        assertEquals(entity.field(), "code");
        assertEquals(entity.entity(), String.class);
        assertTrue(entity.aggregation());
        // 镜像缺省成员须与正源默认值一致（entityField 为 f47236d7 新增，镜像漏抄即漂移）
        assertEquals(entity.entityField(), "");

        PortalMoneyField money = PortalAnnotationUtil.fetch(field("mirrorMoneyCol"),
                com.bidr.admin.config.PortalMoneyField.class);
        assertEquals(money.unit(), 7000);
        assertEquals(money.fix(), 3);
    }

    @Test
    public void bareFieldMatchesNothing() {
        assertNull(PortalAnnotationUtil.fetch(field("bareCol"),
                com.bidr.admin.config.PortalDictField.class), "负向腿：无注解字段必须取不到");
        assertTrue(!PortalAnnotationUtil.has(field("bareCol"),
                com.bidr.admin.config.PortalMoneyField.class));
    }

    /**
     * 全族结构一致性：kernel anno 包每枚注解的成员，admin 同名镜像必须逐一具备（含默认值）。
     * 镜像漏抄/改签名时，marker 类读取不会报错、只在取成员处才炸——本针把漂移挡在编译期之后、运行前。
     */
    @Test
    public void mirrorFamilyMatchesPortalMembers() {
        Class<?>[] portals = {
                com.bidr.kernel.constant.dict.portal.anno.PortalAudioField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalDictField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalDisableField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalDisplayNoneField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalDisplayOnlyField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalEditAbleField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalEntityField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalGroupDisplayField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalIdField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalImageField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalImportField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalLargeField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalMoneyField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalNameField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalNoFilterField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalNoSortField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalOrderField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalPercentField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalPidField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalSortField.class,
                com.bidr.kernel.constant.dict.portal.anno.PortalTextAreaField.class,
        };
        for (Class<?> portal : portals) {
            Class<?> mirror;
            try {
                mirror = Class.forName("com.bidr.admin.config." + portal.getSimpleName());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("admin 镜像缺枚: " + portal.getSimpleName(), e);
            }
            for (java.lang.reflect.Method member : portal.getDeclaredMethods()) {
                try {
                    mirror.getMethod(member.getName(), member.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    throw new AssertionError(
                            "镜像成员漂移: " + portal.getSimpleName() + "." + member.getName(), e);
                }
            }
        }
        assertEquals(portals.length, 21, "全族数量针");
    }
}
