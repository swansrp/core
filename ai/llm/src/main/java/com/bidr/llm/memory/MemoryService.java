package com.bidr.llm.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Title: MemoryService
 * Description: 跨会话记忆门面（T4.3 记忆库）：写入（生成 recordId、默认 TTL 30 天可覆盖）、
 * 召回（全 kind 合并按 priority + createdAt 排序截断）、过期清扫与 TTL 调整。
 * <p>
 * 存储经 {@link ObjectProvider}&lt;{@link MemoryStore}&gt; 可选装配（InMemory/Redis 二选一，
 * 条件注解互斥；业务方自定义实现标注 {@code @Primary} 即接管）。存储缺席或异常是辅助链路：
 * 写入仍返回内存态记录（不落盘），检索返回空列表，绝不打断业务主流程（同对话存储口径）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Service
public class MemoryService {

    /**
     * 默认保留天数（可被 write 的 ttlDays 覆盖）
     */
    public static final long DEFAULT_TTL_DAYS = 30;

    /**
     * 管理端单次列举上限（防一次拖全量）
     */
    public static final int LIST_TOP_N = 500;

    private final ObjectProvider<MemoryStore> storeProvider;

    public MemoryService(ObjectProvider<MemoryStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    /**
     * 写入一条记忆（默认优先级 0、默认 TTL 30 天）
     *
     * @return 内存态记录（recordId 已生成；存储缺席时记录不落盘但仍返回）
     */
    public MemoryRecord write(Long tenantId, Long userId, MemoryKind kind, String content) {
        return write(tenantId, userId, kind, content, 0, DEFAULT_TTL_DAYS);
    }

    /**
     * 写入一条记忆（全参）：生成 recordId 与 createdAt，ttlDays&gt;0 时按天折算 expireAt，
     * ttlDays&lt;=0 表示不过期。kind/content/租户/用户任一缺失即拒绝（IllegalArgumentException）。
     */
    public MemoryRecord write(Long tenantId, Long userId, MemoryKind kind, String content,
                              int priority, long ttlDays) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("记忆写入需要租户与用户标识");
        }
        if (kind == null) {
            throw new IllegalArgumentException("记忆写入需要类别（MemoryKind）");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("记忆正文不能为空");
        }
        long now = System.currentTimeMillis();
        MemoryRecord record = new MemoryRecord();
        record.setRecordId(UUID.randomUUID().toString().replace("-", ""));
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setKind(kind);
        record.setContent(content);
        record.setCreatedAt(now);
        record.setExpireAt(ttlDays > 0 ? now + ttlDays * 24 * 3600 * 1000L : 0);
        record.setPriority(priority);
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            log.debug("记忆存储未装配，记录仅返回不落盘 recordId={}", record.getRecordId());
            return record;
        }
        store.save(record);
        return record;
    }

    /**
     * 召回：全部 kind 合并，按 priority 降序 → createdAt 降序排好后截断 topN
     * （新会话建立时由 {@link MemoryContextInjector} 调用注入 systemPrompt）
     */
    public List<MemoryRecord> recall(Long tenantId, Long userId, int topN) {
        if (topN <= 0) {
            return new ArrayList<>();
        }
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return new ArrayList<>();
        }
        List<MemoryRecord> records = store.search(tenantId, userId, null, null, Integer.MAX_VALUE);
        records.sort(MemoryRecord.PRIORITY_THEN_NEWEST);
        return records.size() > topN ? new ArrayList<>(records.subList(0, topN)) : records;
    }

    /**
     * 管理端列举：kind/keyword 可选过滤，上限 {@link #LIST_TOP_N}
     */
    public List<MemoryRecord> list(Long tenantId, Long userId, MemoryKind kind, String keyword) {
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return new ArrayList<>();
        }
        return store.search(tenantId, userId, kind, keyword, LIST_TOP_N);
    }

    /**
     * 删除单条记忆（存储缺席时静默跳过）
     */
    public void remove(Long tenantId, Long userId, String recordId) {
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return;
        }
        store.delete(tenantId, userId, recordId);
    }

    /**
     * TTL 调整：定位记录后重写 expireAt（days&gt;0=按天顺延，days&lt;=0=改为永不过期），
     * 覆盖保存走 save 的 recordId 幂等；记录不存在抛 IllegalArgumentException
     */
    public void adjustTtl(Long tenantId, Long userId, String recordId, long days) {
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return;
        }
        MemoryRecord target = locate(store, tenantId, userId, recordId);
        if (target == null) {
            throw new IllegalArgumentException("未找到要调整 TTL 的记忆: " + recordId);
        }
        target.setExpireAt(days > 0 ? System.currentTimeMillis() + days * 24 * 3600 * 1000L : 0);
        store.save(target);
    }

    /**
     * 过期清扫（管理端手动触发 / 定时任务挂点）
     */
    public void sweep() {
        MemoryStore store = storeProvider.getIfAvailable();
        if (store == null) {
            return;
        }
        store.purgeExpired();
    }

    /**
     * 按记录标识定位（全 kind 扫描后匹配 recordId）
     */
    private MemoryRecord locate(MemoryStore store, Long tenantId, Long userId, String recordId) {
        if (!StringUtils.hasText(recordId)) {
            return null;
        }
        for (MemoryRecord record : store.search(tenantId, userId, null, null, Integer.MAX_VALUE)) {
            if (recordId.equals(record.getRecordId())) {
                return record;
            }
        }
        return null;
    }
}
