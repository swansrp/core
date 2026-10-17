package com.bidr.platform.redis.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Title: MyRedisCacheManager
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/8/12 10:55
 */

@Slf4j
public class MyRedisCacheManager extends RedisCacheManager {

    private final RedisCacheWriter cacheWriter;

    private final RedisCacheConfiguration defaultCacheConfig;

    public MyRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        super(cacheWriter, defaultCacheConfiguration);
        this.cacheWriter = cacheWriter;
        this.defaultCacheConfig = defaultCacheConfiguration;
        // TODO Auto-generated constructor stub
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
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
        String[] array = StringUtils.delimitedListToStringArray(name, "#");
        name = array[0];
        if (array.length > 1) {
            long ttl = Long.parseLong(array[1]);
            cacheConfig = cacheConfig.entryTtl(Duration.ofSeconds(ttl));
        }
        return new MyRedisCache(name, cacheWriter, cacheConfig != null ? cacheConfig : defaultCacheConfig);
    }
}
