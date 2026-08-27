package com.bidr.insight.chatbi.vo;

import com.bidr.llm.flow.LlmHistoryMessage;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Title: ChatBiHistoryItem
 * Description: 智能问数对话历史条目（实现 {@link LlmHistoryMessage}，llm 结点零转换读取历史）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChatBiHistoryItem implements LlmHistoryMessage {

    @ApiModelProperty(value = "角色：user-用户提问 / assistant-助手回答")
    @NotBlank(message = "role不能为空")
    private String role;

    @ApiModelProperty(value = "消息内容（assistant 只保留正文，不含 chart-spec 代码块）")
    @NotBlank(message = "content不能为空")
    private String content;
}
