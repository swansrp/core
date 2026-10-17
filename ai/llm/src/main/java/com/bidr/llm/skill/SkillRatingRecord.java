package com.bidr.llm.skill;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: SkillRatingRecord
 * Description: skill 回答评价快照——评价时点的提问/回答摘要（截断存）与评价人，原对话过期也不影响统计回看。
 * ratingId = conversationId:messageId（业务方自定，同一回答重复评价原地覆盖）。
 * ext 存业务维度键值（如 chatbi 的 tableId/portalName），统计筛选走精确匹配。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class SkillRatingRecord {

    /**
     * 评价标识（业务方自定，建议 conversationId:messageId）
     */
    private String ratingId;

    /**
     * 对话标识
     */
    private String conversationId;

    /**
     * 被评价消息标识
     */
    private String messageId;

    /**
     * 评价人（提问访问人）
     */
    private String operator;

    /**
     * 本轮用户提问（摘要）
     */
    private String question;

    /**
     * 被评价的回答正文（摘要）
     */
    private String answer;

    /**
     * 评价：like-点赞 / dislike-点踩
     */
    private String rating;

    /**
     * 回答时间（毫秒时间戳）
     */
    private Long messageTime;

    /**
     * 评价时间（毫秒时间戳）
     */
    private Long ratingTime;

    /**
     * 业务维度扩展键值（如 chatbi 的 tableId/portalName），筛选走精确匹配
     */
    private Map<String, String> ext = new LinkedHashMap<>();
}
