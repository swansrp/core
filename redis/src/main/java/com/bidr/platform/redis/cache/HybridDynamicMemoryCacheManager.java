package com.bidr.platform.redis.cache;

import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.diboot.core.util.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 混合缓存管理器：Redis + 本地内存
 * <p>
 * 读取顺序：本地缓存 -> Redis -> null
 * 写入策略：同时写入本地缓存和 Redis
 * 降级策略：Redis 不可用时自动降级为本地缓存
 * </p>
 *
 * @author Sharp
 * @since 2026/03/26
 */
@Slf4j
public class HybridDynamicMemoryCacheManager extends DynamicMemoryCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String cacheKeyPrefix;
    /**
     * Redis 是否可用
     */
    private volatile boolean redisAvailable = true;
    /**
     * 上次检查 Redis 可用性的时间
     */
    private volatile long lastCheckTime = 0;
    /**
     * Redis 健康检查间隔（毫秒）
     */
    private static final long HEALTH_CHECK_INTERVAL = 30000;

    public HybridDynamicMemoryCacheManager(RedisTemplate<String, Object> redisTemplate, String cacheKeyPrefix) {
        super();
        this.redisTemplate = redisTemplate;
        this.cacheKeyPrefix = cacheKeyPrefix;
        checkRedisAvailable();
    }

    public HybridDynamicMemoryCacheManager(RedisTemplate<String, Object> redisTemplate,
                                           String cacheKeyPrefix,
                                           Map<String, Integer> cacheName2ExpiredMinutes) {
        super(cacheName2ExpiredMinutes);
        this.redisTemplate = redisTemplate;
        this.cacheKeyPrefix = cacheKeyPrefix;
        checkRedisAvailable();
    }

    /**
     * 构建 Redis 缓存 key
     */
    private String buildRedisKey(String cacheName, Object key) {
        return cacheKeyPrefix + ":dynamic:" + cacheName + ":" + key;
    }

    /**
     * 检查 Redis 是否可用
     */
    private void checkRedisAvailable() {
        try {
            redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            if (!redisAvailable) {
                log.info("Redis 缓存已恢复可用");
            }
            redisAvailable = true;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("Redis 缓存不可用，已降级为本地缓存模式: {}", e.getMessage());
            }
            redisAvailable = false;
        }
        lastCheckTime = System.currentTimeMillis();
    }

    /**
     * 定期检查 Redis 可用性
     */
    private void checkRedisAvailableIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime > HEALTH_CHECK_INTERVAL) {
            checkRedisAvailable();
        }
    }

    @Override
    public <T> T getCacheObj(String cacheName, Object objKey, Class<T> tClass) {
        // 先检查本地缓存
        T result = super.getCacheObj(cacheName, objKey, tClass);
        if (result != null) {
            if (log.isDebugEnabled()) {
                log.debug("从本地缓存命中: {}.{}", cacheName, objKey);
            }
            return result;
        }

        // 本地缓存没有，尝试从 Redis 获取
        checkRedisAvailableIfNeeded();
        if (redisAvailable) {
            try {
                String redisKey = buildRedisKey(cacheName, objKey);
                Object value = redisTemplate.opsForValue().get(redisKey);
                if (value != null && tClass.isInstance(value)) {
                    result = tClass.cast(value);
                    // 回填到本地缓存
                    super.putCacheObj(cacheName, objKey, result);
                    if (log.isDebugEnabled()) {
                        log.debug("从 Redis 缓存命中并回填本地: {}.{}", cacheName, objKey);
                    }
                    return result;
                }
            } catch (Exception e) {
                log.warn("从 Redis 读取缓存失败，降级为本地缓存: {}.{} - {}", cacheName, objKey, e.getMessage());
                redisAvailable = false;
            }
        }

        return null;
    }

    @Override
    public void putCacheObj(String cacheName, Object objKey, Object obj) {
        // 写入本地缓存
        super.putCacheObj(cacheName, objKey, obj);

        // 同时写入 Redis
        checkRedisAvailableIfNeeded();
        if (redisAvailable) {
            try {
                String redisKey = buildRedisKey(cacheName, objKey);
                // 获取该缓存的过期时间（分钟）
                Integer expiredMinutes = getCacheExpiredMinutes(cacheName);
                if (expiredMinutes != null && expiredMinutes > 0) {
                    redisTemplate.opsForValue().set(redisKey, obj, expiredMinutes, TimeUnit.MINUTES);
                } else {
                    // 默认 24 小时
                    redisTemplate.opsForValue().set(redisKey, obj, Duration.ofHours(24));
                }
                if (log.isDebugEnabled()) {
                    log.debug("写入 Redis 缓存: {}.{}", cacheName, objKey);
                }
            } catch (Exception e) {
                log.warn("写入 Redis 缓存失败: {}.{} - {}", cacheName, objKey, e.getMessage());
                redisAvailable = false;
            }
        }
    }

    /**
     * 获取缓存的过期时间
     */
    private Integer getCacheExpiredMinutes(String cacheName) {
        try {
            java.lang.reflect.Field field = DynamicMemoryCacheManager.class.getDeclaredField("CACHE_EXPIREDMINUTES_CACHE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> expiredMap = (Map<String, Integer>) field.get(this);
            return expiredMap.get(cacheName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除指定缓存（本地 + Redis）
     */
    @Override
    public void clearOutOfDateData(String cacheName) {
        // 清理本地缓存
        super.clearOutOfDateData(cacheName);

        // Redis 的过期由 Redis 自己管理，不需要手动清理
    }

    /**
     * 判断 Redis 是否可用
     */
    public boolean isRedisAvailable() {
        checkRedisAvailableIfNeeded();
        return redisAvailable;
    }
}
