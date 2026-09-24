package com.bidr.agent.runtime.dto;

import com.bidr.llm.agent.runtime.dto.FileRef;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 发消息请求（relay 入参；clientKey 为**歧义窗口幂等键**——未收到 accepted 前失败时同键重试复用同一轮次）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "发消息请求")
@Data
public class AgentChatSendReq {

    @ApiModelProperty(value = "内容（≤65536）")
    private String content;

    @ApiModelProperty(value = "幂等键（不传由 relay 生成；重试须复用同键）")
    private String clientKey;

    @ApiModelProperty(value = "附件引用（仅上传成功项）")
    private List<FileRef> files;
}
