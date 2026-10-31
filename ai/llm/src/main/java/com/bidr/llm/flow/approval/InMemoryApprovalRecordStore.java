package com.bidr.llm.flow.approval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Title: InMemoryApprovalRecordStore
 * Description: 审批记录内存实现（类路径无 core/redis 时的默认装配，与业务自定义实现互斥；
 * 有 Redis/关系表的应用注册自己的 {@link ApprovalRecordStore} Bean 即自动让位）：
 * 单实例适用，进程重启审批队列清零（待审即失联，发起方重提即可）。
 * <p>
 * 读写与 InMemoryStreamAnswerStore 同手法：ConcurrentHashMap + 惰性拷贝排序，
 * decision 为空即待审，history 只收已决定记录（新→旧）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
@ConditionalOnMissingClass("com.bidr.platform.redis.service.RedisService")
public class InMemoryApprovalRecordStore implements ApprovalRecordStore {

    /**
     * history 缺省条数上限（limit 非正时的兜底）
     */
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    /**
     * recordId → 记录（save 即插入或覆盖）
     */
    private final Map<String, ApprovalRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(ApprovalRecord record) {
        if (record == null) {
            return;
        }
        if (record.getRecordId() == null || record.getRecordId().trim().isEmpty()) {
            record.setRecordId(UUID.randomUUID().toString());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(System.currentTimeMillis());
        }
        records.put(record.getRecordId(), record);
    }

    @Override
    public ApprovalRecord find(String recordId) {
        return recordId == null ? null : records.get(recordId);
    }

    @Override
    public List<ApprovalRecord> listPending(String flowKey) {
        return records.values().stream()
                .filter(record -> record.getDecision() == null)
                .filter(record -> flowKey == null || flowKey.equals(record.getFlowKey()))
                .sorted(Comparator.comparingLong(record -> record.getCreatedAt() == null ? 0 : record.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ApprovalRecord> history(String flowKey, int limit) {
        List<ApprovalRecord> decided = records.values().stream()
                .filter(record -> record.getDecision() != null)
                .filter(record -> flowKey == null || flowKey.equals(record.getFlowKey()))
                .sorted(Comparator.comparingLong(
                        (ApprovalRecord record) -> record.getCreatedAt() == null ? 0 : record.getCreatedAt())
                        .reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        int cap = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;
        return decided.size() <= cap ? decided : new ArrayList<>(decided.subList(0, cap));
    }
}
