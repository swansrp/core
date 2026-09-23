package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.Map;

/**
 * 平台会话（详情与列表共用：列表项附带 messageCount/lastMessage）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "平台会话（详情与列表共用：列表项附带 messageCount/lastMessage）")
@Data
public class SessionInfo {

    @ApiModelProperty(value = "平台会话ID")
    private String sessionId;

    @ApiModelProperty(value = "绑定的 Agent 编码")
    private String agentCode;

    @ApiModelProperty(value = "会话名（平台侧创建即冻结）")
    private String name;

    @ApiModelProperty(value = "业务分组键")
    private String groupKey;

    @ApiModelProperty(value = "透传元数据（平台不解析）")
    private Map<String, Object> metadata;

    @ApiModelProperty(value = "创建时间")
    private String createdAt;

    @ApiModelProperty(value = "更新时间")
    private String updatedAt;

    @ApiModelProperty(value = "轮次数（仅列表）")
    private Integer messageCount;

    @ApiModelProperty(value = "末轮预览（仅列表）")
    private Object lastMessage;
}
