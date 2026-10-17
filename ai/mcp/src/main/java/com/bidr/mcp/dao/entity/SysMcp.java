package com.bidr.mcp.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * mcp配置表
 */
@ApiModel(description = "mcp配置表")
@Data
@TableName(value = "sys_mcp")
public class SysMcp {
    /**
     * mcp服务
     */
    @MppMultiId("end_point")
    @ApiModelProperty(value = "mcp服务")
    private String endPoint;

    /**
     * mcp方法名
     */
    @MppMultiId
    @ApiModelProperty(value = "mcp方法名")
    private String name;

    /**
     * mcp类型
     */
    @MppMultiId
    @ApiModelProperty(value = "mcp类型")
    private String type;

    /**
     * mcp服务名称
     */
    @TableField(value = "end_point_name")
    @ApiModelProperty(value = "mcp服务名称")
    private String endPointName;

    /**
     * mcp方法描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "mcp方法描述")
    private String description;
}