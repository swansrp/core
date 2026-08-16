package com.bidr.insight.service;

import com.bidr.insight.constant.param.ChatBiParam;
import com.bidr.insight.vo.ChatBiConversation;
import com.bidr.insight.vo.ChatBiConversationMessage;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Title: ChatBiConversationService
 * Description: 问答对话 Redis 存储——按访问人建 zset 索引（member=conversationId，score=最近活跃时间），
 * 整条对话 JSON 存独立 key，保留天数读系统参数 {@link ChatBiParam#CONVERSATION_RETENTION_DAYS}
 * （默认 10 天），超期由 Redis TTL 自动清理，重启不丢；每次追加消息同步续期（滚动窗口）。
 * <p>
 * 写入时机：用户提问即存（{@link #appendUser}，新对话在此创建并返回 id），
 * 助手回复由引擎链路收口时补写（{@link #appendAssistant}）。
 * 与轨迹存储同理：读-改-写有丢更新窗口，写路径保留 synchronized；
 * 对话是辅助链路，Redis 异常只记日志不打断问数主流程。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class ChatBiConversationService {

    /**
     * 对话标题截断长度（字）
     */
    private static final int TITLE_MAX_CHARS = 50;

    /**
     * 单对话消息条数软上限（超出丢弃最旧，防长对话无限膨胀存储）
     */
    private static final int MAX_MESSAGES = 100;

    /**
     * 免登/无访问人时的回落标识（与轨迹存储一致）
     */
    private static final String DEFAULT_OPERATOR = "anonymous";

    /**
     * 保留天数参数非法时的回落值
     */
    private static final int DEFAULT_RETENTION_DAYS = 10;

    /**
     * 对话正文 key 前缀：insight:chatbi:conversation:{conversationId}
     */
    private static final String KEY_DETAIL = "insight:chatbi:conversation:";

    /**
     * 访问人索引 key 前缀：insight:chatbi:conversation:index:{operator}（zset，score=updateTime）
     */
    private static final String KEY_INDEX = "insight:chatbi:conversation:index:";

    private final RedisService redisService;

    private final SysConfigCacheService sysConfigCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatBiConversationService(RedisService redisService, SysConfigCacheService sysConfigCacheService) {
        this.redisService = redisService;
        this.sysConfigCacheService = sysConfigCacheService;
    }

    /**
     * 追加用户提问（提问即存）：conversationId 为空则创建新对话（标题=首问摘要），
     * 返回实际对话 id（调用方经 SSE conv 事件回传前端，前端续问按它续接）
     */
    public synchronized String appendUser(String conversationId, String operator, String question,
                                          String tableId, String portalName) {
        ChatBiConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            conversation = new ChatBiConversation();
            conversation.setConversationId(UUID.randomUUID().toString());
            conversation.setOperator(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR);
            conversation.setTitle(truncate(question));
            conversation.setCreateTime(System.currentTimeMillis());
        }
        append(conversation, message("user", question, null, "done", tableId, portalName));
        return conversation.getConversationId();
    }

    /**
     * 追加助手回复（引擎链路收口时调用）：失败回复带错误信息与 error 状态，恢复时按错误样式展示；
     * chartSpec 为 JSON 字符串时归一化成结构化对象（前端恢复渲染直接按对象消费）。
     * 返回消息标识（对话不存在时返回 null）——经 SSE msgid 事件回传前端，评价时按它定位
     */
    public synchronized String appendAssistant(String conversationId, String content, Object chartSpec,
                                                String tableId, String portalName, String status) {
        ChatBiConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            // 对话正文已过期（TTL 先到）或 id 非法：放弃本轮回复，不重建（避免凭空造出无提问的对话）
            log.debug("追加助手回复时对话不存在, conversationId={}", conversationId);
            return null;
        }
        ChatBiConversationMessage item = message("assistant", content, normalizeSpec(chartSpec), status, tableId, portalName);
        append(conversation, item);
        return item.getMessageId();
    }

    /**
     * 评价助手回复（仅本人对话）：定位被评价消息（messageId 空=最后一条 assistant）→ 内嵌 rating → 保存。
     * 返回改写后的整条对话（调用方从中再取该轮提问等快照）；对话过期/越权/找不到消息抛 IllegalArgumentException
     */
    public synchronized ChatBiConversation rateMessage(String conversationId, String messageId,
                                                       String rating, String operator) {
        ChatBiConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在或已过期");
        }
        if (!conversation.getOperator().equals(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("只能评价自己的对话");
        }
        boolean found = false;
        for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
            ChatBiConversationMessage item = conversation.getMessages().get(i);
            if (!"assistant".equals(item.getRole())) {
                continue;
            }
            if (!StringUtils.hasText(messageId) || messageId.equals(item.getMessageId())) {
                // 取消评价存 null（前端恢复回显"未评价"态）
                item.setRating(StringUtils.hasText(rating) ? rating : null);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("未找到被评价的回答");
        }
        save(conversation);
        return conversation;
    }

    /**
     * 历史对话列表（新→旧，按访问人隔离；列表视图不带消息明细，全文走 getConversation 详情）。
     * 索引里正文已过期的成员（TTL 先到）顺手清掉
     */
    public List<ChatBiConversation> listConversations(String operator) {
        List<ChatBiConversation> result = new ArrayList<>();
        String indexKey = KEY_INDEX + (StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR);
        Set<String> conversationIds = redisService.zSetRange(indexKey, 0, -1, String.class);
        if (conversationIds == null || conversationIds.isEmpty()) {
            return result;
        }
        Set<String> stale = new HashSet<>();
        for (String conversationId : conversationIds) {
            ChatBiConversation conversation = getConversation(conversationId);
            if (conversation == null) {
                stale.add(conversationId);
                continue;
            }
            result.add(listView(conversation));
        }
        if (!stale.isEmpty()) {
            redisService.zSetRemoveBySet(indexKey, stale);
        }
        // 显式按最近活跃倒序（新→旧），不依赖 zset 返回顺序
        result.sort(Comparator.comparingLong(ChatBiConversation::getUpdateTime).reversed());
        return result;
    }

    public ChatBiConversation getConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        try {
            String json = redisService.get(KEY_DETAIL + conversationId, String.class);
            return json == null ? null : objectMapper.readValue(json, ChatBiConversation.class);
        } catch (Exception e) {
            log.warn("读取历史对话失败, conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除对话（仅能删自己的，防横向越权）；正文+索引成员一并清理
     */
    public void deleteConversation(String conversationId, String operator) {
        ChatBiConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            return;
        }
        if (!conversation.getOperator().equals(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("只能删除自己的对话");
        }
        redisService.delete(KEY_DETAIL + conversationId);
        redisService.zSetRemoveBySet(KEY_INDEX + conversation.getOperator(), Collections.singleton(conversationId));
    }

    /**
     * 追加一条消息并落库：软上限截断旧消息，TTL=保留天数，索引 zset 同步续期（滚动窗口）
     */
    private void append(ChatBiConversation conversation, ChatBiConversationMessage item) {
        conversation.getMessages().add(item);
        if (conversation.getMessages().size() > MAX_MESSAGES) {
            conversation.setMessages(
                    new ArrayList<>(conversation.getMessages().subList(conversation.getMessages().size() - MAX_MESSAGES,
                            conversation.getMessages().size())));
        }
        conversation.setUpdateTime(item.getTime());
        save(conversation);
    }

    /**
     * 整条对话写正文 key（TTL=保留天数）+ 访问人索引 zset（同步续期）
     */
    private void save(ChatBiConversation conversation) {
        try {
            String json = objectMapper.writeValueAsString(conversation);
            int ttlSeconds = retentionDays() * 24 * 3600;
            redisService.set(KEY_DETAIL + conversation.getConversationId(), ttlSeconds, json);
            String indexKey = KEY_INDEX + conversation.getOperator();
            redisService.zSetAdd(indexKey, conversation.getConversationId(), conversation.getUpdateTime());
            redisService.expire(indexKey, ttlSeconds);
        } catch (Exception e) {
            log.warn("保存历史对话失败, conversationId={}, error={}", conversation.getConversationId(), e.getMessage());
        }
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

    /**
     * 保留天数实时读系统参数（管理页可改，改后即生效），非法配置回落默认值
     */
    private int retentionDays() {
        try {
            int days = sysConfigCacheService.getParamInt(ChatBiParam.CONVERSATION_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取问数对话保存天数参数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }

    private ChatBiConversationMessage message(String role, String content, Object spec, String status,
                                               String tableId, String portalName) {
        ChatBiConversationMessage item = new ChatBiConversationMessage();
        item.setRole(role);
        item.setContent(content == null ? "" : content);
        item.setSpec(spec);
        item.setStatus(StringUtils.hasText(status) ? status : "done");
        item.setTableId(tableId);
        item.setPortalName(portalName);
        item.setTime(System.currentTimeMillis());
        item.setMessageId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return item;
    }

    private ChatBiConversation listView(ChatBiConversation conversation) {
        ChatBiConversation view = new ChatBiConversation();
        view.setConversationId(conversation.getConversationId());
        view.setOperator(conversation.getOperator());
        view.setTitle(conversation.getTitle());
        view.setCreateTime(conversation.getCreateTime());
        view.setUpdateTime(conversation.getUpdateTime());
        view.setMessageCount(conversation.getMessages().size());
        return view;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= TITLE_MAX_CHARS ? text : text.substring(0, TITLE_MAX_CHARS) + "…";
    }
}
