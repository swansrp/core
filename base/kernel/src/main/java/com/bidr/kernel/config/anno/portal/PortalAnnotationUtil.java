package com.bidr.kernel.config.anno.portal;

import com.bidr.kernel.utils.FuncUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Portal 字段注解双口径读取工具。
 *
 * <p>列配置注解的正源在 kernel（config/anno/portal，与本类同包），但历史契约里它们位于
 * com.bidr.admin.config，业务 VO 尚有大量该包 import。两包各持一份同名同成员的注解定义，
 * Java 注解按类型精确匹配，消费方必须两枚都认：{@link #has} 判存在、{@link #fetch} 取实例。
 *
 * <p>镜像实例经动态代理适配回 kernel 类型（成员访问逐个委托原实例），消费代码形状不变。
 */
public final class PortalAnnotationUtil {

    private static final String MIRROR_PREFIX = "com.bidr.admin.config.";

    private PortalAnnotationUtil() {
    }

    /**
     * 取字段上的 Portal 注解：正源（kernel 包）优先；仅挂镜像（admin 包）时适配为正源类型返回。
     *
     * @param field       VO 字段
     * @param mirrorClass admin 包同名镜像注解类，正源类按简单名在本类同包推导
     * @return 正源类型的注解实例，两者皆无时 null
     */
    public static <A extends Annotation> A fetch(Field field, Class<? extends Annotation> mirrorClass) {
        Class<A> portalClass = portalClass(mirrorClass);
        A direct = field.getAnnotation(portalClass);
        if (FuncUtil.isNotEmpty(direct)) {
            return direct;
        }
        Annotation mirror = field.getAnnotation(mirrorClass);
        if (FuncUtil.isEmpty(mirror)) {
            return null;
        }
        return adapt(portalClass, mirror);
    }

    /**
     * 字段是否携带正源或镜像任一形态的 Portal 注解。
     */
    public static boolean has(Field field, Class<? extends Annotation> mirrorClass) {
        return fetch(field, mirrorClass) != null;
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> Class<A> portalClass(Class<? extends Annotation> mirrorClass) {
        String fqcn = mirrorClass.getName();
        if (!fqcn.startsWith(MIRROR_PREFIX)) {
            throw new IllegalArgumentException("Portal 镜像注解须位于 " + MIRROR_PREFIX + "，实际: " + fqcn);
        }
        try {
            // 从本类所在包推导正源包名，不落字面量——注解再搬家时字符串双写是会静默漂移的
            return (Class<A>) Class.forName(
                    PortalAnnotationUtil.class.getPackage().getName() + "." + mirrorClass.getSimpleName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("kernel 缺少 Portal 注解正源: " + mirrorClass.getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A adapt(Class<A> portalClass, Annotation source) {
        return (A) Proxy.newProxyInstance(portalClass.getClassLoader(),
                new Class<?>[]{portalClass}, new AnnotationAdapter(source));
    }

    /**
     * 把镜像注解实例的成员调用委托给原实例（两包同名同成员，方法签名一致）。
     */
    private static final class AnnotationAdapter implements InvocationHandler {

        private final Annotation source;

        private AnnotationAdapter(Annotation source) {
            this.source = source;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("annotationType".equals(method.getName()) && (args == null || args.length == 0)) {
                return source.annotationType();
            }
            if ("toString".equals(method.getName()) && (args == null || args.length == 0)) {
                return source.toString();
            }
            if ("hashCode".equals(method.getName()) && (args == null || args.length == 0)) {
                return source.hashCode();
            }
            if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            try {
                return source.getClass().getMethod(method.getName(), method.getParameterTypes())
                        .invoke(source, args);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "Portal 镜像注解 " + source.annotationType().getName()
                                + " 缺少正源成员 " + method.getName() + "，两包定义已漂移", e);
            }
        }
    }
}
