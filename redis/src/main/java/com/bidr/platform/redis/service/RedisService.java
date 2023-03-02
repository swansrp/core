package com.bidr.platform.redis.service;

import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface RedisService {

    Set<String> keys(String prefix);

    Set<String> keysByPattern(String pattern);

    void set(String key, Object value);

    void set(String key, int seconds, Object value);

    Boolean setnx(String key, Object value);

    Boolean setnx(String key, int seconds, Object value);

    <T> T get(String key, Class<?> collectionClass, Class<?>... elementClasses);

    Long incr(String key);

    Long incr(String key, long delta);

    Double incr(String key, Double delta);

    Long decr(String key);

    Long decr(String key, long delta);

    Set<String> getKeys(String pattern);

    Boolean delete(String key);

    Boolean hasKey(String key);

    Long delete(List<String> keys);

    Boolean setExpireTime(String key, Date expireDate);

    Boolean expire(String key, long timeout);

    Boolean expire(String key, long timeout, TimeUnit timeUnit);

    void hashSet(String key, String field, String value);

    void hashSet(String key, Object value);

    void hashSet(String key, Map<String, Object> map);

    void hashUpdate(String key, Object value);

    Long hashDel(String key, String... fields);

    Map<String, Object> hashGet(String key);

    <T> T hashGet(String key, Class<T> clazz);

    <T> T hashGet(String key, String field, Class<T> clazz);

    Long hashIncr(String key, String field, long number);

    Double hashIncrDouble(String key, String field, Double number);

    Long leftPush(String key, Object value);

    Long leftPush(String key, List<Object> value);

    Long rightPush(String key, Object value);

    Long rightPush(String key, List<Object> value);

    <T> T leftPop(String key, Class<T> clazz);

    <T> T leftBlockingPop(String key, int seconds, Class<T> clazz);

    <T> T rightPop(String key, Class<T> clazz);

    <T> T rightBlockingPop(String key, int seconds, Class<T> clazz);

    <T> List<T> getSetMembers(String key, Class<T> clazz);

    Long setAdd(String key, List<Object> value);

    Long setAdd(String key, Object value);

    Long setRemove(String key, List<Object> value);

    Long setRemove(String key, Object value);

    <T> T getSetRandMember(String key, Class<T> clazz);

    Long getSetSize(String key);

    Boolean setIsMember(String key, Object value);

    Boolean zSetAdd(String key, Object value, double score);

    Long zSetRemoveBySet(String key, Set<?> value);

    Long zSetRemove(String key, Object value);

    Double zSetIncrScore(String key, Object value, double delta);

    Double zSetScore(String key, Object value);

    Long zSetRank(String key, Object value);

    <T> Set<T> zSetRange(String key, long start, long end, Class<T> clazz);

    <T> Set<T> zSetRangeByScore(String key, double min, double max, Class<T> clazz);

    Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScore(String key, long start, long end);

    Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScoreByScore(String key, double min, double max);

    Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScore(String key, long start, long end);

    Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScoreByScore(String key, double min, double max);

    <T> Set<T> zSetRevRange(String key, long start, long end, Class<T> clazz);

    Long zSetRemoveRange(String key, long start, long end);

    Long zSetRemoveByScore(String key, double min, double max);

    Long zSetSize(String key);

    boolean zSetIsMember(String key, Object value);

    Long zSetUnion(List<String> keyList, String destKey);

    Long zSetIntersect(List<String> keyList, String destKey);

    void publish(final String topic, final Object message);

    void subscribe(final MessageListener listener, final String topic);
}
