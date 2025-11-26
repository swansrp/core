package com.bidr.forge.vo.portal;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 生成Portal配置请求VO
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "生成Portal配置请求")
@Data
public class GeneratePortalReq {

    @ApiModelProperty(value = "Portal名称", required = true)
    @NotBlank(message = "Portal名称不能为空")
    private String portalName;

    @ApiModelProperty(value = "数据模式:MATRIX/DATASET", required = true)
    @NotBlank(message = "数据模式不能为空")
    private String dataMode;

    @ApiModelProperty(value = "Matrix表ID（MATRIX模式必填）")
    private Long matrixId;

    @ApiModelProperty(value = "Dataset ID（DATASET模式必填）")
    private Long datasetId;

    @ApiModelProperty(value = "Portal中文显示名称")
    private String displayName;

    @ApiModelProperty(value = "URL路径（默认使用portalName）")
    private String url;

    @ApiModelProperty(value = "Bean名称（默认使用portalName + PortalController）")
    private String bean;
}
