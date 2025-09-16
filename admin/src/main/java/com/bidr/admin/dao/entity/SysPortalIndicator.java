package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 统计指标
 */
@ApiModel(description = "统计指标")
@Data
@TableName(value = "sys_portal_indicator")
public class SysPortalIndicator {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 指标分组id
     */
    @TableField(value = "group_id")
    @ApiModelProperty(value = "指标分组id")
    private Long groupId;

    /**
     * 指标项值
     */
    @TableField(value = "item_value")
    @ApiModelProperty(value = "指标项值")
    private String itemValue;

    /**
     * 指标项名称
     */
    @TableField(value = "item_name")
    @ApiModelProperty(value = "指标项名称")
    private String itemName;

    /**
     * 条件json
     */
    @TableField(value = "`condition`")
    @ApiModelProperty(value = "条件json")
    private String condition;

    /**
     * 动态字段map
     */
    @TableField(value = "dynamic_column")
    @ApiModelProperty(value = "动态字段map")
    private String dynamicColumn;

    /**
     * 指标项排序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "指标项排序")
    private Integer displayOrder;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}