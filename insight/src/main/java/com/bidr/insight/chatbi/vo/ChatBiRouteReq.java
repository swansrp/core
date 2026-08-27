package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * Title: ChatBiRouteReq
 * Description: 看板路由请求（按问题选择最相关看板）——全局模式每次提问都重路由：
 * 携带当前看板与最近对话，模型结合上下文判断话题延续还是切板
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Data
public class ChatBiRouteReq {

    @ApiModelProperty(value = "用户问题")
    @NotBlank(message = "question不能为空")
    private String question;

    @ApiModelProperty(value = "当前看板 tableId（话题延续判断用，可空）")
    private String currentTableId;

    @ApiModelProperty(value = "最近对话（user/assistant 正文，用于理解指代与话题延续）")
    private List<ChatBiHistoryItem> history;
}
