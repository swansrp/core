package com.bidr.platform.redis.service;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.cache.RedisCacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisServiceImpl implements RedisService {
    private static final Long SCAN_SIZE = 1000L;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Set<String> keys(String prefix) {
        Validator.assertNotBlank(prefix, ErrCodeSys.PA_DATA_NOT_EXIST, "prefix");
        String appPrefix = appPrefix();
        Set<String> keys = new HashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(appPrefix + prefix + "*").count(SCAN_SIZE).build();
        Cursor<String> cursor = redisTemplate.scan(scanOptions);
        while (cursor.hasNext()) {
            keys.add(stripAppPrefix(cursor.next(), appPrefix));
        }
        cursor.close();
        return keys;
    }

    @Override
    public Set<String> keysByPattern(String pattern) {
        Validator.assertNotBlank(pattern, ErrCodeSys.PA_DATA_NOT_EXIST, "pattern");
        String appPrefix = appPrefix();
        return logicalKeys(redisTemplate.keys(appPrefix + pattern), appPrefix);
    }

    @Override
    public void set(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForValue().set(getKey(key), value);
    }

    public String getKey(String key) {
        return RedisCacheConfig.getKey(key);
    }

    /**
     * 🔴 键命名空间只有"逻辑键"一种口径：读写一律经 {@link #getKey(String)} 补 app.projectId 前缀，
     * 故枚举类方法也必须<b>以补前缀的串扫描、把前缀剥掉再返回</b>——调用方拿到的键可以直接再喂回本服务。
     * 历史缺陷两条：扫描串裸传 ⇒ 恒扫不到；返回物理键 ⇒ 喂回来二次加前缀（会话枚举恒空、对话列表读空）。
     */
    private String appPrefix() {
        return RedisCacheConfig.getKey("");
    }

    private String stripAppPrefix(String physicalKey, String appPrefix) {
        return physicalKey.startsWith(appPrefix) ? physicalKey.substring(appPrefix.length()) : physicalKey;
    }

    private Set<String> logicalKeys(Set<String> physicalKeys, String appPrefix) {
        if (physicalKeys == null || physicalKeys.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> keys = new HashSet<>(physicalKeys.size());
        for (String physicalKey : physicalKeys) {
            keys.add(stripAppPrefix(physicalKey, appPrefix));
        }
        return keys;
    }

    @Override
    public void set(String key, int seconds, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForValue().set(getKey(key), value, seconds, TimeUnit.SECONDS);
    }

    @Override

    public Boolean setnx(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForValue().setIfAbsent(getKey(key), value);
    }

    @Override
    public Boolean setnx(String key, int seconds, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForValue().setIfAbsent(getKey(key), value, seconds, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<?> collectionClass, Class<?>... elementClasses) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForValue().get(getKey(key));
        return JsonUtil.readJson(res, collectionClass, elementClasses);

    }

    @Override
    public Long incr(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(getKey(key));
    }

    @Override
    public Long incr(String key, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(getKey(key), delta);
    }

    @Override
    public Double incr(String key, Double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().increment(getKey(key), delta);
    }

    @Override
    public Long decr(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().decrement(getKey(key));
    }

    @Override
    public Long decr(String key, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForValue().decrement(getKey(key), delta);
    }

    @Override
    public Set<String> getKeys(String pattern) {
        return keysByPattern(pattern);
    }

    @Override
    public Boolean delete(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.delete(getKey(key));
    }

    @Override
    public Boolean hasKey(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.hasKey(getKey(key));
    }

    @Override
    public Long delete(List<String> keys) {
        Validator.assertNotEmpty(keys, ErrCodeSys.PA_DATA_NOT_EXIST, "keys");
        return redisTemplate.delete(getKeys(keys));
    }

    private List<String> getKeys(List<String> key) {
        List<String> keys = new ArrayList<>();
        for (String s : key) {
            keys.add(getKey(s));
        }
        return keys;
    }

    @Override
    public Boolean setExpireTime(String key, Date expireDate) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(expireDate, ErrCodeSys.PA_DATA_NOT_EXIST, "expireDate");
        return redisTemplate.expireAt(getKey(key), expireDate);
    }

    @Override
    public Boolean expire(String key, long timeout) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.expire(getKey(key), timeout, TimeUnit.SECONDS);
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.expire(getKey(key), timeout, timeUnit);
    }

    @Override
    public void hashSet(String key, String field, String value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        Validator.assertNotBlank(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().put(getKey(key), field, value);
    }

    @Override
    public void hashSet(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().putAll(getKey(key), ReflectionUtil.getHashMap(value));
    }

    @Override
    public void hashSet(String key, Map<String, Object> map) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(map, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.opsForHash().putAll(getKey(key), map);
    }

    @Override
    public void hashUpdate(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        Map<String, Object> map = ReflectionUtil.getHashMap(value);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                redisTemplate.opsForHash().put(getKey(key), entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Long hashDel(String key, String... fields) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(fields, ErrCodeSys.PA_DATA_NOT_EXIST, "fields");
        return redisTemplate.opsForHash().delete(getKey(key), fields);
    }

    @Override
    public Map<String, Object> hashGet(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Map<Object, Object> map = redisTemplate.opsForHash().entries(getKey(key));
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
            Object res = redisTemplate.opsForHash().get(getKey(key), field.getName());
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
        Object res = redisTemplate.opsForHash().get(getKey(key), field);
        return JsonUtil.readJson(res, clazz);

    }

    @Override
    public Long hashIncr(String key, String field, long delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        return redisTemplate.opsForHash().increment(getKey(key), field, delta);
    }

    @Override
    public Double hashIncrDouble(String key, String field, Double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotBlank(field, ErrCodeSys.PA_DATA_NOT_EXIST, "field");
        return redisTemplate.opsForHash().increment(getKey(key), field, delta);
    }

    @Override
    public Long leftPush(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().leftPush(getKey(key), value);
    }

    @Override
    public Long leftPush(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().leftPushAll(getKey(key), value);
    }

    @Override
    public Long rightPush(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");

        return redisTemplate.opsForList().rightPush(getKey(key), value);
    }

    @Override
    public Long rightPush(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForList().rightPushAll(getKey(key), value);
    }

    @Override
    public <T> T leftPop(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().leftPop(getKey(key));
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T leftBlockingPop(String key, int seconds, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().leftPop(getKey(key), seconds, TimeUnit.SECONDS);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T rightPop(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().rightPop(getKey(key));
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> T rightBlockingPop(String key, int seconds, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForList().rightPop(getKey(key), seconds, TimeUnit.SECONDS);
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public <T> List<T> getSetMembers(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<?> res = redisTemplate.opsForSet().members(getKey(key));
        return JsonUtil.readJson(res, Set.class, clazz);
    }

    @Override
    public Long setAdd(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().add(getKey(key), value.toArray());
    }

    @Override
    public Long setAdd(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().add(getKey(key), value);
    }

    @Override
    public Long setRemove(String key, List<Object> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().remove(getKey(key), value.toArray());
    }

    @Override
    public Long setRemove(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForSet().remove(getKey(key), value);
    }

    @Override
    public <T> T getSetRandMember(String key, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Object res = redisTemplate.opsForSet().randomMember(getKey(key));
        return JsonUtil.readJson(res, clazz);
    }

    @Override
    public Long getSetSize(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForSet().size(getKey(key));
    }

    @Override
    public Boolean setIsMember(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForSet().isMember(getKey(key), value);
    }

    @Override
    public Boolean zSetAdd(String key, Object value, double score) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().add(getKey(key), value, score);
    }

    @Override
    public Long zSetRemoveBySet(String key, Set<?> value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotEmpty(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().remove(getKey(key), value.toArray());
    }

    @Override
    public Long zSetRemove(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().remove(getKey(key), value);
    }

    @Override
    public Double zSetIncrScore(String key, Object value, double delta) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().incrementScore(getKey(key), value, delta);
    }

    @Override
    public Double zSetScore(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().score(getKey(key), value);
    }

    @Override
    public Long zSetRank(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        return redisTemplate.opsForZSet().rank(getKey(key), value);
    }

    @Override
    public <T> Set<T> zSetRange(String key, long start, long end, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().range(getKey(key), start, end);
        return JsonUtil.readJson(res, Set.class, clazz);
    }

    @Override
    public <T> Set<T> zSetRangeByScore(String key, double min, double max, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().rangeByScore(getKey(key), min, max);
        return JsonUtil.readJson(res, Set.class, clazz);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScore(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().rangeWithScores(getKey(key), start, end);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScoreByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().rangeByScoreWithScores(getKey(key), min, max);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScore(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().reverseRangeWithScores(getKey(key), start, end);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zSetRevRangeWithScoreByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().reverseRangeByScoreWithScores(getKey(key), min, max);
    }

    @Override
    public <T> Set<T> zSetRevRange(String key, long start, long end, Class<T> clazz) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        Set<Object> res = redisTemplate.opsForZSet().reverseRange(getKey(key), start, end);
        return JsonUtil.readJson(res, Set.class, clazz);
    }

    @Override
    public Long zSetRemoveRange(String key, long start, long end) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().removeRange(getKey(key), start, end);
    }

    @Override
    public Long zSetRemoveByScore(String key, double min, double max) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().removeRangeByScore(getKey(key), min, max);
    }

    @Override
    public Long zSetSize(String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, "key");
        return redisTemplate.opsForZSet().size(getKey(key));
    }

    @Override
    public boolean zSetIsMember(String key, Object value) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_DATA_NOT_EXIST, key);
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        Long res = redisTemplate.opsForZSet().rank(getKey(key), value);
        return !(res == null || res <= 0);
    }

    @Override
    public Long zSetUnion(List<String> keyList, String destKey) {
        Validator.assertNotEmpty(keyList, ErrCodeSys.PA_DATA_NOT_EXIST, "keyList");
        String firstKey = keyList.get(0);
        keyList.remove(0);
        return redisTemplate.opsForZSet().unionAndStore(getKey(firstKey), getKeys(keyList), destKey);
    }

    @Override
    public Long zSetIntersect(List<String> keyList, String destKey) {
        Validator.assertNotEmpty(keyList, ErrCodeSys.PA_DATA_NOT_EXIST, "keyList");
        String firstKey = keyList.get(0);
        keyList.remove(0);
        return redisTemplate.opsForZSet().intersectAndStore(getKey(firstKey), getKeys(keyList), destKey);
    }

    @Override
    public void publish(String topic, Object value) {
        Validator.assertNotBlank(topic, ErrCodeSys.PA_DATA_NOT_EXIST, "topic");
        Validator.assertNotNull(value, ErrCodeSys.PA_DATA_NOT_EXIST, "value");
        redisTemplate.convertAndSend(getKey(topic), value);
    }

    @Override
    public void subscribe(MessageListener listener, String topic) {
        Validator.assertNotBlank(topic, ErrCodeSys.PA_DATA_NOT_EXIST, "topic");
        redisTemplate.execute((RedisCallback<String>) connection -> {
            connection.subscribe(listener, getKey(topic).getBytes());
            return null;
        });
    }
}
