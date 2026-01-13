package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 字典表
 *
 * @author sharp
 */
@ApiModel(description = "字典表")
@Data
@TableName(value = "sys_biz_dict")
public class SysBizDict {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 字典编码
     */
    @TableField(value = "dict_code")
    @ApiModelProperty(value = "字典编码")
    private String dictCode;

    /**
     * 字典名称
     */
    @TableField(value = "dict_name")
    @ApiModelProperty(value = "字典名称")
    private String dictName;

    /**
     * NULL表示系统共用字典
     */
    @TableField(value = "biz_id")
    @ApiModelProperty(value = "NULL表示系统共用字典")
    private String bizId;

    /**
     * 字典项显示名称
     */
    @TableField(value = "label")
    @ApiModelProperty(value = "字典项显示名称")
    private String label;

    /**
     * 字典项值
     */
    @TableField(value = "`value`")
    @ApiModelProperty(value = "字典项值")
    private String value;

    /**
     * 描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 排序号
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "排序号")
    private Integer sort;

    /**
     * 是否为默认项
     */
    @TableField(value = "is_default")
    @ApiModelProperty(value = "是否为默认项")
    private String isDefault;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 是否有效
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "是否有效")
    private String valid;
}
