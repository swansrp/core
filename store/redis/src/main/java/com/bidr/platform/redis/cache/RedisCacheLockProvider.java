package com.bidr.platform.redis.cache;

import com.bidr.kernel.cache.lock.CacheLockProvider;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁实现
 * <p>
 * 参考 {@link com.bidr.platform.redis.aop.redisson.RedissonLockAspect} 的实现方式，
 * tryLock 返回 RLock 实例作为锁令牌，unlock 接收同一个实例进行释放。
 *
 * @author Sharp
 * @since 2026/03/26
 */
@Slf4j
public class RedisCacheLockProvider implements CacheLockProvider {

    private static final String LOCK_KEY_PREFIX = "cache:lock:";

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private volatile boolean available = false;

    public RedisCacheLockProvider(RedissonClient redissonClient, String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = (keyPrefix != null && !keyPrefix.isEmpty()) ? keyPrefix + ":" : "";
        checkAvailable();
    }

    private void checkAvailable() {
        try {
            redissonClient.getNodesGroup().pingAll();
            available = true;
            log.info("RedisCacheLockProvider 初始化成功，分布式锁已启用");
        } catch (Exception e) {
            available = false;
            log.warn("RedisCacheLockProvider 初始化失败，分布式锁不可用: {}", e.getMessage());
        }
    }

    @Override
    public Object tryLock(String lockKey, long waitTime, long leaseTime) {
        if (!available) {
            return null;
        }
        try {
            String fullKey = keyPrefix + LOCK_KEY_PREFIX + lockKey;
            RLock lock = redissonClient.getLock(fullKey);
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
            if (acquired) {
                if (log.isDebugEnabled()) {
                    log.debug("成功获取分布式锁: {}", lock.getName());
                }
                // 返回 RLock 实例作为锁令牌
                return lock;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("获取分布式锁失败: {}", fullKey);
                }
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断: {}", lockKey);
            return null;
        } catch (Exception e) {
            log.warn("获取分布式锁异常: {} - {}", lockKey, e.getMessage());
            return null;
        }
    }

    @Override
    public void unlock(Object lockToken) {
        if (!(lockToken instanceof RLock)) {
            return;
        }
        RLock lock = (RLock) lockToken;
        try {
            // 直接删除锁 key，完全绕开 Redisson 的 lock API（unlock/forceUnlock/isHeldByCurrentThread）
            // 因为所有 lock API 都会访问 redisson_lock__channel，需要额外 ACL 权限
            // 调用方已确保只有锁持有者才会调用 unlock，所以这里直接删除
            String lockName = lock.getName();
            log.debug("释放分布式锁: {}", lockName);
            redissonClient.getKeys().delete(lockName);
        } catch (Exception e) {
            log.warn("释放分布式锁异常: {}", e.getMessage(), e);
        }
    }
}
