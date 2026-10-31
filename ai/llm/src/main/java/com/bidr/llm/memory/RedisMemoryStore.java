package com.bidr.llm.memory;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Title: RedisMemoryStore
 * Description: 基于 Redis 的跨会话记忆存储实现（多实例部署适用）。
 * <ul>
 *     <li>记录以 JSON 写独立 key（llm:memory:record:{tenantId}:{userId}:{recordId}），
 *     expireAt&gt;0 时按剩余毫秒折算秒级 TTL（不足 1 秒按 1 秒），Redis 自动过期即物理清除；</li>
 *     <li>每用户一个 zset 索引（llm:memory:index:{tenantId}:{userId}，member=recordId，
 *     score=createdAt）；检索走索引定位 recordId，索引里的失效成员（记录先过期）顺手清掉；</li>
 *     <li>仅当类路径存在 core/redis 时装配，与 {@link InMemoryMemoryStore} 互斥；
 *     业务方如需自定义实现，可注册自己的 {@link MemoryStore} Bean 并标注 {@code @Primary}。</li>
 * </ul>
 * <p>存储是辅助链路：读写异常只记日志不抛出（同 RedisStreamAnswerStore 口径）。</p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.bidr.platform.redis.service.RedisService")
public class RedisMemoryStore implements MemoryStore {

    /**
     * 记录正文 key 段：{keyPrefix}record:{tenantId}:{userId}:{recordId}
     */
    private static final String SEG_RECORD = "record:";

    /**
     * 用户索引 key 段：{keyPrefix}index:{tenantId}:{userId}（zset，score=createdAt）
     */
    private static final String SEG_INDEX = "index:";

    private final RedisService redisService;

    private final ObjectMapper objectMapper;

    /**
     * Redis key 前缀
     */
    private final String keyPrefix;

