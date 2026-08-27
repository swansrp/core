package com.bidr.llm.agent.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: InMemoryAgentSessionStore
 * Description: 会话存储内存实现——类路径无 core/redis 时的单实例 fallback
 * （会话与控制键均在本地 JVM，跨实例不感知；无 TTL，终态后由业务自行不再引用）。
 * 生产（pm-hse 聚合）始终装配 Redis 实现，本类仅兜底
 *
 * @author Sharp
 * @since 2026/8/20
 */
public class InMemoryAgentSessionStore implements AgentSessionStore {

    private static class SessionBox {
        AgentSessionState state;
        final List<AgentEvent> events = new ArrayList<>();
        String pauseNote;
        String resumeGuidance;
        String answerJson;
        boolean stopRequested;
        long lastViewAt;
    }

    private final Map<String, SessionBox> sessions = new ConcurrentHashMap<>();

    @Override
    public void saveState(AgentSessionState state) {
        if (state == null) {
            return;
        }
        box(state.getSessionId()).state = state;
    }

    @Override
    public AgentSessionState getState(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        return box == null ? null : box.state;
    }

    @Override
    public List<String> listSessionIds() {
        return new ArrayList<>(sessions.keySet());
    }

    @Override
    public long appendEvent(String sessionId, String type, Object payload) {
        SessionBox box = box(sessionId);
        synchronized (box.events) {
            AgentEvent event = new AgentEvent();
            event.setSeq(box.events.isEmpty() ? 1 : box.events.get(box.events.size() - 1).getSeq() + 1);
            event.setType(type);
            event.setPayload(payload);
            event.setTime(System.currentTimeMillis());
            box.events.add(event);
            return event.getSeq();
        }
    }

    @Override
    public List<AgentEvent> events(String sessionId, long sinceSeq) {
        SessionBox box = sessions.get(sessionId);
        if (box == null) {
            return Collections.emptyList();
        }
        synchronized (box.events) {
            List<AgentEvent> delta = new ArrayList<>();
            for (AgentEvent event : box.events) {
                if (event.getSeq() > sinceSeq) {
                    delta.add(event);
                }
            }
            return delta;
        }
    }

    @Override
    public void pause(String sessionId, String note) {
        box(sessionId).pauseNote = note == null ? "1" : note;
    }

    @Override
    public void touchView(String sessionId) {
        box(sessionId).lastViewAt = System.currentTimeMillis();
    }

    @Override
    public long viewTime(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        return box == null ? 0 : box.lastViewAt;
    }

    @Override
    public void resume(String sessionId, String guidance) {
        SessionBox box = box(sessionId);
        box.pauseNote = null;
        if (guidance != null && !guidance.trim().isEmpty()) {
            box.resumeGuidance = guidance;
        }
    }

    @Override
    public boolean isPaused(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        return box != null && box.pauseNote != null;
    }

    @Override
    public String consumeResumeGuidance(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        if (box == null) {
            return null;
        }
        String guidance = box.resumeGuidance;
        box.resumeGuidance = null;
        return guidance;
    }

    @Override
    public void submitAnswer(String sessionId, String answerJson) {
        box(sessionId).answerJson = answerJson;
    }

    @Override
    public String consumeAnswer(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        if (box == null) {
            return null;
        }
        String answer = box.answerJson;
        box.answerJson = null;
        return answer;
    }

    @Override
    public void requestStop(String sessionId) {
        box(sessionId).stopRequested = true;
    }

    @Override
    public boolean isStopRequested(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        return box != null && box.stopRequested;
    }

    @Override
    public void clearControls(String sessionId) {
        SessionBox box = sessions.get(sessionId);
        if (box != null) {
            box.pauseNote = null;
            box.resumeGuidance = null;
            box.answerJson = null;
            box.stopRequested = false;
            box.lastViewAt = 0;
        }
    }

    private SessionBox box(String sessionId) {
        return sessions.computeIfAbsent(sessionId, key -> new SessionBox());
    }
}
