package com.bidr.kernel.cache;

import com.bidr.kernel.cache.exception.DynamicMemoryCacheExpiredException;
import com.bidr.kernel.utils.ReflectionUtil;
import com.diboot.core.cache.DynamicMemoryCacheManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Title: DynamicMemoryCache
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/02/17 11:49
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
    public List<T> getAllCache() {
        List<T> resList = new ArrayList<>();
        Collection<String> cacheNames = dynamicMemoryCacheManager.getCacheNames();
        if (CollectionUtils.isNotEmpty(cacheNames)) {
            for (String key : cacheNames) {
                if (dynamicMemoryCacheManager.isExpired(getCacheName(), key)) {
                    refresh();
                    throw new DynamicMemoryCacheExpiredException();
                } else {
                    resList.add(dynamicMemoryCacheManager.getCacheObj(getCacheName(), key, entityClass));
                }
            }
        }
        return resList;
    }

    /**
     * 获取缓存数据列表
     *
     * @return
     */
    protected abstract Collection<T> getCacheData();

    /**
     * 获取缓存id
     *
     * @param obj
     * @return
     */
    protected abstract Object getCacheKey(T obj);

    @Retryable(value = DynamicMemoryCacheExpiredException.class, maxAttempts = 2)
    public T getCache(Object key) {
        if (key != null) {
            if (dynamicMemoryCacheManager.isExpired(getCacheName(), key)) {
                refresh();
                throw new DynamicMemoryCacheExpiredException();
            } else {
                return dynamicMemoryCacheManager.getCacheObj(getCacheName(), key, entityClass);
            }
        } else {
            return null;
        }
    }

    @Override
    public void refresh() {
        Cache cache = dynamicMemoryCacheManager.getCache(getCacheName());
        if (cache != null) {
            cache.clear();
        }
        init();
    }

    @Override
    public void init() {
        synchronized (DynamicMemoryCache.class) {
            Collection<T> cacheDataList = getCacheData();
            if (CollectionUtils.isNotEmpty(cacheDataList)) {
                for (T cacheData : cacheDataList) {
                    dynamicMemoryCacheManager.putCacheObj(this.getCacheName(), getCacheKey(cacheData), cacheData);
                }
            }
        }
    }

    @Override
    public int getExpired() {
        return dynamicMemoryExpiredMinutes;
    }
}
