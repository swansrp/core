package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 矩阵字段配置
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "矩阵字段配置")
@Data
@AccountContextFill
@TableName(value = "sys_matrix_column")
public class SysMatrixColumn {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @TableField(value = "matrix_id")
    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    @TableField(value = "column_name")
    @ApiModelProperty(value = "字段名")
    private String columnName;

    @TableField(value = "column_comment")
    @ApiModelProperty(value = "字段注释")
    private String columnComment;

    @TableField(value = "column_type")
    @ApiModelProperty(value = "字段类型")
    private String columnType;

    @TableField(value = "field_type")
    @ApiModelProperty(value = "表单字段类型")
    private String fieldType;

    @TableField(value = "column_length")
    @ApiModelProperty(value = "字段长度")
    private Integer columnLength;

    @TableField(value = "decimal_places")
    @ApiModelProperty(value = "小数位数")
    private Integer decimalPlaces;

    @TableField(value = "is_nullable")
    @ApiModelProperty(value = "是否可空")
    private String isNullable;

    @TableField(value = "default_value")
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    @TableField(value = "sequence")
    @ApiModelProperty(value = "序列")
    private String sequence;

    @TableField(value = "is_primary_key")
    @ApiModelProperty(value = "是否主键")
    private String isPrimaryKey;

    @TableField(value = "is_index")
    @ApiModelProperty(value = "是否索引")
    private String isIndex;

    @TableField(value = "is_unique")
    @ApiModelProperty(value = "是否唯一")
    private String isUnique;

    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
    private Integer sort;

    @TableField(value = "is_display_name_field")
    @ApiModelProperty(value = "名称字段")
    private String isDisplayNameField;

    @TableField(value = "is_order_field")
    @ApiModelProperty(value = "顺序字段")
    private String isOrderField;

    @TableField(value = "is_pid_field")
    @ApiModelProperty(value = "父节点字段")
    private String isPidField;

    @TableField(value = "reference_matrix_id")
    @ApiModelProperty(value = "关联矩阵")
    private String referenceMatrixId;

    @TableField(value = "reference_dict")
    @ApiModelProperty(value = "关联字典")
    private String referenceDict;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
