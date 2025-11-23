package com.bidr.forge.vo.matrix;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 导出矩阵变更日志请求VO
 *
 * @author sharp
 * @since 2025-11-23
 */
@ApiModel(description = "导出矩阵变更日志请求VO")
@Data
public class ExportChangeLogReqVO {

    /**
     * 矩阵ID
     */
    @ApiModelProperty(value = "矩阵ID", required = true)
    private Long matrixId;
}
