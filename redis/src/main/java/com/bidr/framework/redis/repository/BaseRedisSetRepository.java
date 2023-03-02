package com.bidr.framework.redis.repository;

import java.util.List;

/**
 * Title: BaseRedisSetRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/2 10:26
 */
public abstract class BaseRedisSetRepository extends BaseRedisRepository {

    public <T> List<T> member(String key, Class<T> clazz) {
        return redisService.getSetMembers(getBaseKey() + key, clazz);
    }

    public Long add(String key, Object value) {
        return redisService.setAdd(getBaseKey() + key, value);
    }

    public Long add(String key, List<Object> value) {
        return redisService.setAdd(getBaseKey() + key, value);
    }

    public Long remove(String key, List<Object> value) {
        return redisService.setRemove(getBaseKey() + key, value);
    }

    public Long remove(String key, Object value) {
        return redisService.setRemove(getBaseKey() + key, value);
    }

    public Long size(String key) {
        return redisService.getSetSize(getBaseKey() + key);
    }

    public Boolean isMember(String key, Object value) {
        return redisService.setIsMember(getBaseKey() + key, value);
    }
}
