package com.bidr.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 建会话请求（relay 入参；groupKey/业务绑定由业务扩展点解析）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "建会话请求")
@Data
public class AgentSessionCreateReq {

    @ApiModelProperty(value = "Agent 编码（未传取业务默认）")
    private String agentCode;

    @ApiModelProperty(value = "会话标题（平台创建即冻结，后续标题只在本侧维护）")
    private String title;

    @ApiModelProperty(value = "业务分组键")
    private String groupKey;

    @ApiModelProperty(value = "业务类型（业务绑定维度）")
    private String businessType;

    @ApiModelProperty(value = "业务主键（业务绑定维度）")
    private String businessId;
}
