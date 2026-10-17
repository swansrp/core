package com.bidr.llm.flow.trace;

import com.bidr.llm.flow.FlowTraceRetentionProvider;
import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Title: FlowTraceRecorder
 * Description: 执行轨迹 Redis 存储——按访问人建 zset 索引（member=traceId，score=开始时间），
 * 整条轨迹 JSON 存独立 key，保留天数经 {@link FlowTraceRetentionProvider} SPI 读取
 * （业务模块接配置中心，实时读取改后即生效；无实现或非法回落默认 10 天），
 * 超期由 Redis TTL 自动清理，重启不丢。应用未接入 Redis 时全部操作降级为空操作
 * （轨迹是调试辅助链路，不影响流程执行）。
 * <p>
 * 读-改-写有丢更新窗口，写路径保留 synchronized（单实例与原内存版语义一致；
 * Redis 异常只记日志不打断主流程）。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Component
public class FlowTraceRecorder {

    /**
     * 问题摘要截断长度（字）
     */
    private static final int QUESTION_MAX_CHARS = 200;

    /**
     * 免登/无访问人时的回落标识
     */
    private static final String DEFAULT_OPERATOR = "anonymous";

    /**
     * 保留天数 SPI 缺失/非法时的回落值
     */
    private static final int DEFAULT_RETENTION_DAYS = 10;

    /**
     * 轨迹正文 key 前缀：llm:flow:trace:{traceId}
     */
    private static final String KEY_DETAIL = "llm:flow:trace:";

    /**
     * 访问人索引 key 前缀：llm:flow:trace:index:{operator}（zset，score=startTime）
     */
    private static final String KEY_INDEX = "llm:flow:trace:index:";

    private final ObjectProvider<RedisService> redisProvider;

    private final ObjectProvider<FlowTraceRetentionProvider> retentionProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowTraceRecorder(ObjectProvider<RedisService> redisProvider,
                             ObjectProvider<FlowTraceRetentionProvider> retentionProvider) {
        this.redisProvider = redisProvider;
        this.retentionProvider = retentionProvider;
    }

    /**
     * 开启一条轨迹（status=running），返回 traceId；未接入 Redis 时仍返回 id 但不落任何记录
     */
    public synchronized String startTrace(String flowKey, String question, boolean builtin, String operator) {
        FlowTrace.TraceRecord record = new FlowTrace.TraceRecord();
        record.setTraceId(UUID.randomUUID().toString());
        record.setFlowKey(flowKey);
        record.setStartTime(System.currentTimeMillis());
        record.setStatus("running");
        record.setQuestion(truncate(question));
        record.setBuiltin(builtin);
        record.setOperator(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR);
        save(record);
        return record.getTraceId();
    }

    public synchronized void recordNode(String traceId, FlowTrace.NodeEvent event) {
        FlowTrace.TraceRecord record = getTrace(traceId);
        if (record != null) {
            record.getNodes().add(event);
            save(record);
        }
    }

    /**
     * 给已记录的结点事件补记全文（流式挂起结点在 resume 时补模型回答、回调失败补错误）
     */
    public synchronized void appendNodeDetail(String traceId, String nodeId, String detailSuffix) {
        FlowTrace.TraceRecord record = getTrace(traceId);
        if (record == null) {
            return;
        }
        for (int i = record.getNodes().size() - 1; i >= 0; i--) {
            FlowTrace.NodeEvent event = record.getNodes().get(i);
            if (nodeId.equals(event.getNodeId())) {
                event.setDetail(StringUtils.hasText(event.getDetail())
                        ? event.getDetail() + detailSuffix : detailSuffix);
                save(record);
                return;
            }
        }
    }

    public synchronized void finishTrace(String traceId, String status, String error) {
        FlowTrace.TraceRecord record = getTrace(traceId);
        if (record != null) {
            record.setStatus(status);
            record.setEndTime(System.currentTimeMillis());
            record.setError(error);
            save(record);
        }
    }

