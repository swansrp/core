package com.bidr.llm.usage;

/**
 * Title: ModelCallContext
 * Description: 一次模型调用"替谁跑的"身份快照（M14 棒5）：会话标识 + agent 键 + 发起人。
 *
 * <p>字段全为 final：本对象会被发起线程交给落库线程读，可变就会读到改一半的值。
 * 三栏都允许为 null——直连小请求（拟标题、范围解析）没有会话身份，计量表按"有则填"留空，
 * 不拿空串冒充、也不按 anonymous 编一个。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public final class ModelCallContext {

    private final String sessionId;
    private final String agentKey;
    private final String askedBy;

    public ModelCallContext(String sessionId, String agentKey, String askedBy) {
        this.sessionId = sessionId;
        this.agentKey = agentKey;
        this.askedBy = askedBy;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentKey() {
        return agentKey;
    }

    public String getAskedBy() {
        return askedBy;
    }

    /** 三栏全空的上下文等价于"没有上下文"：调用方按此省掉一次 set，别让空对象盖掉真实值 */
    public boolean isEmpty() {
        return sessionId == null && agentKey == null && askedBy == null;
    }
}
