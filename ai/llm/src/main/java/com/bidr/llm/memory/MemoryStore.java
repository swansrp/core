package com.bidr.llm.memory;

import java.util.List;

/**
 * Title: MemoryStore
 * Description: 跨会话记忆存储 SPI（T4.3 记忆库）。实现方决定存储介质与淘汰策略：
 * <ul>
 *     <li>{@link InMemoryMemoryStore}——JVM 内存实现（含 LRU 上限 + TTL 惰性清理），单实例兜底；</li>
 *     <li>{@link RedisMemoryStore}——Redis 实现（逐条 TTL + zset 索引），类路径有 core/redis 时装配。</li>
 * </ul>
 * 记录按 tenantId + userId 维度隔离；keyword 为空表示不过滤正文。
 * 存储是辅助链路：实现方对底层故障只记日志不抛出（不打断业务主流程）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public interface MemoryStore {

    /**
     * 保存/覆盖一条记忆：recordId 相同即覆盖（TTL 调整走同一条路）。
     *
     * @param record 完整记录（recordId 必填）
     */
    void save(MemoryRecord record);

    /**
     * 检索记忆：按租户 + 用户（+ 可选 kind / keyword 过滤），
     * 返回按 priority 降序 → createdAt 降序排列的前 topN 条；已过期的条目不可见。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param kind     类别过滤；null=全部类别
     * @param keyword  正文包含过滤（忽略大小写）；空=不过滤
     * @param topN     最大返回条数；&lt;=0 返回空列表
     * @return 命中记录（永不返回 null）
     */
    List<MemoryRecord> search(Long tenantId, Long userId, MemoryKind kind, String keyword, int topN);

    /**
     * 删除单条记忆（recordId 不存在时静默跳过）。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param recordId 记录标识
     */
    void delete(Long tenantId, Long userId, String recordId);

    /**
     * 物理清除全部已过期条目（管理端 sweep / 定时任务调用）。
     */
    void purgeExpired();
}
