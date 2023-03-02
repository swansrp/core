package com.bidr.platform.redis.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

/**
 * @author sharp
 */
@Slf4j
public class MyRedisCache extends RedisCache {

    private final String name;
    private final RedisCacheWriter cacheWriter;

    protected MyRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig) {
        super(name, cacheWriter, cacheConfig);
        this.cacheWriter = cacheWriter;
        this.name = name;
        // TODO Auto-generated constructor stub
    }

    @Override
    protected Object lookup(Object key) {
        byte[] value = cacheWriter.get(name, createAndConvertCacheKey(key));
        Object res = null;
        if (value != null) {
            res = deserializeCacheValue(value);
            put(key, res);
        }
        return res;
    }

    private byte[] createAndConvertCacheKey(Object key) {
        return serializeCacheKey(createCacheKey(key));
    }

    @Override
    public void evict(Object key) {
        cacheWriter.clean(name, createAndConvertCacheKey(key));
    }
}
