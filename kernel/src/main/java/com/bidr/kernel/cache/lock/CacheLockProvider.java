package com.bidr.kernel.cache.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存锁提供者接口
 * <p>
 * 用于控制缓存的初始化和刷新操作，
 * 避免多个线程/实例同时执行 getCacheData() 导致数据库冲突。
 * </p>
 *
 * @author Sharp
 * @since 2026/03/26
 */
public interface CacheLockProvider {

    /**
     * 尝试获取锁
     *
     * @param lockKey   锁的 key
     * @param waitTime  等待时间（毫秒）
     * @param leaseTime 锁持有时间（毫秒）
     * @return 锁令牌（获取失败返回 null），需传给 unlock 方法
     */
    Object tryLock(String lockKey, long waitTime, long leaseTime);

    /**
     * 释放锁
     *
     * @param lockToken tryLock 返回的锁令牌
     */
    void unlock(Object lockToken);

    /**
     * 本地锁默认实现（单机模式使用）
     */
    CacheLockProvider LOCAL = new LocalCacheLockProvider();
}

/**
 * 本地锁实现（基于 JVM 内部的 ReentrantLock）
 */
class LocalCacheLockProvider implements CacheLockProvider {

    private final ConcurrentMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public Object tryLock(String lockKey, long waitTime, long leaseTime) {
        ReentrantLock lock = lockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
        try {
            boolean acquired = lock.tryLock(waitTime, java.util.concurrent.TimeUnit.MILLISECONDS);
            return acquired ? lock : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void unlock(Object lockToken) {
        if (lockToken instanceof ReentrantLock) {
            ReentrantLock lock = (ReentrantLock) lockToken;
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String toString() {
        return "LocalCacheLockProvider";
    }
}
