package com.bidr.oss.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: PartInfo
 * Description: 已上传分片信息（分片上传/断点续通用）
 * Copyright: Copyright (c) 2026 Company: plsintec Ltd.
 *
 * @author sharp
 * @since 2026/09/05 12:00
 */
@ApiModel(description = "已上传分片信息")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartInfo {

    @ApiModelProperty("分片号（从1开始）")
    private Integer partNumber;

    @ApiModelProperty("分片标识（etag）")
    private String etag;
}
