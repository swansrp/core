package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 动态表单配置
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "动态表单配置")
@Data
@AccountContextFill
@TableName(value = "sys_form_config")
public class SysFormConfig {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @TableField(value = "matrix_id")
    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    @TableField(value = "column_id")
    @ApiModelProperty(value = "字段ID")
    private Long columnId;

    @TableField(value = "`label`")
    @ApiModelProperty(value = "显示标签")
    private String label;

    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    @TableField(value = "field_type")
    @ApiModelProperty(value = "字段类型")
    private String fieldType;

    @TableField(value = "dict")
    @ApiModelProperty(value = "所属字典")
    private String dict;

    @TableField(value = "unit")
    @ApiModelProperty(value = "单位")
    private String unit;

    @TableField(value = "default_value")
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    @TableField(value = "max_value")
    @ApiModelProperty(value = "最大值")
    private String maxValue;

    @TableField(value = "min_value")
    @ApiModelProperty(value = "最小值")
    private String minValue;

    @TableField(value = "is_required")
    @ApiModelProperty(value = "是否必填")
    private String isRequired;

    @TableField(value = "readonly")
    @ApiModelProperty(value = "是否只读")
    private String readonly;

    @TableField(value = "validation_rule")
    @ApiModelProperty(value = "正则表达式")
    private String validationRule;

    @TableField(value = "width")
    @ApiModelProperty(value = "宽")
    private Integer width;

    @TableField(value = "height")
    @ApiModelProperty(value = "高")
    private Integer height;

    @TableField(value = "position_x")
    @ApiModelProperty(value = "横轴坐标")
    private Integer positionX;

    @TableField(value = "position_y")
    @ApiModelProperty(value = "纵轴坐标")
    private Integer positionY;

    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
    private Integer sort;

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
