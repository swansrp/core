package com.bidr.kernel.utils;

import org.reflections.Reflections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Title: PackageScanUtil
 * Description: Copyright: Copyright (c) 2026 Company: Plsintec Ltd.
 * 包扫描工具,支持 my.base-package 配置为逗号分隔的多个基础包
 *
 * @author Sharp
 * @since 2026/09/03
 */
public class PackageScanUtil {

    /**
     * Reflections 扫描器缓存,key 为基础包配置
     * 构建索引需遍历解析整个 classpath,成本高,同一配置进程内只扫描一次,结果永久有效
     */
    private static final ConcurrentMap<String, Reflections> REFLECTIONS_CACHE = new ConcurrentHashMap<>();

    /**
     * 按逗号拆分基础包配置,构建 Reflections 扫描器
     * 同一基础包配置的扫描器进程内缓存复用,并发调用同一配置时仅首个线程执行扫描,其余线程等待复用结果
     *
     * @param basePackage 基础包配置,支持逗号分隔的多包
     * @return Reflections 扫描器(进程级共享实例,调用方只读使用,禁止调用 merge/save/expandSuperTypes)
     */
    public static Reflections reflections(String basePackage) {
        return REFLECTIONS_CACHE.computeIfAbsent(basePackage,
                pkg -> new Reflections((Object[]) pkg.split(",")));
    }

    /**
     * 判断类全名是否属于任一基础包
     *
     * @param className   类全名
     * @param basePackage 基础包配置,支持逗号分隔的多包
     * @return 属于任一基础包返回 true
     */
    public static boolean inBasePackage(String className, String basePackage) {
        for (String pkg : basePackage.split(",")) {
            if (className.startsWith(pkg.trim())) {
                return true;
            }
        }
        return false;
    }
}
