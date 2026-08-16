package com.bidr.insight.service;

import com.bidr.insight.constant.param.ChatBiParam;
import com.bidr.insight.flow.ChatBiRouteFlowDefinition;
import com.bidr.insight.vo.ChatBiConversation;
import com.bidr.insight.vo.ChatBiConversationMessage;
import com.bidr.insight.vo.ChatBiRateReq;
import com.bidr.insight.vo.ChatBiRatingStatRes;
import com.bidr.llm.skill.SkillRatingFilter;
import com.bidr.llm.skill.SkillRatingRecord;
import com.bidr.llm.skill.SkillRatingService;
import com.bidr.llm.skill.SkillRatingStatRes;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Title: ChatBiRatingService
 * Description: 回答评价运营薄封装——存储与统计已下沉 llm skill 底座
 * （{@link SkillRatingService}，key llm:skill:rating:chatbi:*），本类只补 ChatBI 业务语义：
 * 评价动作双写——对话正文内嵌 rating（{@link ChatBiConversationService#rateMessage}，本人恢复回显）
 * + skill 底座全局快照（提问/回答摘要，看板维度走 ext）。
 * <p>
 * ratingId = conversationId:messageId，同一回答重复评价原地覆盖，取消评价移除全局记录。
 * 保留天数与对话共用 {@link ChatBiParam#CONVERSATION_RETENTION_DAYS}（评价比对话活得久没有意义）。
 * 统计筛选的看板维度经 extEquals 透传（tableId/portalName 存 ext）。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class ChatBiRatingService {

    /**
     * ext 里的看板维度键（筛选按 tableId 精确匹配）
     */
    private static final String EXT_TABLE_ID = "tableId";

    /**
     * ext 里的看板名键（展示用，不参与筛选）
     */
    private static final String EXT_PORTAL_NAME = "portalName";

    private static final int DEFAULT_RETENTION_DAYS = 10;

    private final SkillRatingService skillRatingService;

    private final SysConfigCacheService sysConfigCacheService;

    private final ChatBiConversationService conversationService;

    public ChatBiRatingService(SkillRatingService skillRatingService, SysConfigCacheService sysConfigCacheService,
                               ChatBiConversationService conversationService) {
        this.skillRatingService = skillRatingService;
        this.sysConfigCacheService = sysConfigCacheService;
        this.conversationService = conversationService;
    }

    /**
     * 评价助手回复：先内嵌进对话正文（定位失败直接抛出），再同步 skill 底座全局快照（like/dislike 覆盖写，空白=取消移除）
     */
    public void rate(ChatBiRateReq req, String operator) {
        String rating = normalizeRating(req.getRating());
        ChatBiConversation conversation = conversationService.rateMessage(
                req.getConversationId(), req.getMessageId(), rating, operator);
        ChatBiConversationMessage target = findTarget(conversation, req.getMessageId());
        String ratingId = conversation.getConversationId() + ":" + target.getMessageId();
        if (rating == null) {
            skillRatingService.remove(ChatBiRouteFlowDefinition.SKILL_CODE, ratingId);
            return;
        }
        SkillRatingRecord record = new SkillRatingRecord();
        record.setRatingId(ratingId);
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
        skillRatingService.save(ChatBiRouteFlowDefinition.SKILL_CODE, record, retentionDays());
    }

    /**
     * 运营统计：经 skill 底座筛选聚合（类型/评价人/时间段/关键词通用 + 看板 extEquals），汇总随筛选联动
     */
    public ChatBiRatingStatRes listRatings(String rating, String tableId, String operator,
                                           Long startTime, Long endTime, String keyword) {
        SkillRatingFilter filter = new SkillRatingFilter();
        filter.setRating(rating);
        filter.setOperator(operator);
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        filter.setKeyword(keyword);
        if (StringUtils.hasText(tableId)) {
            filter.getExtEquals().put(EXT_TABLE_ID, tableId);
        }
        return toStatRes(skillRatingService.list(ChatBiRouteFlowDefinition.SKILL_CODE, filter));
    }

    /**
     * rating 归一化：like/dislike 二值，空白=取消（null），其余取值视为非法
     */
    private String normalizeRating(String rating) {
        if (!StringUtils.hasText(rating)) {
            return null;
        }
        if (!"like".equals(rating) && !"dislike".equals(rating)) {
            throw new IllegalArgumentException("评价取值非法：" + rating);
        }
        return rating;
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

    /**
     * 底座通用响应转 ChatBI 统计结构（看板维度从 ext 还原，前端结构不变）
     */
    private ChatBiRatingStatRes toStatRes(SkillRatingStatRes source) {
        ChatBiRatingStatRes result = new ChatBiRatingStatRes();
        result.setTotal(source.getTotal());
        result.setLikeCount(source.getLikeCount());
        result.setDislikeCount(source.getDislikeCount());
        for (SkillRatingRecord item : source.getRecords()) {
            result.getRecords().add(toRecord(item));
        }
        return result;
    }

    private ChatBiRatingStatRes.Record toRecord(SkillRatingRecord source) {
        ChatBiRatingStatRes.Record record = new ChatBiRatingStatRes.Record();
        record.setRatingId(source.getRatingId());
        record.setConversationId(source.getConversationId());
        record.setMessageId(source.getMessageId());
        record.setOperator(source.getOperator());
        record.setQuestion(source.getQuestion());
        record.setAnswer(source.getAnswer());
        record.setRating(source.getRating());
        record.setTableId(source.getExt() == null ? null : source.getExt().get(EXT_TABLE_ID));
        record.setPortalName(source.getExt() == null ? null : source.getExt().get(EXT_PORTAL_NAME));
        record.setMessageTime(source.getMessageTime());
        record.setRatingTime(source.getRatingTime());
        return record;
    }

    /**
     * 保留天数实时读系统参数（与对话共用），非法配置回落默认值
     */
    private int retentionDays() {
        try {
            int days = sysConfigCacheService.getParamInt(ChatBiParam.CONVERSATION_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取问数评价保存天数参数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
