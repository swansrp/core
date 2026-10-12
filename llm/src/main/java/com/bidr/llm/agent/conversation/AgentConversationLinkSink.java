package com.bidr.llm.agent.conversation;

/**
 * Title: AgentConversationLinkSink
 * Description: 会话↔历史对话定位回调（M14 棒2 增量）：谁想把"某场 agent 会话对应哪条对话"记进
 * 自己的永久台账，就实现本接口并注册成 Bean。
 *
 * <p><b>为什么需要这一层</b>：对话正文按保留天数滚窗过期（Redis TTL），而"这场会话聊过什么"这件事
 * 本身是不该跟着过期的证据——装配单、审计这类永久面只需要一个 id 就能表达"当时有条对话，
 * 现在过没过保留期"。框架侧只负责在写对话的那一刻把这个对应关系广播出去，
 * 存不存、存哪儿一概不管。</p>
 *
 * <p><b>可选装配</b>：本接口无框架内实现；{@link AgentConversationService} 经 {@code ObjectProvider}
 * 取，一个实现都没有即静默跳过 ⇒ 不装台账的工程零行为变化。回调异常一律由广播方吞掉，
 * 绝不打断对话写入主链路。</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
public interface AgentConversationLinkSink {

    /**
     * 定位关系确立（一条会话的对话首次写入时一次；同一会话续写不重复广播）
     *
     * @param sessionId      框架 agent 会话标识
     * @param conversationId 历史对话标识（正文在保留期内可按它取回）
     */
    void onConversationLinked(String sessionId, String conversationId);
}
