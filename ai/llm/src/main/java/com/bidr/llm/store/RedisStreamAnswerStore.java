package com.bidr.llm.store;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的流式回答状态存储实现。
 * <p>
 * 特性：
 * <ul>
 *     <li>状态以 JSON 字符串写入 Redis，运行中与结束后使用不同 TTL；</li>
 *     <li>内置写入节流：中间态更新距上次写入不足 {@code min-write-interval-ms} 时跳过本次写入
 *     （下次写入会携带最新全量内容，不会丢内容）；finish=true 的终态写入永不节流；</li>
 *     <li>仅当类路径存在 core/redis 时装配；业务方如需自定义实现，
 *     可注册自己的 {@link StreamAnswerStore} Bean 并标注 {@code @Primary}。</li>
 * </ul>
 * </p>
 *
 * @author Sharp
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.bidr.platform.redis.service.RedisService")
public class RedisStreamAnswerStore implements StreamAnswerStore {

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * Redis key 前缀
     */
    private final String keyPrefix;

    /**
     * 运行中状态的过期时间（秒）
     */
    private final long ttlSeconds;

    /**
     * 流结束后的短过期时间（秒）
     */
    private final long finishedTtlSeconds;

    /**
     * 中间态写入的最小间隔（毫秒），0 表示不节流。
     * 长文场景（每个 token 推一次全量内容）必须开启，避免打爆 Redis 带宽。
     */
    private final long minWriteIntervalMs;

    /**
     * 各流最近一次成功写入 Redis 的时间戳，用于节流判断；终态写入后移除
     */
    private final Map<String, Long> lastWriteAt = new ConcurrentHashMap<String, Long>();

    public RedisStreamAnswerStore(RedisService redisService,
                                  ObjectMapper objectMapper,
                                  @Value("${llm.stream-answer.key-prefix:llm:stream:answer:}") String keyPrefix,
                                  @Value("${llm.stream-answer.ttl-seconds:600}") long ttlSeconds,
                                  @Value("${llm.stream-answer.finished-ttl-seconds:300}") long finishedTtlSeconds,
                                  @Value("${llm.stream-answer.min-write-interval-ms:300}") long minWriteIntervalMs) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttlSeconds = ttlSeconds;
        this.finishedTtlSeconds = finishedTtlSeconds;
        this.minWriteIntervalMs = minWriteIntervalMs;
    }

    @Override
    public void updateContent(String streamId, String content, boolean finish) {
        // 节流：中间态更新且距上次写入过近时跳过（内容是全量覆盖，下次写入不丢数据）
        if (shouldSkipWrite(streamId, finish)) {
            return;
        }
        StreamAnswerState state = getOrCreateState(streamId);
        state.setContent(content);
        state.setFinish(finish);
        saveState(streamId, state, finish);
    }

    @Override
    public void updateState(String streamId, StreamAnswerState state) {
        if (state == null) {
            return;
        }
        boolean finish = state.isFinish();
        if (shouldSkipWrite(streamId, finish)) {
            return;
        }
        saveState(streamId, state, finish);
    }

    @Override
    public String getCurrentContent(String streamId) {
        StreamAnswerState state = getState(streamId);
        return state != null ? state.getContent() : null;
    }

    @Override
    public StreamAnswerState getState(String streamId) {
        String key = buildKey(streamId);
        try {
            // 通过 RedisService 读取字符串，再用 ObjectMapper 反序列化
            String json = redisService.get(key, String.class);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, StreamAnswerState.class);
        } catch (Exception e) {
            // 读取/反序列化异常时记录日志，返回 null 让上层做容错处理
            log.warn("Redis读取/反序列化异常 key {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 判断本次写入是否应被节流跳过：仅对中间态生效，终态写入永不跳过
     */
    private boolean shouldSkipWrite(String streamId, boolean finish) {
        if (finish || minWriteIntervalMs <= 0) {
            return false;
        }
        Long last = lastWriteAt.get(streamId);
        return last != null && System.currentTimeMillis() - last < minWriteIntervalMs;
    }

    /**
     * 获取已有状态，若无则创建新状态
     */
    private StreamAnswerState getOrCreateState(String streamId) {
        StreamAnswerState state = getState(streamId);
        return state == null ? new StreamAnswerState() : state;
    }

    /**
     * 序列化状态并带 TTL 写入 Redis，成功后维护节流时间戳
     */
    private void saveState(String streamId, StreamAnswerState state, boolean finish) {
        String key = buildKey(streamId);
        try {
            String json = objectMapper.writeValueAsString(state);
            long useTtl = finish ? finishedTtlSeconds : ttlSeconds;
            if (useTtl <= 0) {
                log.error("stream-answer TTL 配置异常，ttlSeconds={}, finishedTtlSeconds={}", ttlSeconds, finishedTtlSeconds);
            }
            // 带过期时间写入
            redisService.set(key, (int) useTtl, json);
            if (finish) {
                // 终态写入后清理节流记录，避免 Map 泄漏
                lastWriteAt.remove(streamId);
            } else {
                lastWriteAt.put(streamId, System.currentTimeMillis());
            }
        } catch (JsonProcessingException e) {
            log.error("Redis序列化失败 streamId {}, error: {}", streamId, e.getMessage());
        } catch (Exception e) {
            // Redis 写入错误（网络/连接）
            log.warn("Redis存入数据错误，请检查网络/连接 streamId {}, error: {}", streamId, e.getMessage());
        }
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(String streamId) {
        return keyPrefix + streamId;
    }
}
