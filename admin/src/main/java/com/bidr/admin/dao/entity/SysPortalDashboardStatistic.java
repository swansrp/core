package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 通用仪表盘数据
 */
@ApiModel(description = "通用仪表盘数据")
@Data
@TableName(value = "sys_portal_dashboard_statistic")
public class SysPortalDashboardStatistic {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * pid
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "pid")
    private Long pid;

    /**
     * 表名称
     */
    @TableField(value = "table_id")
    @ApiModelProperty(value = "表名称")
    private String tableId;

    /**
     * 显示名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "显示名称")
    private String title;

    /**
     * 所属人
     */
    @TableField(value = "customer_number")
    @ApiModelProperty(value = "所属人")
    private String customerNumber;

    /**
     * 指标树顺序
     */
    @TableField(value = "`order`")
    @ApiModelProperty(value = "指标树顺序")
    private Integer order;

    /**
     * 指标配置
     */
    @TableField(value = "`indicator`")
    @ApiModelProperty(value = "指标配置")
    private String indicator;
}