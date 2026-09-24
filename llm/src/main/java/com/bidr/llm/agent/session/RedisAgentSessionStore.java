package com.bidr.llm.agent.session;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Title: RedisAgentSessionStore
 * Description: 会话存储 Redis 实现（生产默认）：
 * 状态与事件流各一键存 JSON，随每次写入 TTL 续期；
 * 暂停/恢复指导语/停止为独立控制键——分布式多实例下任意实例可控制属主实例的会话。
 * <p>
 * 🔴 <b>不变式（是契约不是实现细节）：一个会话的事件流只有 run 线程这一个写者。</b>
 * {@link #appendEvent} 为整表读-改-写、无跨键原子性，双写会互相覆盖。故任何非 run 线程
 * （HTTP 请求线程、心跳调度线程、定时任务）<b>都不得对同一会话调用 appendEvent</b>：
 * 跨线程要传达的事实走控制键（{@code requestStop}/{@code pause}/{@code submitAnswer}），
 * 由 run 线程收口时统一补发事件；执行实例存活信号同样走独立轻键（{@code touchRun}），
 * 心跳线程不回写状态快照。
 * <p>
 * 🔴 另一条同等重要的约束：<b>读失败不得当作"空表"回写</b>，否则一次瞬时失败会把整条历史截断
 * （实测中断标志位污染 Redisson 期间 64 条→2 条、序号从 1 重启）。{@link #appendEvent} 已按
 * "读失败即跳过本次追加"处理；{@link #read} 仍返回 null 表示失败，调用方不得据此构造新值写回。
 * <p>
 * Redis 不可用时不中断执行：事件与状态写入失败仅记日志（会话退化无过程可见，控制键失联），
 * 与 RedisStreamAnswerStore 的容错口径一致
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class RedisAgentSessionStore implements AgentSessionStore {

    /** 停止键短 TTL：run 线程收口后即清理，键仅是跨实例传信载体 */
    private static final int STOP_TTL_SECONDS = 600;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final int ttlSeconds;

    public RedisAgentSessionStore(RedisService redisService, ObjectMapper objectMapper,
                                  String keyPrefix, int ttlSeconds) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public void saveState(AgentSessionState state) {
        if (state == null) {
            return;
        }
        write(stateKey(state.getSessionId()), state);
    }

    @Override
    public AgentSessionState getState(String sessionId) {
        return read(stateKey(sessionId), new TypeReference<AgentSessionState>() {
        });
    }

    @Override
    public List<String> listSessionIds() {
        // 扫状态键（事件/控制键均带后缀，按后缀排除）；会话量小（管理端操作），keys 扫描可接受
        Set<String> keys = redisService.keysByPattern(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String key : keys) {
            String id = key.substring(keyPrefix.length());
            if (!id.contains(":")) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 🔴 读-改-写的安全前提：**"读失败"绝不等于"还没有事件"**。
     * 若把读失败当空表再回写，会把整条历史截断成 1 条（实测中断标志位污染 Redisson 期间发生过
     * 64 条→2 条、序号从 1 重启）。故读失败时**跳过本次追加**：宁可少一条过程事件，不可毁掉历史。
     *
     * @return 本次事件的 seq；负值表示未落库（读或写失败）
     */
    @Override
    public long appendEvent(String sessionId, String type, Object payload) {
        String key = eventsKey(sessionId);
        List<AgentEvent> events;
        try {
            String json = redisService.get(key, String.class);
            events = json == null ? new ArrayList<AgentEvent>()
                    : objectMapper.readValue(json, new TypeReference<List<AgentEvent>>() {
            });
        } catch (Exception e) {
            log.warn("agent 会话事件流读取失败，跳过本次追加以免截断历史, sessionId={}, type={}, error={}",
                    sessionId, type, e.getMessage());
            return -1;
        }
        if (events == null) {
            events = new ArrayList<>();
        }
        AgentEvent event = new AgentEvent();
        event.setSeq(events.isEmpty() ? 1 : events.get(events.size() - 1).getSeq() + 1);
        event.setType(type);
        event.setPayload(payload);
        event.setTime(System.currentTimeMillis());
        events.add(event);
        try {
            redisService.set(key, ttlSeconds, objectMapper.writeValueAsString(events));
        } catch (Exception e) {
            log.warn("agent 会话事件写入失败（会话退化无过程可见）key={}, error={}", key, e.getMessage());
            return -1;
        }
        return event.getSeq();
    }

    @Override
    public List<AgentEvent> events(String sessionId, long sinceSeq) {
        List<AgentEvent> events = read(eventsKey(sessionId), new TypeReference<List<AgentEvent>>() {
        });
        if (events == null) {
            return Collections.emptyList();
        }
        List<AgentEvent> delta = new ArrayList<>();
        for (AgentEvent event : events) {
            if (event.getSeq() > sinceSeq) {
                delta.add(event);
            }
        }
        return delta;
    }

    @Override
    public void pause(String sessionId, String note) {
        redisService.set(pauseKey(sessionId), ttlSeconds, note == null ? "1" : note);
    }

    @Override
    public void touchView(String sessionId) {
        redisService.set(viewKey(sessionId), ttlSeconds, String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public void touchRun(String sessionId) {
        redisService.set(runKey(sessionId), ttlSeconds, String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public long runTime(String sessionId) {
        String value = redisService.get(runKey(sessionId), String.class);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public long viewTime(String sessionId) {
        String value = redisService.get(viewKey(sessionId), String.class);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void resume(String sessionId, String guidance) {
        redisService.delete(pauseKey(sessionId));
        if (guidance != null && !guidance.trim().isEmpty()) {
            redisService.set(guidanceKey(sessionId), ttlSeconds, guidance);
        }
    }

    @Override
    public boolean isPaused(String sessionId) {
        return Boolean.TRUE.equals(redisService.hasKey(pauseKey(sessionId)));
    }

    @Override
    public String consumeResumeGuidance(String sessionId) {
        String key = guidanceKey(sessionId);
        String guidance = redisService.get(key, String.class);
        if (guidance != null) {
            redisService.delete(key);
        }
        return guidance;
    }

    @Override
    public void submitAnswer(String sessionId, String answerJson) {
        redisService.set(answerKey(sessionId), ttlSeconds, answerJson);
    }

    @Override
    public String consumeAnswer(String sessionId) {
        String key = answerKey(sessionId);
        String answer = redisService.get(key, String.class);
        if (answer != null) {
            redisService.delete(key);
        }
        return answer;
    }

    @Override
    public void requestStop(String sessionId) {
        redisService.set(stopKey(sessionId), STOP_TTL_SECONDS, "1");
    }

    @Override
    public boolean isStopRequested(String sessionId) {
        return Boolean.TRUE.equals(redisService.hasKey(stopKey(sessionId)));
    }

    @Override
    public void clearControls(String sessionId) {
        redisService.delete(pauseKey(sessionId));
        redisService.delete(guidanceKey(sessionId));
        redisService.delete(answerKey(sessionId));
        redisService.delete(stopKey(sessionId));
        redisService.delete(viewKey(sessionId));
        redisService.delete(runKey(sessionId));
    }

    // ---- 序列化与键 ----

    private void write(String key, Object value) {
        try {
            redisService.set(key, ttlSeconds, objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            log.warn("agent 会话 Redis 写入失败（会话退化无过程可见）key={}, error={}", key, e.getMessage());
        }
    }

    private <T> T read(String key, TypeReference<T> type) {
        try {
            String json = redisService.get(key, String.class);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("agent 会话 Redis 读取失败 key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private String stateKey(String sessionId) {
        return keyPrefix + sessionId;
    }

    private String eventsKey(String sessionId) {
        return keyPrefix + sessionId + ":events";
    }

    private String runKey(String sessionId) {
        return keyPrefix + sessionId + ":run";
    }

    private String pauseKey(String sessionId) {
        return keyPrefix + sessionId + ":pause";
    }

    private String guidanceKey(String sessionId) {
        return keyPrefix + sessionId + ":guidance";
    }

    private String answerKey(String sessionId) {
        return keyPrefix + sessionId + ":answer";
    }

    private String stopKey(String sessionId) {
        return keyPrefix + sessionId + ":stop";
    }

    private String viewKey(String sessionId) {
        return keyPrefix + sessionId + ":view";
    }
}
