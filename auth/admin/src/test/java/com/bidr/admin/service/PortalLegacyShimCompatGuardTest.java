package com.bidr.admin.service;

import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.constant.dict.portal.anno.PortalDictField;
import com.bidr.kernel.constant.dict.portal.anno.PortalDisplayOnlyField;
import com.bidr.kernel.constant.dict.portal.anno.PortalEntityField;
import com.bidr.kernel.constant.dict.portal.anno.PortalFieldAnnos;
import com.bidr.kernel.constant.dict.portal.anno.PortalIdField;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: PortalLegacyShimCompatGuardTest
 * Description: 旧包名兼容垫片的三条机器闸门。
 * 列配置正源＝kernel 的 portal.anno 包，com.bidr.admin.config 保留同名 @Deprecated 垫片供存量代码引用，
 * 扫描侧统一经 PortalFieldAnnos 归一。本测试保证：垫片与正源属性可互相适配、消费点不再裸读注解、
 * 且旧包写法解析出的列属性与正源写法逐属性等值。
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class PortalLegacyShimCompatGuardTest {

    private static final String LEGACY_PREFIX = "com.bidr.admin.config.";

    /**
     * 裸读注解的四种调用形态，接收者变量名不参与匹配（旧写法 `f.getAnnotation(PortalXxx.class)` 同样命中）。
     * 白名单只有 PortalSelect 与 PortalDynamicColumn(s)：它们未搬家，仍在旧包，直接 getAnnotation 是正确写法。
     */
    private static final Pattern RAW_READ = Pattern.compile(
            "\\.(get(?:Declared)?Annotation|getAnnotationsByType|isAnnotationPresent)"
                    + "\\s*\\(\\s*(?:[A-Za-z_][\\w.]*\\.)?(Portal\\w*)\\s*\\.\\s*class\\s*\\)");

    /**
     * 垫片 → 正源 的 16 对同名注解（本批新增的 5 枚不留垫片，故不在此列）
     */
    private static final Class<?>[][] SHIM_PAIRS = {
            {com.bidr.admin.config.PortalAudioField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalAudioField.class},
            {com.bidr.admin.config.PortalDictField.class, PortalDictField.class},
            {com.bidr.admin.config.PortalDisplayNoneField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalDisplayNoneField.class},
            {com.bidr.admin.config.PortalDisplayOnlyField.class, PortalDisplayOnlyField.class},
            {com.bidr.admin.config.PortalEntityField.class, PortalEntityField.class},
            {com.bidr.admin.config.PortalGroupDisplayField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalGroupDisplayField.class},
            {com.bidr.admin.config.PortalIdField.class, PortalIdField.class},
            {com.bidr.admin.config.PortalImageField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalImageField.class},
            {com.bidr.admin.config.PortalMoneyField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalMoneyField.class},
            {com.bidr.admin.config.PortalNameField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalNameField.class},
            {com.bidr.admin.config.PortalNoFilterField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalNoFilterField.class},
            {com.bidr.admin.config.PortalOrderField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalOrderField.class},
            {com.bidr.admin.config.PortalPercentField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalPercentField.class},
            {com.bidr.admin.config.PortalPidField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalPidField.class},
            {com.bidr.admin.config.PortalSortField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalSortField.class},
            {com.bidr.admin.config.PortalTextAreaField.class,
                    com.bidr.kernel.constant.dict.portal.anno.PortalTextAreaField.class},
    };

    private static final String[] CONSUMER_FILES = {
            "src/main/java/com/bidr/admin/service/PortalConfigService.java",
            "src/main/java/com/bidr/admin/service/common/BasePortalService.java",
            "src/main/java/com/bidr/admin/service/excel/validation/ExcelValidationHandler.java",
    };

    static class LegacyAnnotated {
        @com.bidr.admin.config.PortalEntityField(entity = String.class, field = "name", joinField = "deptId")
        String deptName;

        @com.bidr.admin.config.PortalDictField(BoolDict.class)
        String status;

        @com.bidr.admin.config.PortalDisplayOnlyField
        String createTime;

        @com.bidr.admin.config.PortalIdField
        Long userId;

        String plain;
    }

    static class KernelAnnotated {
        @PortalEntityField(entity = String.class, field = "name", joinField = "deptId", entityField = "deptId")
        String deptName;

        @PortalDictField(BoolDict.class)
        String status;

        @PortalDisplayOnlyField
        String createTime;

        @PortalIdField
        Long userId;

        String plain;
    }

    /**
     * 闸门一：垫片与正源的属性集必须可互相适配——垫片声明的每个成员在正源上同名同类型同默认值；
     * 正源多出的成员（本批＝PortalEntityField.entityField）必须自带默认值，否则旧包写法解析时会无处回落。
     */
    @DataProvider(name = "shimPairs")
    public Object[][] shimPairs() {
        Object[][] rows = new Object[SHIM_PAIRS.length][];
        for (int i = 0; i < SHIM_PAIRS.length; i++) {
            rows[i] = new Object[]{SHIM_PAIRS[i][0], SHIM_PAIRS[i][1]};
        }
        return rows;
    }

    @Test(dataProvider = "shimPairs")
    public void shimAttributesMustAdaptToCanonical(Class<? extends Annotation> shim,
                                                   Class<? extends Annotation> canonical) throws Exception {
        Assert.assertEquals(shim.getName(), LEGACY_PREFIX + canonical.getSimpleName(),
                "垫片与正源必须同名，否则 PortalFieldAnnos 按 simpleName 归一会失效");
        for (Method shimMethod : shim.getDeclaredMethods()) {
            Method canonicalMethod = canonical.getMethod(shimMethod.getName());
            Assert.assertEquals(canonicalMethod.getReturnType(), shimMethod.getReturnType(),
                    canonical.getSimpleName() + "." + shimMethod.getName() + " 返回类型不一致");
            Assert.assertEquals(canonicalMethod.getDefaultValue(), shimMethod.getDefaultValue(),
                    canonical.getSimpleName() + "." + shimMethod.getName() + " 默认值不一致");
        }
        for (Method canonicalMethod : canonical.getDeclaredMethods()) {
            try {
                shim.getMethod(canonicalMethod.getName());
            } catch (NoSuchMethodException absentOnShim) {
                Assert.assertNotNull(canonicalMethod.getDefaultValue(),
                        "正源新增成员 " + canonical.getSimpleName() + "." + canonicalMethod.getName()
                                + " 必须声明默认值，旧包写法才回落得出来");
            }
        }
    }

    /**
     * 闸门二：Portal 列注解一律经 PortalFieldAnnos 读取，消费文件里不得残留 field.getAnnotation(Portal*)——
     * 旧包写法被裸读时会静默返回 null（列配置整列消失），编译与启动都不报错。
     */
    @Test
    public void consumersMustNotReadPortalAnnosRaw() throws Exception {
        int matched = 0;
        for (String relative : CONSUMER_FILES) {
            File file = new File(relative);
            Assert.assertTrue(file.isFile(), "闸门失效：消费文件不存在，扫描面为空＝" + file.getAbsolutePath());
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Assert.assertTrue(source.contains("PortalFieldAnnos."), relative + " 未经门面读取 Portal 注解");
            Matcher matcher = RAW_READ.matcher(source);
            while (matcher.find()) {
                matched++;
                String anno = matcher.group(2);
                boolean whitelisted = "PortalSelect".equals(anno) || anno.startsWith("PortalDynamicColumn");
                Assert.assertTrue(whitelisted,
                        relative + " 残留裸读注解：" + matcher.group());
            }
        }
        Assert.assertTrue(matched > 0,
                "闸门失效：三枚消费文件里一条裸读形态都没匹配到（连白名单件都不命中），说明正则在空扫描");
    }

    /**
     * 闸门三：旧包写法解析结果与正源写法逐属性等值，且正源新增成员回落默认值。
     */
    @Test
    public void legacyUsageResolvesToCanonicalEquivalent() throws Exception {
        Assert.assertNull(field(LegacyAnnotated.class, "deptName").getAnnotation(PortalEntityField.class),
                "前置失真：旧包注解若能被 getAnnotation 直接命中，双识别就不是必需的了");

        PortalEntityField fromShim = PortalFieldAnnos.find(field(LegacyAnnotated.class, "deptName"),
                PortalEntityField.class);
        PortalEntityField fromCanonical = PortalFieldAnnos.find(field(KernelAnnotated.class, "deptName"),
                PortalEntityField.class);
        Assert.assertNotNull(fromShim);
        Assert.assertEquals(fromShim.annotationType(), PortalEntityField.class);
        Assert.assertEquals(fromShim.entity(), fromCanonical.entity());
        Assert.assertEquals(fromShim.field(), fromCanonical.field());
        Assert.assertEquals(fromShim.joinField(), fromCanonical.joinField());
        Assert.assertEquals(fromShim.entityField(), "", "垫片无 entityField 成员，应回落正源默认值空串");

        PortalDictField dictFromShim = PortalFieldAnnos.find(field(LegacyAnnotated.class, "status"),
                PortalDictField.class);
        Assert.assertEquals(dictFromShim.value(), BoolDict.class);
        PortalDisplayOnlyField displayFromShim = PortalFieldAnnos.find(field(LegacyAnnotated.class, "createTime"),
                PortalDisplayOnlyField.class);
        Assert.assertEquals(displayFromShim.table(), "1");
        Assert.assertEquals(displayFromShim.detail(), "1");

        Assert.assertTrue(PortalFieldAnnos.present(field(LegacyAnnotated.class, "userId"), PortalIdField.class));
        Assert.assertFalse(PortalFieldAnnos.present(field(LegacyAnnotated.class, "plain"), PortalIdField.class));
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        return owner.getDeclaredField(name);
    }
}
