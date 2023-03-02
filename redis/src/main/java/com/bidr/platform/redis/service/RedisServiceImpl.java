package com.bidr.platform.redis.service;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisServiceImpl implements RedisService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Set<String> keys(String prefix) {
        return keysByPattern(prefix + "*");
    }

    @Override
    public Set<String> keysByPattern(String pattern) {
        return redisTemplate.keys(pattern);
    }

    @Override
    public void set(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, int seconds, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    @Override

    public Boolean setnx(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    @Override
    public Boolean setnx(String key, int seconds, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForValue().setIfAbsent(key, value, seconds, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<?> collectionClass, Class<?>... elementClasses) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForValue().get(key);
        return JsonUtil.readJson(res, collectionClass, elementClasses);

    }

    @Override
    public Long incr(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    public Long incr(String key, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(key, delta);
    }

    @Override
    public Double incr(String key, Double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(key, delta);
    }

    @Override
    public Long decr(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().decrement(key);
    }

    @Override
    public Long decr(String key, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    @Override
    public Set<String> getKeys(String pattern) {
        Validator.assertNotBlank(pattern, ErrCodeSys.PA_DATA_NOT_EXIST, "pattern");
        return redisTemplate.keys(pattern);
    }

    @Override
    public Boolean delete(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.delete(key);
    }

    @Override
    public Boolean hasKey(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.hasKey(key);
    }

    @Override
    public Long delete(List<String> keys) {
        Validator.assertNotEmpty(keys, ErrCodeSys.PA_DATA_NOT_EXIST, "keys");
        return redisTemplate.delete(keys);
    }

    @Override
    public Boolean setExpireTime(String key, Date expireDate) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(expireDate, ErrCodeSys.PA_DATA_NOT_EXIST, "expireDate");
        return redisTemplate.expireAt(key, expireDate);
    }

    @Override
    public Boolean expire(String key, long timeout) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.expire(key, timeout, timeUnit);
    }

    @Override
    public void hashSet(String key, String field, String value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        Validator.assertNotBlank(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().put(key, field, value);
    }

    @Override
    public void hashSet(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().putAll(key, ReflectionUtil.getHashMap(value));
    }

    @Override
    public void hashSet(String key, Map<String, Object> map) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(map, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().putAll(key, map);
    }

    @Override
    public void hashUpdate(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        Map<String, Object> map = ReflectionUtil.getHashMap(value);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                redisTemplate.opsForHash().put(key, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Long hashDel(String key, String... fields) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(fields, ErrCodeSys.PA_DATA_NOT_EXIST, "fields");
        return redisTemplate.opsForHash().delete(key, fields);
    }

    @Override
    public Map<String, Object> hashGet(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        Map<String, Object> res = new HashMap<>(map.size());
        if (MapUtils.isNotEmpty(map)) {
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                res.put(StringUtil.parse(entry.getKey()), entry.getValue());
            }
        }
        return res;
    }

    @Override
    public <T> T hashGet(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        List<Field> fields = ReflectionUtil.getFields(clazz);
        Map<String, Object> map = new HashMap<>(fields.size());
        boolean update = false;
        for (Field field : fields) {
            Object res = redisTemplate.opsForHash().get(key, field.getName());
            if (res != null) {
                map.put(field.getName(), res);
                update = true;
            }
        }
        return JsonUtil.readJson(update ? map : null, clazz);

    }

    @Override
    public <T> T hashGet(String key, String field, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        Object res = redisTemplate.opsForHash().get(key, field);
        return JsonUtil.readJson(res, clazz);

    }

    @Override
    public Long hashIncr(String key, String field, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    @Override
    public Double hashIncrDouble(String key, String field, Double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    @Override
    public Long leftPush(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().leftPush(key, value);
    }

    @Override
    public Long leftPush(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().leftPushAll(key, value);
    }

    @Override
    public Long rightPush(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");

        return redisTemplate.opsForList().rightPush(key, value);
    }

    @Override
    public Long rightPush(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().rightPushAll(key, value);
    }

    @Override
    public <T> T leftPop(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().leftPop(key);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T leftBlockingPop(String key, int seconds, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().leftPop(key, seconds, TimeUnit.SECONDS);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T rightPop(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().rightPop(key);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T rightBlockingPop(String key, int seconds, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().rightPop(key, seconds, TimeUnit.SECONDS);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> List<T> getSetMembers(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<?> res = redisTemplate.opsForSet().members(key);
        return ReflectionUtil.copyList(res, clazz);
    }

    @Override
    public Long setAdd(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().add(key, value.toArray());
    }

    @Override
    public Long setAdd(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().add(key, value);
    }

    @Override
    public Long setRemove(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().remove(key, value.toArray());
    }

    @Override
    public Long setRemove(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().remove(key, value);
    }

    @Override
    public <T> T getSetRandMember(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForSet().randomMember(key);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public Long getSetSize(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForSet().size(key);
    }

    @Override
    public Boolean setIsMember(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForSet().isMember(key, value);
    }

    @Override
    public Boolean zSetAdd(String key, Object value, double score) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    @Override
    public Long zSetRemoveBySet(String key, Set<?> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().remove(key, value.toArray());
    }

    @Override
    public Long zSetRemove(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().remove(key, value);
    }

    @Override
    public Double zSetIncrScore(String key, Object value, double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    @Override
    public Double zSetScore(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().score(key, value);
    }

    @Override
    public Long zSetRank(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().rank(key, value);
    }

    @Override
    public <T> Set<T> zSetRange(String key, long start, long end, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().range(key, start, end);
        return ReflectionUtil.copySet(res, clazz);
    }

    @Override
    public <T> Set<T> zSetRangeByScore(String key, double min, double max, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().rangeByScore(key, min, max);
        return JsonUtil.readJson(res, Set.class, clazz);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScore(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScoreByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().rangeByScoreWithScores(key, min, max);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScore(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScoreByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, min, max);
    }

    @Override
    public <T> Set<T> zSetRevRange(String key, long start, long end, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().reverseRange(key, start, end);
        return ReflectionUtil.copySet(res, clazz);
    }

    @Override
    public Long zSetRemoveRange(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().removeRange(key, start, end);
    }

    @Override
    public Long zSetRemoveByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    @Override
    public Long zSetSize(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().size(key);
    }

    @Override
    public boolean zSetIsMember(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, key);
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        Long res = redisTemplate.opsForZSet().rank(key, value);
        return !(res == null || res <= 0);
    }

    @Override
    public Long zSetUnion(List<String> keyList, String destKey) {
        Validator.assertNotEmpty(keyList, ErrCodeSys.PA_DATA_NOT_EXIST, "keyList");
        String firstKey = keyList.get(0);
        keyList.remove(0);
        return redisTemplate.opsForZSet().unionAndStore(firstKey, keyList, destKey);
    }

    @Override
    public Long zSetIntersect(List<String> keyList, String destKey) {
        Validator.assertNotEmpty(keyList, ErrCodeSys.PA_DATA_NOT_EXIST, "keyList");
        String firstKey = keyList.get(0);
        keyList.remove(0);
        return redisTemplate.opsForZSet().intersectAndStore(firstKey, keyList, destKey);
    }

    @Override
    public void publish(String topic, Object value) {
        Validator.assertNotBlank(topic, ErrCodeSys.PA_DATA_NOT_EXIST, "topic");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.convertAndSend(topic, value);
    }

    @Override
    public void subscribe(MessageListener listener, String topic) {
        Validator.assertNotBlank(topic, ErrCodeSys.PA_DATA_NOT_EXIST, "topic");
        redisTemplate.execute((RedisCallback<String>) connection -> {
            connection.subscribe(listener, topic.getBytes());
            return null;
        });
    }
}
