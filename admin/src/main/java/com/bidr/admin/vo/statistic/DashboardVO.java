package com.bidr.admin.vo.statistic;

import com.bidr.admin.config.PortalDisplayNoneField;
import com.bidr.admin.config.PortalEntityField;
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DashboardVO {

    @PortalIdField
    private String id;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "数据id")
    private Long statisticId;

    @PortalEntityField(field = "case when sys_portal_dashboard_statistic.customer_number is null then 1 else 0 end")
    @ApiModelProperty("是否是通用指标")
    private String commonStatistic;

    @ApiModelProperty(value = "数据名称")
    @PortalEntityField(entity = SysPortalDashboardStatistic.class, alias = "sys_portal_dashboard_statistic",
            field = "title")
    private String title;

    @ApiModelProperty(value = "图表指标")
    @PortalEntityField(entity = SysPortalDashboardStatistic.class, alias = "sys_portal_dashboard_statistic",
            field = "indicator")
    private String indicator;

    @JsonProperty("xPosition")
    @ApiModelProperty(value = "图表横坐标")
    private Integer xPosition;

    @JsonProperty("yPosition")
    @ApiModelProperty(value = "图表纵坐标")
    private Integer yPosition;

    @ApiModelProperty(value = "图表宽度")
    @JsonProperty("xGrid")
    private Integer xGrid;

    @ApiModelProperty(value = "图表高度")
    @JsonProperty("yGrid")
    private Integer yGrid;
}
