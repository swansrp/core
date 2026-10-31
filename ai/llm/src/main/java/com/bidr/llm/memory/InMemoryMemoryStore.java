package com.bidr.llm.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Title: InMemoryMemoryStore
 * Description: 基于 JVM 内存的跨会话记忆存储实现（兜底，单实例适用）。
 * <ul>
 *     <li>仅当类路径不存在 core/redis 时装配，与 {@link RedisMemoryStore} 互斥（先例同
 *     InMemoryStreamAnswerStore / RedisStreamAnswerStore）；</li>
 *     <li>TTL：条目带 expireAt，检索时惰性判过期（顺手移除），{@link #purgeExpired()} 物理清扫；</li>
 *     <li>LRU：总条目数超过 maxRecords 时先清过期，仍超限则淘汰最近最少访问的条目，
 *     防长运行内存泄漏；同毫秒访问按写入序破平（seq 单调递增），淘汰确定性可测。</li>
 * </ul>
 * <p>业务方如需自定义实现，可注册自己的 {@link MemoryStore} Bean 并标注 {@code @Primary}。</p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
@ConditionalOnMissingClass("com.bidr.platform.redis.service.RedisService")
public class InMemoryMemoryStore implements MemoryStore {

    /**
     * 条目键：tenantId:userId:recordId
     */
    private final Map<String, Entry> records = new ConcurrentHashMap<String, Entry>();

    /**
     * LRU 总条目上限（超限先清过期，再淘汰最近最少访问）
     */
    private final int maxRecords;

    /**
     * 访问序号（同毫秒访问破平用，单调递增）
     */
    private final AtomicLong accessSeq = new AtomicLong();

    public InMemoryMemoryStore(@Value("${llm.memory.max-records:10000}") int maxRecords) {
        this.maxRecords = maxRecords;
        log.info("跨会话记忆存储使用内存实现（单实例适用，maxRecords={}），多实例部署请引入 core/redis", maxRecords);
    }

    @Override
    public void save(MemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getRecordId())) {
            return;
        }
        records.put(key(record.getTenantId(), record.getUserId(), record.getRecordId()),
                new Entry(record, System.currentTimeMillis(), accessSeq.incrementAndGet()));
        evictIfNeeded();
    }

    @Override
    public List<MemoryRecord> search(Long tenantId, Long userId, MemoryKind kind, String keyword, int topN) {
        List<MemoryRecord> result = new ArrayList<>();
        if (topN <= 0) {
            return result;
        }
        long now = System.currentTimeMillis();
        long seq = accessSeq.incrementAndGet();
        Iterator<Map.Entry<String, Entry>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            // 过期条目视为不存在并顺手移除（惰性 TTL）
            if (isExpired(entry.record, now)) {
                iterator.remove();
                continue;
            }
            if (!matches(entry.record, tenantId, userId, kind, keyword)) {
                continue;
            }
            // 命中即触达（LRU 访问时间刷新）
            entry.lastAccessAt = now;
            entry.lastAccessSeq = seq;
            result.add(entry.record);
        }
        result.sort(MemoryRecord.PRIORITY_THEN_NEWEST);
        return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
    }

    @Override
    public void delete(Long tenantId, Long userId, String recordId) {
        records.remove(key(tenantId, userId, recordId));
    }

    @Override
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue().record, now)) {
                iterator.remove();
            }
        }
    }

    /**
     * 超上限时先物理清过期，仍超限则按最近最少访问淘汰（accessAt 相同按写入序，先写先出）
     */
    private void evictIfNeeded() {
        if (maxRecords <= 0 || records.size() <= maxRecords) {
            return;
        }
        purgeExpired();
        while (records.size() > maxRecords) {
            Map.Entry<String, Entry> lru = null;
            for (Map.Entry<String, Entry> candidate : records.entrySet()) {
                if (lru == null || isLessRecentlyUsed(candidate.getValue(), lru.getValue())) {
                    lru = candidate;
                }
            }
            if (lru == null) {
                break;
            }
            records.remove(lru.getKey());
        }
    }

    /**
     * LRU 比较条目是否比另一条更久未被访问：先比访问毫秒，同毫秒比访问序号
     */
    private boolean isLessRecentlyUsed(Entry a, Entry b) {
        if (a.lastAccessAt != b.lastAccessAt) {
            return a.lastAccessAt < b.lastAccessAt;
        }
        return a.lastAccessSeq < b.lastAccessSeq;
    }

    /**
     * 维度过滤：租户/用户必须全等，kind/keyword 可选
     */
    private boolean matches(MemoryRecord record, Long tenantId, Long userId, MemoryKind kind, String keyword) {
        if (tenantId == null || !tenantId.equals(record.getTenantId())) {
            return false;
        }
        if (userId == null || !userId.equals(record.getUserId())) {
            return false;
        }
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

    private boolean isExpired(MemoryRecord record, long now) {
        return record.getExpireAt() > 0 && now > record.getExpireAt();
    }

    private String key(Long tenantId, Long userId, String recordId) {
        return tenantId + ":" + userId + ":" + recordId;
    }

    /**
     * 存储条目：记录快照 + 最近访问时间/序号（LRU 依据）
     */
    private static class Entry {
        final MemoryRecord record;
        volatile long lastAccessAt;
        volatile long lastAccessSeq;

        Entry(MemoryRecord record, long lastAccessAt, long lastAccessSeq) {
            this.record = record;
            this.lastAccessAt = lastAccessAt;
            this.lastAccessSeq = lastAccessSeq;
        }
    }
}
