package com.bidr.llm.agent.conversation;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Title: AgentConversationService
 * Description: agent 通用历史对话 Redis 存储（自 ChatBiConversationService 上提，业务零绑定）——
 * 按访问人建 zset 索引（member=conversationId，score=最近活跃时间），整条对话 JSON 存独立 key，
 * 保留天数经 {@link AgentConversationRetentionProvider} SPI 注入（无实现回落 10 天），
 * 超期由 Redis TTL 自动清理，每次追加消息同步续期（滚动窗口）。
 * <p>
 * 对话按统一注册中心 agentCode 归组：列表默认跨 agent 聚合（前端历史统一展示），
 * 可按 agentCode 过滤。业务恢复渲染负载走消息 {@link AgentConversationMessage#getExt()}，
 * llm 不感知其结构。
 * 写入时机：用户提问即存（{@link #appendUser}，新对话在此创建并返回 id），
 * 助手回复由业务链路收口时补写（{@link #appendAssistant}）。
 * 与轨迹存储同理：读-改-写有丢更新窗口，写路径保留 synchronized；
 * 对话是辅助链路，未接入 Redis 或异常只记日志不打断业务主流程。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Slf4j
@Service
public class AgentConversationService {

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
     * 保留天数 SPI 缺失/非法时的回落值
     */
    private static final int DEFAULT_RETENTION_DAYS = 10;

    /**
     * 对话正文 key 前缀：llm:agent:conversation:{conversationId}
     */
    private static final String KEY_DETAIL = "llm:agent:conversation:";

    /**
     * 访问人索引 key 前缀：llm:agent:conversation:index:{operator}（zset，score=updateTime）
     */
    private static final String KEY_INDEX = "llm:agent:conversation:index:";

    private final ObjectProvider<RedisService> redisProvider;

    private final ObjectProvider<AgentConversationRetentionProvider> retentionProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentConversationService(ObjectProvider<RedisService> redisProvider,
                                    ObjectProvider<AgentConversationRetentionProvider> retentionProvider) {
        this.redisProvider = redisProvider;
        this.retentionProvider = retentionProvider;
    }

    /**
     * 追加用户提问（提问即存）：conversationId 为空则创建新对话（标题=首问摘要），
     * 返回实际对话 id（调用方回传前端续接）。未接入 Redis 时仍返回对话 id（本轮不落盘）
     */
    public synchronized String appendUser(String agentCode, String conversationId, String operator,
                                          String content, Map<String, Object> ext) {
        AgentConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            conversation = new AgentConversation();
            conversation.setConversationId(UUID.randomUUID().toString());
            conversation.setAgentCode(StringUtils.hasText(agentCode) ? agentCode.trim() : null);
            conversation.setOperator(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR);
            conversation.setTitle(truncate(content));
            conversation.setCreateTime(System.currentTimeMillis());
        }
        append(conversation, message("user", content, "done", ext));
        return conversation.getConversationId();
    }

    /**
     * 追加助手回复（业务链路收口时调用）：失败/停止回复带对应状态，恢复时按状态样式展示；
     * 业务负载（如 chart-spec、问数完整 payload）经 ext 透传。
     * 返回消息标识（对话不存在时返回 null），评价时按它定位
     */
    public synchronized String appendAssistant(String conversationId, String content,
                                               String status, Map<String, Object> ext) {
        AgentConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            // 对话正文已过期（TTL 先到）或 id 非法：放弃本轮回复，不重建（避免凭空造出无提问的对话）
            log.debug("追加助手回复时对话不存在, conversationId={}", conversationId);
            return null;
        }
        AgentConversationMessage item = message("assistant", content, status, ext);
        append(conversation, item);
        return item.getMessageId();
    }

    /**
     * 评价助手回复（仅本人对话）：定位被评价消息（messageId 空=最后一条 assistant）→ 内嵌 rating → 保存。
     * 返回改写后的整条对话；对话过期/越权/找不到消息抛 IllegalArgumentException
     */
    public synchronized AgentConversation rateMessage(String conversationId, String messageId,
                                                      String rating, String operator) {
        AgentConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在或已过期");
        }
        if (!conversation.getOperator().equals(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("只能评价自己的对话");
        }
        boolean found = false;
        for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
            AgentConversationMessage item = conversation.getMessages().get(i);
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
     * 历史对话列表（新→旧）：默认按访问人隔离只看本人；跨 agent 聚合，agentCode 非空则过滤单 agent。
     * 列表视图不带消息明细；索引里正文已过期的成员（TTL 先到）顺手清掉
     */
    public List<AgentConversation> listConversations(String operator, String agentCode) {
        return listConversations(operator, agentCode, false);
    }

    /**
     * 历史对话列表（发起人作用域可选）：
     * <ul>
     *     <li>allScope=false：按访问人隔离只看本人（用户场景默认）；</li>
     *     <li>allScope=true：跨发起人聚合（管理视图）——扫全部发起人索引，列表项自带 operator 供前端区分归属。
     *     管理端点专用；删除/评价仍限本人对话（服务端越权拦截不变）</li>
     * </ul>
     */
    public List<AgentConversation> listConversations(String operator, String agentCode, boolean allScope) {
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return new ArrayList<>();
        }
        List<String> indexKeys = new ArrayList<>();
        if (allScope) {
            Set<String> scanned = redis.keysByPattern(KEY_INDEX + "*");
            if (scanned != null) {
                indexKeys.addAll(scanned);
            }
        } else {
            indexKeys.add(KEY_INDEX + (StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR));
        }
        List<AgentConversation> result = new ArrayList<>();
        for (String indexKey : indexKeys) {
            Set<String> conversationIds = redis.zSetRange(indexKey, 0, -1, String.class);
            if (conversationIds == null || conversationIds.isEmpty()) {
                continue;
            }
            Set<String> stale = new HashSet<>();
            for (String conversationId : conversationIds) {
                AgentConversation conversation = getConversation(conversationId);
                if (conversation == null) {
                    stale.add(conversationId);
                    continue;
                }
                if (StringUtils.hasText(agentCode) && !agentCode.trim().equals(conversation.getAgentCode())) {
                    continue;
                }
                result.add(listView(conversation));
            }
            if (!stale.isEmpty()) {
                redis.zSetRemoveBySet(indexKey, stale);
            }
        }
        // 显式按最近活跃倒序（新→旧），不依赖 zset 返回顺序（跨索引聚合时无序可言）
        result.sort(Comparator.comparingLong(AgentConversation::getUpdateTime).reversed());
        return result;
    }

    public AgentConversation getConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            String json = redis.get(KEY_DETAIL + conversationId, String.class);
            return json == null ? null : objectMapper.readValue(json, AgentConversation.class);
        } catch (Exception e) {
            log.warn("读取历史对话失败, conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除对话（仅能删自己的，防横向越权）；正文+索引成员一并清理
     */
    public void deleteConversation(String conversationId, String operator) {
        AgentConversation conversation = getConversation(conversationId);
        if (conversation == null) {
            return;
        }
        if (!conversation.getOperator().equals(StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("只能删除自己的对话");
        }
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        redis.delete(KEY_DETAIL + conversationId);
        redis.zSetRemoveBySet(KEY_INDEX + conversation.getOperator(), Collections.singleton(conversationId));
    }

    /**
     * 追加一条消息并落库：软上限截断旧消息，TTL=保留天数，索引 zset 同步续期（滚动窗口）
     */
    private void append(AgentConversation conversation, AgentConversationMessage item) {
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
     * 整条对话写正文 key（TTL=保留天数）+ 访问人索引 zset（同步续期）；未接入 Redis 时静默跳过
     */
    private void save(AgentConversation conversation) {
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(conversation);
            int ttlSeconds = retentionDays() * 24 * 3600;
            redis.set(KEY_DETAIL + conversation.getConversationId(), ttlSeconds, json);
            String indexKey = KEY_INDEX + conversation.getOperator();
            redis.zSetAdd(indexKey, conversation.getConversationId(), conversation.getUpdateTime());
            redis.expire(indexKey, ttlSeconds);
        } catch (Exception e) {
            log.warn("保存历史对话失败, conversationId={}, error={}", conversation.getConversationId(), e.getMessage());
        }
    }

    /**
     * 保留天数实时读 SPI（无实现或非法配置回落默认值）
     */
    private int retentionDays() {
        AgentConversationRetentionProvider provider = retentionProvider.getIfAvailable();
        if (provider == null) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            int days = provider.retentionDays();
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取对话保留天数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }

    private AgentConversationMessage message(String role, String content, String status, Map<String, Object> ext) {
        AgentConversationMessage item = new AgentConversationMessage();
        item.setRole(role);
        item.setContent(content == null ? "" : content);
        item.setStatus(StringUtils.hasText(status) ? status : "done");
        if (ext != null) {
            item.getExt().putAll(ext);
        }
        item.setTime(System.currentTimeMillis());
        item.setMessageId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return item;
    }

    private AgentConversation listView(AgentConversation conversation) {
        AgentConversation view = new AgentConversation();
        view.setConversationId(conversation.getConversationId());
        view.setAgentCode(conversation.getAgentCode());
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
