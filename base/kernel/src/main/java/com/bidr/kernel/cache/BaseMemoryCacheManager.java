package com.bidr.kernel.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Title: BaseMemoryCacheManager
 * Description: 本地内存缓存管理基座（diboot 同名类的自研等价实现，接口方法全量对齐 BaseCacheManager）。
 * 维护 cacheName -> Spring Cache 的映射；getCache 对未注册的 cacheName 自动创建
 * （diboot 原版继承 SimpleCacheManager 对未注册 cacheName 返回 null，此处更宽容避免 NPE）。
 * 过期清理由子类实现：StaticMemoryCacheManager 不支持过期，DynamicMemoryCacheManager 提供时间戳过期机制。
 * Copyright: Copyright (c) 2024 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2026/09/08
 */
@Slf4j
public abstract class BaseMemoryCacheManager implements InitializingBean, CacheManager, BaseCacheManager {

    /**
     * cacheName -> Cache 映射
     */
    protected final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();

    /**
     * 预注册的缓存列表（构造/配置阶段写入，afterPropertiesSet 时装载）
     */
    private List<Cache> caches = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        for (Cache cache : loadCaches()) {
            cacheMap.put(cache.getName(), cache);
        }
    }

    /**
     * 设置预注册的缓存列表
     *
     * @param caches 缓存列表
     */
    public void setCaches(List<Cache> caches) {
        this.caches = caches;
    }

    /**
     * 装载预注册缓存（子类可覆盖）
     *
     * @return 缓存列表
     */
    protected List<Cache> loadCaches() {
        return new ArrayList<>(caches);
    }

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, ConcurrentMapCache::new);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheMap.keySet();
    }

    /**
     * 缓存对象
     *
     * @param cacheName 缓存名
     * @param objKey    键
     * @param obj       值
     */
    @Override
    public void putCacheObj(String cacheName, Object objKey, Object obj) {
        Cache cache = getCache(cacheName);
        cache.put(objKey, obj);
    }

    @Override
    public void removeCacheObj(String cacheName, Object objKey) {
        Cache cache = getCache(cacheName);
        cache.evict(objKey);
        if (log.isDebugEnabled()) {
            ConcurrentMap<Object, Object> nativeCache = (ConcurrentMap<Object, Object>) cache.getNativeCache();
            log.debug("缓存删除: {}.{} , 当前size={}", cacheName, objKey, nativeCache.size());
        }
    }

    /**
     * 缓存是否未初始化（无数据）
     *
     * @param cacheName 缓存名
     * @return true=无数据
     */
    @Override
    public boolean isUninitializedCache(String cacheName) {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            return true;
        }
        ConcurrentMap<Object, Object> nativeCache = (ConcurrentMap<Object, Object>) cache.getNativeCache();
        return nativeCache.isEmpty();
    }

    /**
     * 获取缓存对象
     *
     * @param cacheName 缓存名
     * @param objKey    键
     * @param tClass    值类型
     * @param <T>       值类型
     * @return 缓存值，无则 null
     */
    @Override
    public <T> T getCacheObj(String cacheName, Object objKey, Class<T> tClass) {
        Cache cache = getCache(cacheName);
        T value = cache.get(objKey, tClass);
        if (log.isTraceEnabled()) {
            log.trace("从缓存获取: {}.{} = {}", cacheName, objKey, value);
        }
        return value;
    }

    @Override
    public <T> T getCacheObj(String cacheName, Object objKey, Callable<T> valueLoader) {
        Cache cache = getCache(cacheName);
        T value = cache.get(objKey, valueLoader);
        if (log.isTraceEnabled()) {
            log.trace("从缓存获取: {}.{} = {}", cacheName, objKey, value);
        }
        return value;
    }

    @Override
    public String getCacheString(String cacheName, Object objKey) {
        return getCacheObj(cacheName, objKey, String.class);
    }
}
