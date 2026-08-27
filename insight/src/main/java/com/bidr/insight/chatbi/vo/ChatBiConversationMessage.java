package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatBiConversationMessage
 * Description: 历史对话消息条目（user 提问 / assistant 回答）——恢复渲染所需的最小集：
 * 正文 + chart-spec 轻量指令（前端经 specMerge 重建图表/表格）+ 关联看板。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class ChatBiConversationMessage {

    @ApiModelProperty(value = "角色：user-用户提问 / assistant-助手回答")
    private String role;

    @ApiModelProperty(value = "消息正文（assistant 为剔除 chart-spec 后的回答全文；失败时为错误信息）")
    private String content;

    @ApiModelProperty(value = "assistant 的 chart-spec 编排指令（结构化对象，前端恢复渲染用；user/失败回复为空）")
    private Object spec;

    @ApiModelProperty(value = "状态：done-正常 / error-失败（失败回复恢复时按错误样式展示）")
    private String status;

    @ApiModelProperty(value = "本条消息关联的看板编码（恢复时图表取数与穿透表共享）")
    private String tableId;

    @ApiModelProperty(value = "看板名（全局模式路由命中名，恢复头部标识用）")
    private String portalName;

    @ApiModelProperty(value = "消息时间（毫秒时间戳）")
    private Long time;

    @ApiModelProperty(value = "消息标识（后端生成；assistant 消息经 SSE msgid 事件回传前端，评价时按它定位）")
    private String messageId;

    @ApiModelProperty(value = "用户评价：like-点赞 / dislike-点踩 / 空-未评价（仅 assistant 消息有值）")
    private String rating;
}
