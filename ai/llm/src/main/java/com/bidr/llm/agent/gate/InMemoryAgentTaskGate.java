package com.bidr.llm.agent.gate;

import com.bidr.kernel.exception.NoticeException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Title: InMemoryAgentTaskGate
 * Description: 闸门内存实现（类路径无 core/redis 的单实例 fallback）：
 * ConcurrentHashMap compute 原子抢锁，活性与失联自愈口径与 Redis 实现一致；
 * 无 TTL 概念（进程内无过期），残留锁同样靠失联阈值强删，进程重启自然清零
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
public class InMemoryAgentTaskGate implements AgentTaskGate {

    private final ConcurrentHashMap<String, GateLock> locks = new ConcurrentHashMap<>();

    /**
     * 失联阈值（毫秒）：与 Redis 实现同口径（锁内心跳超时即判定属主失联）。
     * 🔴 同样按每次判定取值——内存兜底形态若固化在构造期，dev 侧改了参数页没反应，两形态生效语义必须一致
     */
    private final LongSupplier orphanMillisSupplier;

    public InMemoryAgentTaskGate(long orphanMillis) {
        this(() -> orphanMillis);
    }

    public InMemoryAgentTaskGate(LongSupplier orphanMillisSupplier) {
        this.orphanMillisSupplier = orphanMillisSupplier;
    }

    private long orphanMillis() {
        return orphanMillisSupplier.getAsLong();
    }

    @Override
    public void acquire(String taskKey, String ownerToken, int ttlSeconds, String busyMessage) {
        if (tryPut(taskKey, ownerToken)) {
            return;
        }
        // 占用：先失联自愈再重试一次，仍占用才判忙（与 Redis 实现同流程）
        if (forceUnlockIfOrphan(taskKey) && tryPut(taskKey, ownerToken)) {
            return;
        }
        throw new NoticeException(busyMessage);
    }

    @Override
    public void checkFree(String taskKey, String busyMessage) {
        forceUnlockIfOrphan(taskKey);
        if (locks.containsKey(taskKey)) {
            throw new NoticeException(busyMessage);
        }
    }

    @Override
    public void heartbeat(String taskKey, String ownerToken, int ttlSeconds) {
        locks.computeIfPresent(taskKey, (key, lock) ->
                ownerToken.equals(lock.getOwnerToken())
                        ? new GateLock(ownerToken, System.currentTimeMillis()) : lock);
    }

    @Override
    public void release(String taskKey, String ownerToken) {
        locks.computeIfPresent(taskKey, (key, lock) ->
                ownerToken.equals(lock.getOwnerToken()) ? null : lock);
    }

    @Override
    public boolean forceUnlockIfOrphan(String taskKey) {
        GateLock lock = locks.get(taskKey);
        if (lock == null) {
            return false;
        }
        // 阈值按本次判定现取（与 Redis 形态同口径）；日志把「生效阈值」与「已静默时长」两者都记
        long threshold = orphanMillis();
        long silentMillis = System.currentTimeMillis() - lock.getHeartbeat();
        if (silentMillis > threshold) {
            locks.remove(taskKey, lock);
            log.warn("闸门属主失联强解锁, taskKey={}, owner={}, 生效阈值={}ms, 心跳已静默={}ms",
                    taskKey, lock.getOwnerToken(), threshold, silentMillis);
            return true;
        }
        return false;
    }

    private boolean tryPut(String taskKey, String ownerToken) {
        return locks.putIfAbsent(taskKey, new GateLock(ownerToken, System.currentTimeMillis())) == null;
    }
}
