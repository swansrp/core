package com.bidr.insight.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Title: ChatBiRateReq
 * Description: 助手回答评价请求——双写：对话正文内嵌 rating（本人恢复回显）
 * + 全局评价索引（跨访问人聚合，运营统计筛选）。
 * rating 空白视为取消评价（移除全局记录，正文 rating 置空）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class ChatBiRateReq {

    @ApiModelProperty(value = "对话标识")
    @NotBlank(message = "conversationId不能为空")
    private String conversationId;

    @ApiModelProperty(value = "消息标识（定位被评价的 assistant 消息；空=该对话最后一条助手回复）")
    private String messageId;

    @ApiModelProperty(value = "评价：like-点赞 / dislike-点踩 / 空-取消")
    private String rating;
}
