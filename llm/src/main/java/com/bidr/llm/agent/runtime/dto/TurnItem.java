package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 轮次聚合视图（§5.6；reply 未产生时为 null）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "轮次聚合视图（§5.6；reply 未产生时为 null）")
@Data
public class TurnItem {

    private String messageId;

    private String userContent;

    /** 用户消息附件（历史装载回填 chips） */
    private List<FileRef> userFiles;

    private String createdAt;

    private Reply reply;

    /** 失败详情（列表可能缺省，单查必带） */
    private String error;

    /**
     * 回复（未产生时 null）
     */
    @Data
    public static class Reply {

        private String content;

        private List<TurnBlock> blocks;

        /** completed / failed / cancelled */
        private String status;

        private Object usage;

        private String createdAt;
    }
}
