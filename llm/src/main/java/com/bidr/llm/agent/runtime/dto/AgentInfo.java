package com.bidr.llm.agent.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 平台 Agent 条目（GET /agents）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "平台 Agent 条目（GET /agents）")
@Data
public class AgentInfo {

    @ApiModelProperty(value = "Agent 公开唯一标识")
    private String agentCode;

    @ApiModelProperty(value = "展示名")
    private String agentName;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "图标")
    private String icon;

    @ApiModelProperty(value = "是否平台共享")
    @JsonProperty("is_system")
    private Boolean system;
}
