package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 轮次列表页
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "轮次列表页")
@Data
public class TurnPage {

    private List<TurnItem> items;

    private Boolean hasMore;

    private String nextBefore;
}
