package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 取消回执（POST …/{mid}/cancel）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "取消回执（POST …/{mid}/cancel）")
@Data
public class CancelResult {

    private String messageId;

    private Boolean cancelled;
}
