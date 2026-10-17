package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单字段分组
 *
 * @author sharp
 */
@ApiModel(description = "表单字段分组")
@Data
@AccountContextFill
@TableName(value = "form_schema_attribute_group")
public class FormSchemaAttributeGroup {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 父 ID
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父 ID")
    private Long pid;

    /**
     * 所属区块 ID
     */
    @TableField(value = "section_id")
    @ApiModelProperty(value = "所属区块 ID")
    private Long sectionId;

    /**
     * 分组标题
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "分组标题")
    private String title;

    /**
     * 分组描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "分组描述")
    private String description;

    /**
     * 是否支持多行：0=单组，1=多组子表
     */
    @TableField(value = "multi")
    @ApiModelProperty(value = "是否支持多行：0=单组，1=多组子表")
    private String multi;

    /**
     * 是否必填
     */
    @TableField(value = "required")
    @ApiModelProperty(value = "是否必填")
    private String required;

    /**
     * 排序号
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "排序号")
    private Integer sort;

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
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
