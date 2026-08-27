package com.bidr.llm.agent.gate;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: RedisAgentTaskGate
 * Description: 闸门 Redis 实现（生产默认，分布式任意实例互斥）：
 * SETNX 抢锁（锁值 {属主令牌, 心跳} JSON），心跳/释放读改写均先比对属主（防误删他实例锁）；
 * 占用时先失联自愈（锁内心跳超阈值 → 强删重试一次）再判忙。
 * Redis 运行时异常降级进程内 CAS（与业务原手写锁同口径，本地/故障场景不阻断），
 * Redis 异常修复或进程重启后自然回归分布式口径
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
public class RedisAgentTaskGate implements AgentTaskGate {

    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    /** 失联阈值（毫秒）：锁内心跳超时即判定属主失联，可强删自愈 */
    private final long orphanMillis;

    /** Redis 运行时异常的进程内降级锁（同原业务 localRunning 口径；键=taskKey，值=属主令牌） */
    private final ConcurrentHashMap<String, String> localFallback = new ConcurrentHashMap<>();

    public RedisAgentTaskGate(RedisService redisService, ObjectMapper objectMapper,
                              String keyPrefix, long orphanMillis) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.orphanMillis = orphanMillis;
    }

    @Override
    public void acquire(String taskKey, String ownerToken, int ttlSeconds, String busyMessage) {
        try {
            if (trySetnx(taskKey, ownerToken, ttlSeconds)) {
                return;
            }
            // 占用：先失联自愈（属主宕机残留锁强删后重试一次），仍占用才判忙
            if (forceUnlockIfOrphan(taskKey) && trySetnx(taskKey, ownerToken, ttlSeconds)) {
                return;
            }
            throw new NoticeException(busyMessage);
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("闸门抢锁 Redis 异常，降级进程内锁, taskKey={}, error={}", taskKey, e.getMessage());
            if (localFallback.putIfAbsent(taskKey, ownerToken) != null) {
                throw new NoticeException(busyMessage);
            }
        }
    }

    @Override
    public void checkFree(String taskKey, String busyMessage) {
        try {
            forceUnlockIfOrphan(taskKey);
            if (Boolean.TRUE.equals(redisService.hasKey(lockKey(taskKey)))) {
                throw new NoticeException(busyMessage);
            }
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("闸门预检 Redis 异常（忽略，拿锁时再校验）, taskKey={}, error={}", taskKey, e.getMessage());
            if (localFallback.containsKey(taskKey)) {
                throw new NoticeException(busyMessage);
            }
        }
    }

    @Override
    public void heartbeat(String taskKey, String ownerToken, int ttlSeconds) {
        try {
            GateLock lock = read(taskKey);
            if (lock != null && ownerToken.equals(lock.getOwnerToken())) {
                write(taskKey, new GateLock(ownerToken, System.currentTimeMillis()), ttlSeconds);
            }
        } catch (Exception e) {
            log.warn("闸门心跳续期失败（忽略，下轮重试）, taskKey={}, error={}", taskKey, e.getMessage());
        }
    }

    @Override
    public void release(String taskKey, String ownerToken) {
        localFallback.remove(taskKey, ownerToken);
        try {
            GateLock lock = read(taskKey);
            if (lock != null && ownerToken.equals(lock.getOwnerToken())) {
                redisService.delete(lockKey(taskKey));
            }
        } catch (Exception e) {
            log.warn("闸门释锁异常（TTL 到期自动失效）, taskKey={}, error={}", taskKey, e.getMessage());
        }
    }

    @Override
    public boolean forceUnlockIfOrphan(String taskKey) {
        try {
            GateLock lock = read(taskKey);
            if (lock != null && System.currentTimeMillis() - lock.getHeartbeat() > orphanMillis) {
                redisService.delete(lockKey(taskKey));
                log.warn("闸门属主失联强解锁（心跳超 {}ms）, taskKey={}, owner={}",
                        System.currentTimeMillis() - lock.getHeartbeat(), taskKey, lock.getOwnerToken());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("闸门失联强解锁失败（忽略，TTL 到期自动失效）, taskKey={}, error={}", taskKey, e.getMessage());
            return false;
        }
    }

    // ---- 锁值读写 ----

    private boolean trySetnx(String taskKey, String ownerToken, int ttlSeconds) throws Exception {
        return Boolean.TRUE.equals(redisService.setnx(lockKey(taskKey), ttlSeconds,
                objectMapper.writeValueAsString(new GateLock(ownerToken, System.currentTimeMillis()))));
    }

    private GateLock read(String taskKey) throws Exception {
        String json = redisService.get(lockKey(taskKey), String.class);
        return json == null ? null : objectMapper.readValue(json, GateLock.class);
    }

    private void write(String taskKey, GateLock lock, int ttlSeconds) throws Exception {
        redisService.set(lockKey(taskKey), ttlSeconds, objectMapper.writeValueAsString(lock));
    }

    private String lockKey(String taskKey) {
        return keyPrefix + taskKey;
    }
}
