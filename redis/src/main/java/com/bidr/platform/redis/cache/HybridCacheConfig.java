package com.bidr.platform.redis.cache;

import com.bidr.kernel.cache.DynamicMemoryCacheInf;
import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.cache.config.MemoryCacheConfig;
import com.bidr.kernel.cache.lock.CacheLockProvider;
import com.bidr.platform.redis.config.RedissonConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合缓存配置类
 * <p>
 * 当 Redis 可用时，使用 HybridDynamicMemoryCacheManager（Redis + 本地内存）
 * 当 Redis 不可用时，使用基础的 DynamicMemoryCacheManager（仅本地内存）
 * </p>
 *
 * @author Sharp
 * @since 2026/03/26
 */
@Slf4j
@Configuration
@AutoConfigureBefore(MemoryCacheConfig.class)
@AutoConfigureAfter(RedissonConfig.class)
public class HybridCacheConfig {

    @Autowired(required = false)
    private List<DynamicMemoryCacheInf<?>> dynamicMemoryCacheList;

    @Value("${app.projectId:}")
    private String projectId;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * 创建分布式锁提供者
     * 注意：不使用 @ConditionalOnBean，因为它在自动配置阶段可能不可靠
     */
    @Bean
    @ConditionalOnMissingBean(CacheLockProvider.class)
    public CacheLockProvider redisCacheLockProvider(
            @Autowired(required = false) RedissonClient redissonClient) {
        if (redissonClient == null) {
            log.info("RedissonClient 不可用，使用默认本地锁");
            return CacheLockProvider.LOCAL;
        }
        log.info("检测到 RedissonClient，创建分布式锁提供者");
        return new RedisCacheLockProvider(redissonClient);
    }

    /**
     * 当 Redis 可用时，创建混合缓存管理器
     * 注意：不使用 @ConditionalOnBean，因为它在自动配置阶段可能不可靠
     * 使用 @ConditionalOnMissingBean 确保只创建一个 DynamicMemoryCacheManager
     */
    @Bean
    @ConditionalOnMissingBean(DynamicMemoryCacheManager.class)
    public DynamicMemoryCacheManager hybridDynamicMemoryCacheManager(
            @Autowired(required = false) RedisTemplate<String, Object> objectRedisTemplate,
            @Autowired(required = false) CacheLockProvider lockProvider) {
        if (objectRedisTemplate == null) {
            log.info("RedisTemplate 不可用，跳过混合缓存模式");
            return null;
        }

        Map<String, Integer> dynamicCaches = new HashMap<>();
        if (!CollectionUtils.isEmpty(dynamicMemoryCacheList)) {
            for (DynamicMemoryCacheInf<?> cache : dynamicMemoryCacheList) {
                dynamicCaches.put(cache.getCacheName(), cache.getExpired());
            }
        }

        String keyPrefix = buildKeyPrefix();
        HybridDynamicMemoryCacheManager manager = new HybridDynamicMemoryCacheManager(objectRedisTemplate, keyPrefix, dynamicCaches);

        if (lockProvider != null) {
            manager.setLockProvider(lockProvider);
            log.info("启用 Redis + 本地内存混合缓存模式，key前缀: {}, 分布式锁: {} ({})",
                    keyPrefix, "已启用", lockProvider.getClass().getSimpleName());
        } else {
            log.warn("启用 Redis + 本地内存混合缓存模式，key前缀: {}, 分布式锁: 未启用（使用默认本地锁）", keyPrefix);
        }

        return manager;
    }

    /**
     * 延迟将 CacheLockProvider 设置到 DynamicMemoryCacheManager
     * 解决组件扫描阶段创建的 DynamicMemoryCacheManager 锁提供者为本地锁的问题
     */
    @Bean
    public SmartInitializingSingleton cacheLockProviderInjector(
            @Autowired(required = false) DynamicMemoryCacheManager dynamicMemoryCacheManager,
            @Autowired(required = false) CacheLockProvider lockProvider) {
        return () -> {
            if (dynamicMemoryCacheManager == null || lockProvider == null) {
                return;
            }
            if (dynamicMemoryCacheManager.getLockProvider() == CacheLockProvider.LOCAL
                    && lockProvider != CacheLockProvider.LOCAL) {
                dynamicMemoryCacheManager.setLockProvider(lockProvider);
                log.info("延迟设置锁提供者: LocalCacheLockProvider -> {}",
                        lockProvider.getClass().getSimpleName());
            }
        };
    }

    /**
     * 构建缓存 key 前缀
     */
    private String buildKeyPrefix() {
        return projectId;
    }
}
