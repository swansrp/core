package com.bidr.llm.agent.conversation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentConversation
 * Description: agent 通用历史对话（自 ChatBiConversation 上提，业务零绑定）——
 * Redis 按访问人隔离存储，保留天数经 {@link AgentConversationRetentionProvider} SPI 注入（缺省 10 天）。
 * 对话归属统一注册中心的 agentCode（flow:{flowKey} / 自主 agentKey / {namespace}:{code}），
 * 历史列表可跨 agent 聚合展示、按 agentCode 过滤。列表视图不带 messages（前端按需走详情）。
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Data
public class AgentConversation {

    /** 对话标识（UUID，首问时后端创建） */
    private String conversationId;

    /** 归属 agent（统一注册中心 agentCode，历史/评价按它归组） */
    private String agentCode;

    /** 访问人（对话按人隔离；免登回落 anonymous） */
    private String operator;

    /** 对话标题（首问摘要，截断 50 字） */
    private String title;

    /** 创建时间（毫秒时间戳） */
    private Long createTime;

    /** 最近活跃时间（毫秒时间戳，列表按它倒序） */
    private Long updateTime;

    /** 消息条数（列表视图展示用） */
    private Integer messageCount;

    /** 消息明细（列表视图不带，详情才有） */
    private List<AgentConversationMessage> messages = new ArrayList<>();
}
