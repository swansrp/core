package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 场记块（reply.blocks 元素：text / tool_call / tool_result / subagent 容器）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "场记块（reply.blocks 元素：text / tool_call / tool_result / subagent 容器）")
@Data
public class TurnBlock {

    private String type;

    private String content;

    private String toolName;

    private Object arguments;

    private String output;

    private String toolCallId;

    private String runId;

    private String agentCode;

    private String status;

    /** 子运行容器块递归（type=subagent） */
    private List<TurnBlock> blocks;
}