    /**
     * 轨迹列表（新→旧，按访问人隔离；列表视图不带结点事件，全文走 getTrace 详情）。
     * 索引里正文已过期的成员（TTL 先到）顺手清掉
     */
    public List<FlowTrace.TraceRecord> listTraces(String flowKey, String operator) {
        List<FlowTrace.TraceRecord> result = new ArrayList<>();
        RedisService redis = redis();
        if (redis == null) {
            return result;
        }
        String indexKey = KEY_INDEX + (StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR);
        Set<String> traceIds = redis.zSetRange(indexKey, 0, -1, String.class);
        if (traceIds == null || traceIds.isEmpty()) {
            return result;
        }
        Set<String> stale = new HashSet<>();
        for (String traceId : traceIds) {
            FlowTrace.TraceRecord record = getTrace(traceId);
            if (record == null) {
                stale.add(traceId);
                continue;
            }
            if (StringUtils.hasText(flowKey) && !flowKey.equals(record.getFlowKey())) {
                continue;
            }
            result.add(listView(record));
        }
        if (!stale.isEmpty()) {
            redis.zSetRemoveBySet(indexKey, stale);
        }
        // 显式按开始时间倒序（新→旧），不依赖 zset 返回顺序
        result.sort(Comparator.comparingLong(FlowTrace.TraceRecord::getStartTime).reversed());
        return result;
    }

    public FlowTrace.TraceRecord getTrace(String traceId) {
        RedisService redis = redis();
        if (redis == null || !StringUtils.hasText(traceId)) {
            return null;
        }
        try {
            String json = redis.get(KEY_DETAIL + traceId, String.class);
            return json == null ? null : objectMapper.readValue(json, FlowTrace.TraceRecord.class);
        } catch (Exception e) {
            log.warn("读取执行轨迹失败, traceId={}, error={}", traceId, e.getMessage());
            return null;
        }
    }

    /**
     * 整条轨迹写正文 key（TTL=保留天数）+ 访问人索引 zset（同步续期，滚动窗口）
     */
    private void save(FlowTrace.TraceRecord record) {
        RedisService redis = redis();
        if (redis == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(record);
            int ttlSeconds = retentionDays() * 24 * 3600;
            redis.set(KEY_DETAIL + record.getTraceId(), ttlSeconds, json);
            String indexKey = KEY_INDEX + record.getOperator();
            redis.zSetAdd(indexKey, record.getTraceId(), record.getStartTime());
            redis.expire(indexKey, ttlSeconds);
        } catch (Exception e) {
            log.warn("保存执行轨迹失败, traceId={}, error={}", record.getTraceId(), e.getMessage());
        }
    }

    /**
     * 保留天数实时读 SPI（业务模块接配置中心，管理页可改，改后即生效），
     * 无实现或非法配置回落默认值
     */
    private int retentionDays() {
        FlowTraceRetentionProvider provider = retentionProvider.getIfAvailable();
        if (provider == null) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            int days = provider.retentionDays();
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取流程轨迹保留天数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }

    /**
     * Redis 可用则返回实例，未接入返回 null（操作全部降级空操作）
     */
    private RedisService redis() {
        return redisProvider.getIfAvailable();
    }

    private FlowTrace.TraceRecord listView(FlowTrace.TraceRecord record) {
        FlowTrace.TraceRecord view = new FlowTrace.TraceRecord();
        view.setTraceId(record.getTraceId());
        view.setFlowKey(record.getFlowKey());
        view.setStartTime(record.getStartTime());
        view.setEndTime(record.getEndTime());
        view.setStatus(record.getStatus());
        view.setQuestion(record.getQuestion());
        view.setOperator(record.getOperator());
        view.setBuiltin(record.getBuiltin());
        view.setError(record.getError());
        return view;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= QUESTION_MAX_CHARS ? text : text.substring(0, QUESTION_MAX_CHARS) + "…";
    }
}
