package com.bidr.agent.runtime.dto;

import com.bidr.llm.agent.runtime.dto.SessionInfo;
import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.util.List;

/**
 * 平台会话列表页（keyset 分页，next_before 为前翻游标）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "平台会话列表页（keyset 分页，next_before 为前翻游标）")
@Data
public class SessionPage {

    private List<SessionInfo> items;

    private Boolean hasMore;

    private String nextBefore;
}
