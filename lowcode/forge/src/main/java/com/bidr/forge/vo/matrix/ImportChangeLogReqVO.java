package com.bidr.forge.vo.matrix;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 导入矩阵变更日志请求VO
 *
 * @author sharp
 * @since 2025-11-23
 */
@ApiModel(description = "导入矩阵变更日志请求VO")
@Data
public class ImportChangeLogReqVO {

    /**
     * 变更日志数据（JSON格式）
     */
    @ApiModelProperty(value = "变更日志数据", required = true)
    private String changeLogData;
}
