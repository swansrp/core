package com.bidr.llm.agent.runtime.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 预签名直传凭据（POST /files/upload-url）
 *
 * @author sharp
 * @since 2026/9/22
 */
@ApiModel(description = "预签名直传凭据（POST /files/upload-url）")
@Data
public class UploadUrlResult {

    private String uploadUrl;

    private String bucket;

    private String objectName;
}
