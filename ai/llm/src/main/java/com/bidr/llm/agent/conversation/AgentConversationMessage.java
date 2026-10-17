package com.bidr.llm.agent.conversation;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: AgentConversationMessage
 * Description: 通用历史对话消息条目（user 提问 / assistant 回答）——正文 + 状态 + 评价为通用维度，
 * 恢复渲染所需的业务负载（如 chatbi 的 chart-spec/看板、问数链的完整 payload）统一走 {@link #ext}，
 * llm 不感知其结构，由业务方写入、前端按业务消费。
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Data
public class AgentConversationMessage {

    /** 角色：user-用户提问 / assistant-助手回答 */
    private String role;

    /** 消息正文（失败回复为错误信息） */
    private String content;

    /** 状态：done-正常 / error-失败 / stopped-用户停止（恢复时按对应样式展示） */
    private String status;

    /** 消息时间（毫秒时间戳） */
    private Long time;

    /** 消息标识（后端生成；评价时按它定位） */
    private String messageId;

    /** 用户评价：like-点赞 / dislike-点踩 / 空-未评价（仅 assistant 消息有值） */
    private String rating;

    /** 业务扩展负载（恢复渲染用；如 chatbi 的 spec/tableId/portalName，问数链的完整 payload） */
    private Map<String, Object> ext = new HashMap<>();
}
