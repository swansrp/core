package com.bidr.insight.chatbi.service;

import com.bidr.insight.chatbi.vo.ChatBiConversation;
import com.bidr.insight.chatbi.vo.ChatBiConversationMessage;
import com.bidr.llm.agent.conversation.AgentConversation;
import com.bidr.llm.agent.conversation.AgentConversationMessage;
import com.bidr.llm.agent.conversation.AgentConversationService;
import com.bidr.llm.agent.registry.AgentDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ChatBiConversationService
 * Description: 问数历史对话业务适配层（原 Redis 存储逻辑已上提至 llm 通用
 * {@link AgentConversationService}，本类只做 chatbi VO 与通用模型互转）——
 * 归属 agentCode 固定 {@code flow:ask}（统一注册中心命名空间约定：
 * {@link AgentDescriptor#FLOW_PREFIX} + {@link com.bidr.insight.chatbi.flow.ChatBiAskFlowDefinition} flowKey）。
 * <p>
 * 消息业务负载走通用消息 ext：spec（chart-spec 编排指令）/tableId/portalName，
 * 恢复渲染语义与旧版一致。旧存量（insight:chatbi:conversation: 前缀）不迁移，
 * 按其 TTL（默认 10 天）自然过期。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class ChatBiConversationService {

    /**
     * 问数对话归属的 agentCode（注册中心：flow:{flowKey}，flowKey=ask）
     */
    public static final String AGENT_CODE = AgentDescriptor.FLOW_PREFIX + "ask";

    /**
     * ext 键：chart-spec 编排指令
     */
    private static final String EXT_SPEC = "spec";

    /**
     * ext 键：看板编码
     */
    private static final String EXT_TABLE_ID = "tableId";

    /**
     * ext 键：看板名
     */
    private static final String EXT_PORTAL_NAME = "portalName";

    private final AgentConversationService agentConversationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatBiConversationService(AgentConversationService agentConversationService) {
        this.agentConversationService = agentConversationService;
    }

    /**
     * 追加用户提问（提问即存）：新对话在此创建并返回 id（经 SSE conv 事件回传前端续接）
     */
    public String appendUser(String conversationId, String operator, String question,
                             String tableId, String portalName) {
        return agentConversationService.appendUser(AGENT_CODE, conversationId, operator,
                question, ext(null, tableId, portalName));
    }

    /**
     * 追加助手回复（引擎链路收口时调用）：失败回复带 error 状态；
     * chartSpec 为 JSON 字符串时归一化成结构化对象。返回消息标识（对话不存在返回 null）
     */
    public String appendAssistant(String conversationId, String content, Object chartSpec,
                                  String tableId, String portalName, String status) {
        return agentConversationService.appendAssistant(conversationId, content, status,
                ext(normalizeSpec(chartSpec), tableId, portalName));
    }

    /**
     * 评价助手回复（仅本人对话）：委托通用服务内嵌 rating，转回 chatbi VO
     * （对话过期/越权/找不到消息抛 IllegalArgumentException，语义不变）
     */
    public ChatBiConversation rateMessage(String conversationId, String messageId,
                                          String rating, String operator) {
        return toChatBi(agentConversationService.rateMessage(conversationId, messageId, rating, operator));
    }

    /**
     * 历史对话列表（新→旧，按访问人隔离；仅 chatbi 对话，通用列表按 agentCode 过滤）
     */
    public List<ChatBiConversation> listConversations(String operator) {
        List<AgentConversation> list = agentConversationService.listConversations(operator, AGENT_CODE);
        return list.stream().map(this::toChatBi).collect(java.util.stream.Collectors.toList());
    }

    public ChatBiConversation getConversation(String conversationId) {
        return toChatBi(agentConversationService.getConversation(conversationId));
    }

    /**
     * 删除对话（仅能删自己的，防横向越权）
     */
    public void deleteConversation(String conversationId, String operator) {
        agentConversationService.deleteConversation(conversationId, operator);
    }

    // ==================== 转换工具 ====================

    /**
     * 组装消息业务负载（空值不入 ext，省存储）
     */
    private Map<String, Object> ext(Object spec, String tableId, String portalName) {
        Map<String, Object> ext = new HashMap<>();
        if (spec != null) {
            ext.put(EXT_SPEC, spec);
        }
        if (StringUtils.hasText(tableId)) {
            ext.put(EXT_TABLE_ID, tableId);
        }
        if (StringUtils.hasText(portalName)) {
            ext.put(EXT_PORTAL_NAME, portalName);
        }
        return ext;
    }

    /**
     * chartSpec 归一化：extract 结点存的是 JSON 字符串，转结构化对象存储（前端恢复直接消费）；
     * 转换失败或本来非字符串则原样保留
     */
    private Object normalizeSpec(Object chartSpec) {
        if (!(chartSpec instanceof String) || !StringUtils.hasText((String) chartSpec)) {
            return chartSpec;
        }
        try {
            return objectMapper.readValue((String) chartSpec, Object.class);
        } catch (Exception e) {
            log.warn("chartSpec 归一化失败，按原文保留: {}", e.getMessage());
            return chartSpec;
        }
    }

    private ChatBiConversation toChatBi(AgentConversation conversation) {
        if (conversation == null) {
            return null;
        }
        ChatBiConversation view = new ChatBiConversation();
        view.setConversationId(conversation.getConversationId());
        view.setOperator(conversation.getOperator());
        view.setTitle(conversation.getTitle());
        view.setCreateTime(conversation.getCreateTime());
        view.setUpdateTime(conversation.getUpdateTime());
        view.setMessageCount(conversation.getMessageCount());
        for (AgentConversationMessage item : conversation.getMessages()) {
            ChatBiConversationMessage message = new ChatBiConversationMessage();
            message.setRole(item.getRole());
            message.setContent(item.getContent());
            message.setStatus(item.getStatus());
            message.setTime(item.getTime());
            message.setMessageId(item.getMessageId());
            message.setRating(item.getRating());
            Map<String, Object> ext = item.getExt();
            message.setSpec(ext.get(EXT_SPEC));
            message.setTableId(asString(ext.get(EXT_TABLE_ID)));
            message.setPortalName(asString(ext.get(EXT_PORTAL_NAME)));
            view.getMessages().add(message);
        }
        return view;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
