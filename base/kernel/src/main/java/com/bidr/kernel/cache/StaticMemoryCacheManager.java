package com.bidr.kernel.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: StaticMemoryCacheManager
 * Description: 静态本地内存缓存管理器（原 diboot 同名类的最小等价实现）。
 * 构造时注册固定 cacheNames，无过期机制。
 * Copyright: Copyright (c) 2024 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2026/09/08
 */
public class StaticMemoryCacheManager extends BaseMemoryCacheManager {

    public StaticMemoryCacheManager() {
        super();
    }

    /**
     * 指定 cacheNames 构造
     *
     * @param cacheNames 缓存名列表
     */
    public StaticMemoryCacheManager(String... cacheNames) {
        List<Cache> caches = new ArrayList<>(cacheNames.length);
        for (String cacheName : cacheNames) {
            caches.add(new ConcurrentMapCache(cacheName));
        }
        setCaches(caches);
        afterPropertiesSet();
    }

    /**
     * 静态缓存不支持过期，不支持清理操作（对齐 diboot：显式抛异常防误用）
     *
     * @param cacheName 缓存名
     */
    @Override
    public void clearOutOfDateData(String cacheName) {
        throw new UnsupportedOperationException("StaticMemoryCacheManager 缓存不支持过期，不支持此操作");
    }
}
