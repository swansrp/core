package com.bidr.kernel.constant.dict.portal.anno;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Title: PortalFieldAnnos
 * Description: Portal 列注解读取门面——正源在 {@code com.bidr.kernel.constant.dict.portal.anno}，
 * 旧包 {@code com.bidr.admin.config} 保留同名 @Deprecated 垫片供存量代码继续引用，本类负责把
 * 垫片实例归一成同名的正源注解实例（属性名与类型一致，正源独有成员回落其默认值）。
 *
 * @author Sharp
 * @since 2026-10-17
 */
public final class PortalFieldAnnos {

    private static final String LEGACY_PACKAGE = "com.bidr.admin.config.";

    private PortalFieldAnnos() {
    }

    /**
     * @return 正源注解实例；字段既无正源也无旧包同名垫片时返回 null
     */
    public static <A extends Annotation> A find(AnnotatedElement element, Class<A> annoType) {
        A direct = element.getAnnotation(annoType);
        if (direct != null) {
            return direct;
        }
        for (Annotation present : element.getAnnotations()) {
            Class<? extends Annotation> presentType = present.annotationType();
            if (!presentType.getName().equals(LEGACY_PACKAGE + annoType.getSimpleName())) {
                continue;
            }
            return adapt(present, annoType);
        }
        return null;
    }

    public static boolean present(AnnotatedElement element, Class<? extends Annotation> annoType) {
        return find(element, annoType) != null;
    }

    private static <A extends Annotation> A adapt(Annotation legacy, Class<A> annoType) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("annotationType".equals(name)) {
                return annoType;
            }
            if ("toString".equals(name)) {
                return annoType.getSimpleName() + "(from legacy shim " + legacy.annotationType().getName() + ")";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            Method onLegacy;
            try {
                onLegacy = legacy.annotationType().getMethod(name, method.getParameterTypes());
            } catch (NoSuchMethodException absentOnLegacy) {
                Object defaultValue = method.getDefaultValue();
                if (defaultValue == null && !method.isDefault()) {
                    throw new IllegalStateException("旧包注解 " + legacy.annotationType().getName() + " 缺少成员 " + name
                            + "，且正源 " + annoType.getName() + " 未声明默认值");
                }
                return defaultValue;
            }
            return onLegacy.invoke(legacy, args);
        };
        return annoType.cast(Proxy.newProxyInstance(annoType.getClassLoader(), new Class<?>[]{annoType}, handler));
    }
}
