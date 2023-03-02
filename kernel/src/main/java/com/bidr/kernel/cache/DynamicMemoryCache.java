package com.bidr.kernel.cache;

import com.bidr.kernel.utils.ReflectionUtil;
import com.diboot.core.cache.DynamicMemoryCacheManager;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;

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

    public T getCache(Object key) {
        if (key != null) {
            if (dynamicMemoryCacheManager.isExpired(getCacheName(), key)) {
                refresh();
                return getCacheExpired(key);
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

    /**
     * 过期处理办法
     *
     * @param key
     * @return
     */

    protected abstract T getCacheExpired(Object key);
}
