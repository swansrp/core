package com.bidr.insight.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ChatBiRatingStatRes
 * Description: 评价运营统计响应——筛选条件作用于列表，汇总随筛选联动（运营看"差评集中在哪块"更直观）。
 * 记录为评价时点的快照（提问/回答摘要/看板），原对话过期也不影响统计回看
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class ChatBiRatingStatRes {

    @ApiModelProperty(value = "筛选后评价总数")
    private int total;

    @ApiModelProperty(value = "筛选后点赞数")
    private int likeCount;

    @ApiModelProperty(value = "筛选后点踩数")
    private int dislikeCount;

    @ApiModelProperty(value = "评价明细（评价时间新→旧）")
    private List<Record> records = new ArrayList<>();

    @Data
    public static class Record {

        @ApiModelProperty(value = "评价标识（conversationId:messageId）")
        private String ratingId;

        @ApiModelProperty(value = "对话标识")
        private String conversationId;

        @ApiModelProperty(value = "被评价消息标识")
        private String messageId;

        @ApiModelProperty(value = "评价人（提问访问人）")
        private String operator;

        @ApiModelProperty(value = "本轮用户提问（摘要）")
        private String question;

        @ApiModelProperty(value = "被评价的回答正文（摘要）")
        private String answer;

        @ApiModelProperty(value = "评价：like-点赞 / dislike-点踩")
        private String rating;

        @ApiModelProperty(value = "回答关联看板编码")
        private String tableId;

        @ApiModelProperty(value = "回答关联看板名")
        private String portalName;

        @ApiModelProperty(value = "回答时间（毫秒时间戳，看板/提问等维度取自该条消息）")
        private Long messageTime;

        @ApiModelProperty(value = "评价时间（毫秒时间戳）")
        private Long ratingTime;
    }
}
