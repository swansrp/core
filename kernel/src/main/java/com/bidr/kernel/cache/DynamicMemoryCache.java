package com.bidr.kernel.cache;

import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.cache.exception.DynamicMemoryCacheExpiredException;
import com.bidr.kernel.cache.lock.CacheLockProvider;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Title: DynamicMemoryCache
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/02/17 11:49
 */
@SuppressWarnings("unchecked")
@Slf4j
public abstract class DynamicMemoryCache<T> implements DynamicMemoryCacheInf<T> {

    /**
     * 锁等待时间（毫秒）
     */
    private static final long LOCK_WAIT_TIME = 5000;
    /**
     * 锁持有时间（毫秒）
     */
    private static final long LOCK_LEASE_TIME = 60000;
    private final Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    /**
     * 实例级别锁，避免不同缓存实例互相阻塞
     */
    private final Object lock = new Object();
    @Lazy
    @Resource
    protected DynamicMemoryCacheManager dynamicMemoryCacheManager;
    @Value("${my.cache.expired}")
    private Integer dynamicMemoryExpiredMinutes;

    @Retryable(value = DynamicMemoryCacheExpiredException.class, maxAttempts = 2)
    public Map<String, T> getAllCache() {
        ConcurrentMapCache cache = (ConcurrentMapCache) dynamicMemoryCacheManager.getCache(getCacheName());
        if (cache != null && FuncUtil.isEmpty(cache.getNativeCache())) {
            refreshWithLock();
        }
        return JsonUtil.readJson(cache.getNativeCache(), Map.class, String.class, entityClass);
    }

    public DynamicMemoryCacheManager cacheManager() {
        return dynamicMemoryCacheManager;
    }

    /**
     * 获取缓存数据列表（不写库）
     */
    protected abstract Map<String, T> getCacheData();

    /**
     * 获取缓存数据列表
     *
     * @param init true=初始化，false=刷新时
     * @return 缓存数据
     */
    protected Map<String, T> getCacheData(boolean init) {
        return getCacheData();
    }

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
        // 快速路径：无需刷新时直接返回
        if (!cacheManager().isUninitializedCache(getCacheName())
                && !cacheManager().isExpired(getCacheName(), key)) {
            return;
        }
        synchronized (lock) {
            // 双重检查：防止多个线程同时进入锁等待后重复执行
            if (cacheManager().isUninitializedCache(getCacheName())) {
                // 首次访问触发初始化，需要写库
                initWithLock(true);
            } else if (cacheManager().isExpired(getCacheName(), key)) {
                refreshWithLock();
            }
        }
    }

    @Override
    public void refresh() {
        refreshWithLock();
    }

    /**
     * 带同步锁的刷新方法（不写库）
     */
    private void refreshWithLock() {
        synchronized (lock) {
            Cache cache = cacheManager().getCache(getCacheName());
            if (cache != null) {
                cache.clear();
            }
            // 刷新时不写库
            initWithLock(false);
        }
    }

    @Override
    public boolean lazyInit() {
        return true;
    }

    @Override
    public void init() {
        // 初始化时写库
        initWithLock(true);
    }

    /**
     * 带锁的初始化方法
     *
     * @param init true=初始化，false=刷新时
     */
    private void initWithLock(boolean init) {
        synchronized (lock) {
            CacheLockProvider lockProvider = cacheManager().getLockProvider();
            String lockKey = "cache:init:" + getCacheName();
            Object lockToken = null;
            int retryCount = 0;
            int maxRetry = 3;
            try {
                while (retryCount < maxRetry) {
                    lockToken = lockProvider.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
                    if (lockToken != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("获取锁成功[{}], 开始{}缓存: {}",
                                    lockProvider.getClass().getSimpleName(),
                                    init ? "初始化" : "刷新",
                                    getCacheName());
                        }
                        doInit(init);
                        return;
                    } else {
                        // 未获取到锁，说明其他实例/线程正在初始化，等待后检查缓存是否已初始化
                        retryCount++;
                        if (log.isDebugEnabled()) {
                            log.debug("未获取到锁[{}], 其他实例正在初始化缓存: {}, 第{}次重试",
                                    lockProvider.getClass().getSimpleName(), getCacheName(), retryCount);
                        }
                        Thread.sleep(1000);
                        // 检查缓存是否已被其他实例初始化完成
                        if (!cacheManager().isUninitializedCache(getCacheName())) {
                            if (log.isDebugEnabled()) {
                                log.debug("缓存已被其他实例初始化: {}", getCacheName());
                            }
                            return;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待锁被中断: {}", getCacheName());
            } finally {
                if (lockToken != null) {
                    try {
                        lockProvider.unlock(lockToken);
                        if (log.isDebugEnabled()) {
                            log.debug("释放锁[{}]: {}", lockProvider.getClass().getSimpleName(), getCacheName());
                        }
                    } catch (Exception e) {
                        log.warn("释放锁失败: {} - {}", getCacheName(), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 执行实际的初始化逻辑
     *
     * @param init true=初始化，false=刷新时
     */
    private void doInit(boolean init) {
        Map<String, T> cacheDataMap = getCacheData(init);
        if (FuncUtil.isNotEmpty(cacheDataMap)) {
            for (Map.Entry<String, T> entry : cacheDataMap.entrySet()) {
                cacheManager().putCacheObj(this.getCacheName(), entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public int getExpired() {
        return dynamicMemoryExpiredMinutes;
    }
}
