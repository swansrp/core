package com.bidr.admin.vo.statistic;

import com.bidr.admin.config.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: PortalIndicatorVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/8 15:10
 */
@Data
public class PortalIndicatorGroupVO {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @PortalPidField
    @ApiModelProperty(value = "父级指标id")
    private Long pid;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "实体名称")
    private String portalName;

    @PortalNameField
    @ApiModelProperty(value = "指标名称")
    private String name;

    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer displayOrder;
}
