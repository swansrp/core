package com.bidr.oss.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: UploadRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 23:41
 */
@Data
public class UploadRes {
    @ApiModelProperty("文件路径")
    private String fileName;
    @ApiModelProperty("文件大小")
    private Long fileSize;
    @ApiModelProperty("文件地址")
    private String url;
}
