package com.bidr.llm.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Title: MemoryContextInjector
 * Description: 记忆上下文注入器（T4.3 记忆库）——把跨会话记忆召回结果格式化为
 * 「已知用户上下文：」段追加到 systemPrompt，让新会话"记得"老用户。
 * <p>
 * 调用时机＝新会话建立、业务调用方组装好基础 systemPrompt 之后、发起首轮对话之前显式调用
 * {@link #injectInto(Long, Long, String)}（不改既有方法签名；无记忆或召回为空时原文返回，
 * 注入失败只记日志并回落原 prompt，绝不打断对话主流程）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
public class MemoryContextInjector {

    /**
     * 默认召回条数（可经 llm.memory.inject-top-n 覆盖）
     */
    public static final int DEFAULT_TOP_N = 10;

    /**
     * 注入段标题
     */
    public static final String CONTEXT_HEADER = "已知用户上下文：";

    private final MemoryService memoryService;

    /**
     * 注入时召回的条数上限
     */
    private final int topN;

    public MemoryContextInjector(MemoryService memoryService,
                                 @Value("${llm.memory.inject-top-n:10}") int topN) {
        this.memoryService = memoryService;
        this.topN = topN > 0 ? topN : DEFAULT_TOP_N;
    }

    /**
     * 把该用户/租户的跨会话记忆（recall top-N）格式化追加到 systemPrompt：
     * 每条一行「- [类别] 正文」。无记忆时原样返回（包括 null/空白 prompt）。
     *
     * @param tenantId     租户标识
     * @param userId       用户标识
     * @param systemPrompt 基础系统提示（可为 null）
     * @return 追加记忆段后的完整 systemPrompt
     */
    public String injectInto(Long tenantId, Long userId, String systemPrompt) {
        List<MemoryRecord> records;
        try {
            records = memoryService.recall(tenantId, userId, topN);
        } catch (Exception e) {
            log.warn("记忆召回失败，回落原始 systemPrompt, error={}", e.getMessage());
            return systemPrompt;
        }
        if (records.isEmpty()) {
            return systemPrompt;
        }
        StringBuilder context = new StringBuilder();
        for (MemoryRecord record : records) {
            if (context.length() > 0) {
                context.append('\n');
            }
            context.append("- [").append(record.getKind()).append("] ")
                    .append(record.getContent() == null ? "" : record.getContent());
        }
        String base = StringUtils.hasText(systemPrompt) ? systemPrompt : "";
        if (base.length() > 0 && !base.endsWith("\n")) {
            base = base + "\n\n";
        }
        return base + CONTEXT_HEADER + "\n" + context;
    }
}
