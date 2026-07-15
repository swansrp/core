package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 宽表字段配置
 *
 * @author sharp
 */
@ApiModel(description = "宽表字段配置")
@Data
@AccountContextFill
@TableName(value = "form_wide_table_config_attr")
public class FormWideTableConfigAttr {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 关联宽表配置 ID
     */
    @TableField(value = "config_id")
    @ApiModelProperty(value = "关联宽表配置 ID")
    private Long configId;

    /**
     * 关联表单字段 ID
     */
    @TableField(value = "attribute_id")
    @ApiModelProperty(value = "关联表单字段 ID")
    private Long attributeId;

    /**
     * 宽表中的物理列名
     */
    @TableField(value = "column_name")
    @ApiModelProperty(value = "宽表中的物理列名")
    private String columnName;

    /**
     * 宽表列显示名
     */
    @TableField(value = "column_label")
    @ApiModelProperty(value = "宽表列显示名")
    private String columnLabel;

    /**
     * 列 SQL 类型
     */
    @TableField(value = "column_type")
    @ApiModelProperty(value = "列 SQL 类型")
    private String columnType;

    /**
     * 是否字典字段: 1=是, 0=否
     */
    @TableField(value = "is_dict")
    @ApiModelProperty(value = "是否字典字段: 1=是, 0=否")
    private String isDict;

    /**
     * 字典 ID
     */
    @TableField(value = "dict_id")
    @ApiModelProperty(value = "字典 ID")
    private String dictId;

    /**
     * 排序
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
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