    public RedisMemoryStore(RedisService redisService,
                            ObjectMapper objectMapper,
                            @Value("${llm.memory.key-prefix:llm:memory:}") String keyPrefix) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void save(MemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getRecordId())) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            String json = objectMapper.writeValueAsString(record);
            if (record.getExpireAt() > 0) {
                if (record.getExpireAt() <= now) {
                    // 已过期：不写（写了也是立即过期的脏数据）
                    return;
                }
                int ttlSeconds = (int) Math.max(1L, (record.getExpireAt() - now) / 1000L);
                redisService.set(recordKey(record.getTenantId(), record.getUserId(), record.getRecordId()),
                        ttlSeconds, json);
            } else {
                redisService.set(recordKey(record.getTenantId(), record.getUserId(), record.getRecordId()), json);
            }
            redisService.zSetAdd(indexKey(record.getTenantId(), record.getUserId()),
                    record.getRecordId(), record.getCreatedAt());
        } catch (JsonProcessingException e) {
            log.error("记忆序列化失败 recordId {}, error: {}", record.getRecordId(), e.getMessage());
        } catch (Exception e) {
            // Redis 写入错误（网络/连接）只记日志，不打断业务主流程
            log.warn("记忆写入 Redis 失败，请检查网络/连接 recordId {}, error: {}",
                    record.getRecordId(), e.getMessage());
        }
    }

    @Override
    public List<MemoryRecord> search(Long tenantId, Long userId, MemoryKind kind, String keyword, int topN) {
        List<MemoryRecord> result = new ArrayList<>();
        if (topN <= 0) {
            return result;
        }
        String indexKey = indexKey(tenantId, userId);
        try {
            Set<String> recordIds = redisService.zSetRange(indexKey, 0, -1, String.class);
            if (recordIds == null || recordIds.isEmpty()) {
                return result;
            }
            long now = System.currentTimeMillis();
            Set<String> stale = new HashSet<>();
            for (String recordId : recordIds) {
                MemoryRecord record = loadRecord(tenantId, userId, recordId);
                if (record == null) {
                    // 记录 key 已被 Redis TTL 清掉（索引滞后）：顺手摘除索引成员
                    stale.add(recordId);
                    continue;
                }
                if (record.getExpireAt() > 0 && record.getExpireAt() <= now) {
                    redisService.delete(recordKey(tenantId, userId, recordId));
                    stale.add(recordId);
                    continue;
                }
                if (matches(record, kind, keyword)) {
                    result.add(record);
                }
            }
            if (!stale.isEmpty()) {
                redisService.zSetRemoveBySet(indexKey, stale);
            }
        } catch (Exception e) {
            log.warn("记忆检索 Redis 异常 tenantId {}, userId {}, error: {}", tenantId, userId, e.getMessage());
            return new ArrayList<>();
        }
        result.sort(MemoryRecord.PRIORITY_THEN_NEWEST);
        return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
    }

    @Override
    public void delete(Long tenantId, Long userId, String recordId) {
        try {
            redisService.delete(recordKey(tenantId, userId, recordId));
            redisService.zSetRemove(indexKey(tenantId, userId), recordId);
        } catch (Exception e) {
            log.warn("记忆删除 Redis 异常 recordId {}, error: {}", recordId, e.getMessage());
        }
    }

    @Override
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        try {
            Set<String> indexKeys = redisService.keysByPattern(keyPrefix + SEG_INDEX + "*");
            if (indexKeys == null || indexKeys.isEmpty()) {
                return;
            }
            for (String indexKey : indexKeys) {
                // index key 形如 {keyPrefix}index:{tenantId}:{userId}，去前缀换 record 段得到记录 key 前缀
                String ownerPart = indexKey.substring((keyPrefix + SEG_INDEX).length());
                String recordKeyPrefix = keyPrefix + SEG_RECORD + ownerPart;
                Set<String> recordIds = redisService.zSetRange(indexKey, 0, -1, String.class);
                if (recordIds == null || recordIds.isEmpty()) {
                    continue;
                }
                Set<String> stale = new HashSet<>();
                for (String recordId : recordIds) {
                    String recordKey = recordKeyPrefix + ":" + recordId;
                    MemoryRecord record = loadRecordByKey(recordKey);
                    if (record == null || (record.getExpireAt() > 0 && record.getExpireAt() <= now)) {
                        if (record != null) {
                            redisService.delete(recordKey);
                        }
                        stale.add(recordId);
                    }
                }
                if (!stale.isEmpty()) {
                    redisService.zSetRemoveBySet(indexKey, stale);
                }
            }
        } catch (Exception e) {
            log.warn("过期记忆清扫 Redis 异常 error: {}", e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    /**
     * 读取单条记录（不存在或反序列化失败返回 null）
     */
    private MemoryRecord loadRecord(Long tenantId, Long userId, String recordId) {
        return loadRecordByKey(recordKey(tenantId, userId, recordId));
    }

    private MemoryRecord loadRecordByKey(String recordKey) {
        try {
            String json = redisService.get(recordKey, String.class);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, MemoryRecord.class);
        } catch (Exception e) {
            log.warn("记忆读取/反序列化异常 key {}, error: {}", recordKey, e.getMessage());
            return null;
        }
    }

    /**
     * kind/keyword 可选过滤（keyword 忽略大小写，空=不过滤）
     */
    private boolean matches(MemoryRecord record, MemoryKind kind, String keyword) {
        if (kind != null && kind != record.getKind()) {
            return false;
        }
        if (StringUtils.hasText(keyword)) {
            String content = record.getContent();
            if (content == null
                    || !content.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private String recordKey(Long tenantId, Long userId, String recordId) {
        return keyPrefix + SEG_RECORD + tenantId + ":" + userId + ":" + recordId;
    }

    private String indexKey(Long tenantId, Long userId) {
        return keyPrefix + SEG_INDEX + tenantId + ":" + userId;
    }
}
