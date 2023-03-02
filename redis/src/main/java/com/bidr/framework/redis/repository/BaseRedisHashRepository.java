package com.bidr.framework.redis.repository;

/**
 * Title: BaseRedisRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/2 10:12
 */
public abstract class BaseRedisHashRepository<T> extends BaseRedisRepository {

    public void insert(String key, T value) {
        redisService.hashSet(getBaseKey() + key, value);
    }

    public void update(String key, T value) {
        redisService.hashUpdate(getBaseKey() + key, value);
    }

    public void update(String key, String field, String value) {
        redisService.hashSet(getBaseKey() + key, field, value);
    }

    public <T> T get(String key, Class<T> clazz) {
        return redisService.hashGet(getBaseKey() + key, clazz);
    }

}
