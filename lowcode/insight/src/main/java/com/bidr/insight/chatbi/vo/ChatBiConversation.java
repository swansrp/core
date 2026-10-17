package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ChatBiConversation
 * Description: 历史对话（Redis 按访问人隔离存储，保留天数见系统参数
 * {@link com.bidr.insight.chatbi.constant.param.ChatBiParam#CONVERSATION_RETENTION_DAYS}）。
 * 列表视图不带 messages（前端按需走详情接口）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class ChatBiConversation {

    @ApiModelProperty(value = "对话标识（UUID，首问时后端创建并经 SSE conv 事件回传前端）")
    private String conversationId;

    @ApiModelProperty(value = "访问人（对话按人隔离，人名优先回落工号）")
    private String operator;

    @ApiModelProperty(value = "对话标题（首问摘要，截断 50 字）")
    private String title;

    @ApiModelProperty(value = "创建时间（毫秒时间戳）")
    private Long createTime;

    @ApiModelProperty(value = "最近活跃时间（毫秒时间戳，列表按它倒序）")
    private Long updateTime;

    @ApiModelProperty(value = "消息条数（列表视图展示用）")
    private Integer messageCount;

    @ApiModelProperty(value = "消息明细（列表视图不带，详情才有）")
    private List<ChatBiConversationMessage> messages = new ArrayList<>();
}
