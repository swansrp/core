package com.bidr.llm.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的流式回答状态存储实现（兜底）。
 * <p>
 * 适用场景：单实例部署且允许重启丢失流状态。多实例部署或需要跨节点读取时，
 * 请引入 core/redis 依赖切换为 {@link RedisStreamAnswerStore}（业务代码无需改动）。
 * <ul>
 *     <li>仅当类路径不存在 core/redis 时装配，与 Redis 实现互斥；</li>
 *     <li>内存写入无需节流；通过惰性清理模拟 TTL，防止长时间运行内存泄漏；</li>
 *     <li>业务方如需自定义实现，可注册自己的 {@link StreamAnswerStore} Bean 并标注 {@code @Primary}。</li>
 * </ul>
 * </p>
 *
 * @author Sharp
 */
@Slf4j
@Component
@ConditionalOnMissingClass("com.bidr.platform.redis.service.RedisService")
public class InMemoryStreamAnswerStore implements StreamAnswerStore {

    /**
     * 惰性清理的最小间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    /**
     * 运行中状态的过期时间（秒），与 Redis 实现共用配置键
     */
    private final long ttlSeconds;

    /**
     * 流结束后的短过期时间（秒），与 Redis 实现共用配置键
     */
    private final long finishedTtlSeconds;

    /**
     * 各流的状态条目，value 记录状态与最后写入时间
     */
    private final Map<String, Entry> states = new ConcurrentHashMap<String, Entry>();

    /**
     * 最近一次执行惰性清理的时间戳
     */
    private volatile long lastCleanupAt = System.currentTimeMillis();

    public InMemoryStreamAnswerStore(@Value("${llm.stream-answer.ttl-seconds:600}") long ttlSeconds,
                                     @Value("${llm.stream-answer.finished-ttl-seconds:300}") long finishedTtlSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.finishedTtlSeconds = finishedTtlSeconds;
        log.info("流式回答状态存储使用内存实现（单实例适用），多实例部署请引入 core/redis");
    }

    @Override
    public void updateContent(String streamId, String content, boolean finish) {
        StreamAnswerState state = getState(streamId);
        if (state == null) {
            state = new StreamAnswerState();
        }
        state.setContent(content);
        state.setFinish(finish);
        updateState(streamId, state);
    }

    @Override
    public void updateState(String streamId, StreamAnswerState state) {
        if (state == null) {
            return;
        }
        states.put(streamId, new Entry(state, System.currentTimeMillis()));
        cleanupIfNeeded();
    }

    @Override
    public String getCurrentContent(String streamId) {
        StreamAnswerState state = getState(streamId);
        return state != null ? state.getContent() : null;
    }

    @Override
    public StreamAnswerState getState(String streamId) {
        Entry entry = states.get(streamId);
        if (entry == null) {
            return null;
        }
        // 已过期的条目视为不存在并顺手移除
        if (isExpired(entry, System.currentTimeMillis())) {
            states.remove(streamId);
            return null;
        }
        return entry.state;
    }

    /**
     * 距上次清理超过间隔时，遍历移除过期条目（惰性 TTL）
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupAt = now;
        Iterator<Map.Entry<String, Entry>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), now)) {
                iterator.remove();
            }
        }
    }

    /**
     * 判断条目是否过期：终态用短 TTL，运行态用长 TTL
     */
    private boolean isExpired(Entry entry, long now) {
        long useTtl = entry.state.isFinish() ? finishedTtlSeconds : ttlSeconds;
        return useTtl > 0 && now - entry.updatedAt > useTtl * 1000;
    }

    /**
     * 状态条目：状态快照 + 最后写入时间
     */
    private static class Entry {
        final StreamAnswerState state;
        final long updatedAt;

        Entry(StreamAnswerState state, long updatedAt) {
            this.state = state;
            this.updatedAt = updatedAt;
        }
    }
}
