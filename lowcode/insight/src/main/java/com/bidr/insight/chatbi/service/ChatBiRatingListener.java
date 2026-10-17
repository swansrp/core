package com.bidr.insight.chatbi.service;

import com.bidr.insight.chatbi.constant.param.ChatBiParam;
import com.bidr.insight.chatbi.flow.ChatBiRouteFlowDefinition;
import com.bidr.insight.chatbi.vo.ChatBiConversation;
import com.bidr.insight.chatbi.vo.ChatBiConversationMessage;
import com.bidr.llm.skill.AgentRatingListener;
import com.bidr.llm.skill.SkillRatingRecord;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Title: ChatBiRatingListener
 * Description: chatbi 评价动作钩子（承接 llm 通用评价端点 /web/api/agent/rating/save）——
 * 补 ChatBI 业务语义：评价双写对话正文（{@link ChatBiConversationService#rateMessage}，本人恢复回显），
 * 并按对话内容组装评价记录（提问/回答摘要，看板维度走 ext）；底座入库与运营统计全走 llm 通用端点。
 * <p>
 * ratingId = conversationId:messageId（钩子按 ':' 拆分定位）；保留天数与对话共用
 * {@link ChatBiParam#CONVERSATION_RETENTION_DAYS}（评价比对话活得久没有意义）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
@Component
public class ChatBiRatingListener implements AgentRatingListener {

    /**
     * ext 里的看板维度键（统计筛选按 tableId 精确匹配）
     */
    private static final String EXT_TABLE_ID = "tableId";

    /**
     * ext 里的看板名键（展示用，不参与筛选）
     */
    private static final String EXT_PORTAL_NAME = "portalName";

    private static final int DEFAULT_RETENTION_DAYS = 10;

    private final SysConfigCacheService sysConfigCacheService;

    private final ChatBiConversationService conversationService;

    public ChatBiRatingListener(SysConfigCacheService sysConfigCacheService,
                                ChatBiConversationService conversationService) {
        this.sysConfigCacheService = sysConfigCacheService;
        this.conversationService = conversationService;
    }

    @Override
    public boolean supports(String skillCode) {
        return ChatBiRouteFlowDefinition.SKILL_CODE.equals(skillCode);
    }

    /**
     * 评价入库前置：先内嵌进对话正文（定位失败直接抛出，端点透传失败不落底座），
     * 再按对话内容组装评价记录返回给端点入库
     */
    @Override
    public SkillRatingRecord beforeRate(String skillCode, String ratingId, String rating, String operator) {
        String[] parts = splitRatingId(ratingId);
        ChatBiConversation conversation = conversationService.rateMessage(parts[0], parts[1], rating, operator);
        ChatBiConversationMessage target = findTarget(conversation, parts[1]);
        SkillRatingRecord record = new SkillRatingRecord();
        // messageId 入参可空（=最近一条回答），ratingId 按定位后的真实消息回填，保证取消时能对上
        record.setRatingId(conversation.getConversationId() + ":" + target.getMessageId());
        record.setConversationId(conversation.getConversationId());
        record.setMessageId(target.getMessageId());
        record.setOperator(conversation.getOperator());
        record.setQuestion(findQuestionBefore(conversation, target));
        record.setAnswer(target.getContent());
        record.setRating(rating);
        if (StringUtils.hasText(target.getTableId())) {
            record.getExt().put(EXT_TABLE_ID, target.getTableId());
        }
        if (StringUtils.hasText(target.getPortalName())) {
            record.getExt().put(EXT_PORTAL_NAME, target.getPortalName());
        }
        record.setMessageTime(target.getTime());
        record.setRatingTime(System.currentTimeMillis());
        return record;
    }

    /**
     * 取消评价前置：清掉对话正文内嵌 rating（定位失败直接抛出，端点透传失败不移除底座记录）
     */
    @Override
    public void beforeRemove(String skillCode, String ratingId, String operator) {
        String[] parts = splitRatingId(ratingId);
        conversationService.rateMessage(parts[0], parts[1], null, operator);
    }

    /**
     * 保留天数实时读系统参数（与对话共用），非法配置回落默认值
     */
    @Override
    public int retentionDays(String skillCode) {
        try {
            int days = sysConfigCacheService.getParamInt(ChatBiParam.CONVERSATION_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取问数评价保存天数参数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }

    /**
     * ratingId 按 ':' 拆成 conversationId/messageId（格式由前端与 chatbi 共同约定；
     * messageId 段允许为空，=最近一条回答，与旧端点口径一致）
     */
    private String[] splitRatingId(String ratingId) {
        int index = ratingId == null ? -1 : ratingId.indexOf(':');
        if (index <= 0) {
            throw new IllegalArgumentException("ratingId 格式非法（期望 conversationId:messageId）：" + ratingId);
        }
        return new String[]{ratingId.substring(0, index), ratingId.substring(index + 1)};
    }

    /**
     * 从改写后的对话里复找被评价消息（rateMessage 已按 messageId 定位并写入 rating）
     */
    private ChatBiConversationMessage findTarget(ChatBiConversation conversation, String messageId) {
        for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
            ChatBiConversationMessage item = conversation.getMessages().get(i);
            if ("assistant".equals(item.getRole())
                    && (!StringUtils.hasText(messageId) || messageId.equals(item.getMessageId()))) {
                return item;
            }
        }
        // rateMessage 已保证能定位到，这里仅为编译器兜底
        throw new IllegalArgumentException("未找到被评价的回答");
    }

    /**
     * 该轮提问：被评价消息之前最近一条 user 消息正文（运营定位"什么问题被点了踩"）
     */
    private String findQuestionBefore(ChatBiConversation conversation, ChatBiConversationMessage target) {
        int index = conversation.getMessages().indexOf(target);
        for (int i = index - 1; i >= 0; i--) {
            ChatBiConversationMessage item = conversation.getMessages().get(i);
            if ("user".equals(item.getRole())) {
                return item.getContent();
            }
        }
        return "";
    }
}
