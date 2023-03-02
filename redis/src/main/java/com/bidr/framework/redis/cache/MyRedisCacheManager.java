package com.bidr.framework.redis.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

import java.time.Duration;
import java.util.*;

/**
 * Title: MyRedisCacheManager
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/8/12 10:55
 */

@Slf4j
public class MyRedisCacheManager extends RedisCacheManager implements ApplicationContextAware, InitializingBean {

    private final RedisCacheWriter cacheWriter;

    private final RedisCacheConfiguration defaultCacheConfig;

    private ApplicationContext applicationContext;

    private final Map<String, RedisCacheConfiguration> initialCacheConfiguration = new LinkedHashMap<>();

    public MyRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        super(cacheWriter, defaultCacheConfiguration);
        this.cacheWriter = cacheWriter;
        this.defaultCacheConfig = defaultCacheConfiguration;
        // TODO Auto-generated constructor stub
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // TODO Auto-generated method stub
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            final Class clazz = applicationContext.getType(beanName);
            add(clazz);
        }
        super.afterPropertiesSet();
    }

    private void add(final Class clazz) {
        ReflectionUtils.doWithMethods(clazz, method -> {
            ReflectionUtils.makeAccessible(method);
            CacheExpire cacheExpire = AnnotationUtils.findAnnotation(method, CacheExpire.class);
            if (cacheExpire == null) {
                return;
            }
            Cacheable cacheable = AnnotationUtils.findAnnotation(method, Cacheable.class);
            if (cacheable != null) {
                add(cacheable.cacheNames(), cacheExpire);
            }
            CacheConfig cacheConfig = AnnotationUtils.findAnnotation(clazz, CacheConfig.class);
            if (cacheConfig != null) {
                add(cacheConfig.cacheNames(), cacheExpire);
            }
        }, method -> null != AnnotationUtils.findAnnotation(method, CacheExpire.class));
    }

    private void add(String[] cacheNames, CacheExpire cacheExpire) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        RedisSerializationContext.SerializationPair<Object> pair =
                RedisSerializationContext.SerializationPair.fromSerializer(
                serializer);
        for (String cacheName : cacheNames) {
            if (cacheName == null || "".equals(cacheName.trim())) {
                continue;
            }
            long expire = cacheExpire.expire();
            log.info("add cache config: {}-{}", cacheName, cacheExpire.expire());
            if (expire >= 0) {
                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(expire)).serializeValuesWith(pair);
                initialCacheConfiguration.put(cacheName, config);
            }
        }
    }

    @Override
    protected Collection<RedisCache> loadCaches() {
        List<RedisCache> caches = new LinkedList<>();
        for (Map.Entry<String, RedisCacheConfiguration> entry : initialCacheConfiguration.entrySet()) {
            caches.add(createRedisCache(entry.getKey(), entry.getValue()));
        }
        return caches;
    }

    /**
     * Configuration hook for creating {@link RedisCache} with given name and
     * {@code cacheConfig}.
     *
     * @param name        must not be {@literal null}.
     * @param cacheConfig can be {@literal null}.
     * @return never {@literal null}.
     */
    @Override
    protected RedisCache createRedisCache(String name, @Nullable RedisCacheConfiguration cacheConfig) {
        return new MyRedisCache(name, cacheWriter, cacheConfig != null ? cacheConfig : defaultCacheConfig);
    }
}
