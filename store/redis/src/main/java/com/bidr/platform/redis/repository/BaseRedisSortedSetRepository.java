package com.bidr.platform.redis.repository;

import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

/**
 * Title: BaseRedisSortedSetRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/7/2 11:56
 */
public abstract class BaseRedisSortedSetRepository extends BaseRedisRepository {


    public Boolean add(String key, Object value, double score) {
        return redisService.zSetAdd(getBaseKey() + key, value, score);
    }

    public Long removeBySet(String key, Set<?> value) {
        return redisService.zSetRemoveBySet(getBaseKey() + key, value);
    }

    public Long remove(String key, Object value) {
        return redisService.zSetRemove(getBaseKey() + key, value);
    }

    public Long size(String key) {
        return redisService.zSetSize(getBaseKey() + key);
    }

    public Boolean isMember(String key, Object value) {
        return redisService.zSetIsMember(getBaseKey() + key, value);
    }

    public Double incr(String key, Object value, double score) {
        return redisService.zSetIncrScore(getBaseKey() + key, value, score);
    }

    public Long rank(String key, Object value) {
        return redisService.zSetRank(getBaseKey() + key, value);
    }

    public <T> Set<T> getRange(String key, long start, long end, Class<T> clazz) {
        return redisService.zSetRange(getBaseKey() + key, start, end, clazz);
    }

    public <T> Set<T> getRevRange(String key, long start, long end, Class<T> clazz) {
        return redisService.zSetRevRange(getBaseKey() + key, start, end, clazz);
    }

    public <T> Set<T> getRangeByScore(String key, double min, double max, Class<T> clazz) {
        return redisService.zSetRangeByScore(getBaseKey() + key, min, max, clazz);
    }

    public Long removeRange(String key, long start, long end) {
        return redisService.zSetRemoveRange(getBaseKey() + key, start, end);
    }

    public Long removeByScore(String key, double min, double max) {
        return redisService.zSetRemoveByScore(getBaseKey() + key, min, max);
    }

    public Set<ZSetOperations.TypedTuple<Object>> getRangeWithScoreByScore(String key, double min, double max) {
        return redisService.zSetRangeWithScoreByScore(getBaseKey() + key, min, max);
    }

    public Set<ZSetOperations.TypedTuple<Object>> getRangeWithScoreByRange(String key, long start, long end) {
        return redisService.zSetRangeWithScore(getBaseKey() + key, start, end);
    }
}
