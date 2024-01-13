package com.bidr.platform.vo.portal;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * Title: PortalReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:12
 */
@Data
public class PortalCopyReq {
    @NotEmpty(message = "源表格id不能为空")
    @ApiModelProperty("源表格id")
    private Long sourceConfigId;

    @NotEmpty(message = "目标表格名称不能为空")
    @ApiModelProperty("目标表格编码")
    private String targetName;

    @NotEmpty(message = "目标表格名称不能为空")
    @ApiModelProperty("目标表格编码")
    private String targetDisplayName;
}
