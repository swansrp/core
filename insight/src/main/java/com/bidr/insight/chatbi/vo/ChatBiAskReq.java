package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * Title: ChatBiAskReq
 * Description: 智能问数提问请求（SSE 流式返回）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChatBiAskReq {

    @ApiModelProperty(value = "表格code（sys_portal_table.table_code，即图表指标挂载的 tableId）")
    @NotBlank(message = "tableId不能为空")
    private String tableId;

    @ApiModelProperty(value = "用户问题")
    @NotBlank(message = "question不能为空")
    private String question;

    @ApiModelProperty(value = "前端编排的完整 system 提示词（开发调试模式，前端热更新迭代）；为空则后端按语义目录拼装")
    private String systemPrompt;

    @ApiModelProperty(value = "历史对话标识（续问传上一轮回传值；空=新对话，后端创建并经 SSE conv 事件回传）")
    private String conversationId;

    @ApiModelProperty(value = "看板名（全局模式路由命中的看板名透传，历史对话恢复头部标识用；可空）")
    private String portalName;

    @ApiModelProperty(value = "最近对话历史（服务端最多取最近5轮）")
    private List<ChatBiHistoryItem> history;
}
