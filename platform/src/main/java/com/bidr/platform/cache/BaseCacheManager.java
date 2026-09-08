package com.bidr.platform.cache;

import java.util.concurrent.Callable;

/**
 * Title: BaseCacheManager
 * Description: 本地缓存管理器统一接口（diboot 同名接口的自研等价实现）。
 * 静态实现（StaticMemoryCacheManager）无过期机制；动态实现（DynamicMemoryCacheManager）
 * 提供 key 级时间戳过期与按天清理。
 * Copyright: Copyright (c) 2024 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2026/09/08
 */
public interface BaseCacheManager {

    /**
     * 获取缓存对象
     *
     * @param cacheName 缓存名
     * @param objKey    键
     * @param tClass    值类型
     * @param <T>       值类型
     * @return 缓存值，无则 null
     */
    <T> T getCacheObj(String cacheName, Object objKey, Class<T> tClass);

    /**
     * 获取缓存对象，无则经 valueLoader 加载并回填（Spring Cache 原生语义）
     *
     * @param cacheName   缓存名
     * @param objKey      键
     * @param valueLoader 加载器
     * @param <T>         值类型
     * @return 缓存值
     */
    <T> T getCacheObj(String cacheName, Object objKey, Callable<T> valueLoader);

    /**
     * 获取缓存字符串
     *
     * @param cacheName 缓存名
     * @param objKey    键
     * @return 缓存值，无则 null
     */
    String getCacheString(String cacheName, Object objKey);

    /**
     * 缓存对象
     *
     * @param cacheName 缓存名
     * @param objKey    键
     * @param obj       值
     */
    void putCacheObj(String cacheName, Object objKey, Object obj);

    /**
     * 删除缓存对象
     *
     * @param cacheName 缓存名
     * @param objKey    键
     */
    void removeCacheObj(String cacheName, Object objKey);

    /**
     * 缓存是否未初始化（无数据）
     *
     * @param cacheName 缓存名
     * @return true=无数据
     */
    boolean isUninitializedCache(String cacheName);

    /**
     * 清理过期数据
     *
     * @param cacheName 缓存名
     */
    void clearOutOfDateData(String cacheName);
}
