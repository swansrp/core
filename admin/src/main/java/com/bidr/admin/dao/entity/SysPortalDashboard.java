package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 个人仪表盘配置
 */
@ApiModel(description = "个人仪表盘配置")
@Data
@TableName(value = "sys_portal_dashboard")
public class SysPortalDashboard {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @ApiModelProperty(value = "")
    private String id;

    /**
     * 数据id
     */
    @TableField(value = "statistic_id")
    @ApiModelProperty(value = "数据id")
    private Long statisticId;

    /**
     * 所属用户
     */
    @TableField(value = "customer_number")
    @ApiModelProperty(value = "所属用户")
    private String customerNumber;

    /**
     * 仪表盘展示顺序
     */
    @TableField(value = "`order`")
    @ApiModelProperty(value = "仪表盘展示顺序")
    private Integer order;

    /**
     * 图表宽度
     */
    @TableField(value = "x_grid")
    @ApiModelProperty(value = "图表宽度")
    private Integer xGrid;

    /**
     * 图表高度
     */
    @TableField(value = "y_grid")
    @ApiModelProperty(value = "图表高度")
    private Integer yGrid;
}