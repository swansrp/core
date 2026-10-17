package com.bidr.llm.agent.conversation;

/**
 * Title: AgentConversationRetentionProvider
 * Description: 对话保留天数 SPI（同 {@link com.bidr.llm.flow.FlowTraceRetentionProvider} 口径）——
 * 业务方按自己的系统参数注入（如 chatbi 的保存天数参数），无实现回落默认 10 天。
 *
 * @author Sharp
 * @since 2026/8/22
 */
public interface AgentConversationRetentionProvider {

    /**
     * 对话保留天数（天，&le;0 视为非法由存储服务回落默认值）
     */
    int retentionDays();
}
