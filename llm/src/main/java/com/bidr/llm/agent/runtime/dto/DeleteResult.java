package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 软删会话回执（DELETE /sessions/{sid}）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "软删会话回执（DELETE /sessions/{sid}）")
@Data
public class DeleteResult {

    private String sessionId;

    private Boolean deleted;
}
