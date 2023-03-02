package com.bidr.framework.redis.repository;

import com.bidr.framework.redis.service.RedisService;

import javax.annotation.Resource;

/**
 * Title: BaseRedisRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/2 11:58
 */
public abstract class BaseRedisRepository {
    @Resource
    protected RedisService redisService;

    public Boolean existed(String key) {
        return redisService.hasKey(getBaseKey() + key);
    }

    protected abstract String getBaseKey();

    public Boolean expire(String key, long seconds) {
        return redisService.expire(getBaseKey() + key, seconds);
    }

    public Boolean delete(String key) {
        return redisService.delete(getBaseKey() + key);
    }

}
