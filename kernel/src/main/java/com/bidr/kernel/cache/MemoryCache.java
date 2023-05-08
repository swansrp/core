package com.bidr.kernel.cache;

import com.diboot.core.cache.StaticMemoryCacheManager;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;

/**
 * Title: MemoryCache
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/02/17 10:41
 */
public abstract class MemoryCache<T> implements MemoryCacheInf<T> {

    @Lazy
    @Resource
    protected StaticMemoryCacheManager staticMemoryCacheManager;

    @Override
    public void refresh() {
        Cache cache = staticMemoryCacheManager.getCache(getCacheName());
        if (cache != null) {
            cache.clear();
        }
        init();
    }

}
