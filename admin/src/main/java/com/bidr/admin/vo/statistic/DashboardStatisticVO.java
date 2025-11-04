package com.bidr.admin.vo.statistic;

import com.bidr.admin.config.*;
import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: DashboardStatisticVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/15 10:08
 */
@Data
public class DashboardStatisticVO {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @PortalPidField
    @ApiModelProperty(value = "pid")
    private Long pid;

    @PortalDisplayNoneField
    @PortalEntityField(field = "id")
    @ApiModelProperty(value = "key")
    private Long key;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "表id")
    private String tableId;

    @PortalNameField
    @ApiModelProperty(value = "显示名称")
    private String title;

    @BindField(entity = AcUser.class, field = "name", condition = "this.customerNumber = customer_number")
    @ApiModelProperty(value = "所属人")
    private String customerNumber;

    @PortalOrderField
    @ApiModelProperty(value = "指标树顺序")
    private Integer order;

    @PortalTextAreaField
    @ApiModelProperty(value = "指标配置")
    private String indicator;

    @ApiModelProperty(value = "默认横向网格数")
    private Integer defaultXGrid;

    @ApiModelProperty(value = "默认纵向网格数")
    private Integer defaultYGrid;
}
