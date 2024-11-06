package com.bidr.kernel.cache;

import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.cache.exception.DynamicMemoryCacheExpiredException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Title: DynamicMemoryCache
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/02/17 11:49
 */
@SuppressWarnings("unchecked")
public abstract class DynamicMemoryCache<T> implements DynamicMemoryCacheInf<T> {

    private final Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    @Lazy
    @Resource
    protected DynamicMemoryCacheManager dynamicMemoryCacheManager;
    @Value("${my.cache.expired}")
    private Integer dynamicMemoryExpiredMinutes;

    @Retryable(value = DynamicMemoryCacheExpiredException.class, maxAttempts = 2)
    public Map<String, T> getAllCache() {
        ConcurrentMapCache cache = (ConcurrentMapCache) dynamicMemoryCacheManager.getCache(getCacheName());
        if (cache != null && FuncUtil.isEmpty(cache.getNativeCache())) {
            refresh();
        }
        return JsonUtil.readJson(cache.getNativeCache(), Map.class, String.class, entityClass);
    }

    public DynamicMemoryCacheManager cacheManager() {
        return dynamicMemoryCacheManager;
    }

    /**
     * 获取缓存数据列表
     *
     * @return
     */
    protected abstract Map<String, T> getCacheData();

    @Override
    public T getCache(Object key) {
        if (key != null) {
            cachePrepare(key);
            return cacheManager().getCacheObj(getCacheName(), key, entityClass);
        } else {
            return null;
        }

    }

    public void cachePrepare(Object key) {
        synchronized (DynamicMemoryCache.class) {
            if (cacheManager().isUninitializedCache(getCacheName())) {
                init();
            } else if (cacheManager().isExpired(getCacheName(), key)) {
                refresh();
            }
        }
    }

    @Override
    public void refresh() {
        Cache cache = cacheManager().getCache(getCacheName());
        if (cache != null) {
            cache.clear();
        }
        init();
    }

    @Override
    public boolean lazyInit() {
        return true;
    }

    @Override
    public void init() {
        synchronized (DynamicMemoryCache.class) {
            Map<String, T> cacheDataMap = getCacheData();
            if (FuncUtil.isNotEmpty(cacheDataMap)) {
                for (Map.Entry<String, T> entry : cacheDataMap.entrySet()) {
                    cacheManager().putCacheObj(this.getCacheName(), entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Override
    public int getExpired() {
        return dynamicMemoryExpiredMinutes;
    }
}
