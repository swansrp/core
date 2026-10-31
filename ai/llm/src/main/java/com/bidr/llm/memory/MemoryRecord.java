package com.bidr.llm.memory;

import lombok.Data;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Title: MemoryRecord
 * Description: 一条跨会话记忆（T4.3 记忆库）：租户 + 用户维度隔离，四类 kind 分类，
 * priority 越大越优先（同优先级按 createdAt 新者在前）。expireAt 毫秒时间戳，
 * 0 表示不过期；过期条目检索时不可见，由 {@link MemoryStore#purgeExpired()} 物理清除。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class MemoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录标识（写入方生成，全局唯一；Redis/内存实现都以它做条目键） */
    private String recordId;

    /** 租户标识 */
    private Long tenantId;

    /** 用户标识 */
    private Long userId;

    /** 记忆类别 */
    private MemoryKind kind;

    /** 记忆正文 */
    private String content;

    /** 写入时间（毫秒） */
    private long createdAt;

    /** 过期时间（毫秒）；0=不过期 */
    private long expireAt;

    /** 优先级（越大越优先） */
    private int priority;

    /**
     * 统一排序器：priority 降序 → createdAt 降序（同优先级新者在前）。
     * 内存/Redis 实现与 recall 合并排序共用一份口径，避免各写一份比较逻辑漂移。
     */
    public static final Comparator<MemoryRecord> PRIORITY_THEN_NEWEST =
            (a, b) -> {
                int byPriority = Integer.compare(b.getPriority(), a.getPriority());
                if (byPriority != 0) {
                    return byPriority;
                }
                return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            };
}
