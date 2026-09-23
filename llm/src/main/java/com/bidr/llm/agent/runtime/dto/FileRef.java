package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 消息附件引用（平台受理时复检）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "消息附件引用（平台受理时复检）")
@Data
public class FileRef {

    private String bucket;

    private String objectName;

    private String fileName;

    private Long fileSize;

    private String mimeType;
}
