package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单字段属性
 *
 * @author sharp
 */
@ApiModel(description = "表单字段属性")
@Data
@AccountContextFill
@TableName(value = "form_schema_attribute")
public class FormSchemaAttribute {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 依赖属性 ID
     */
    @TableField(value = "parent_attribute_id")
    @ApiModelProperty(value = "依赖属性 ID")
    private Long parentAttributeId;

    /**
     * 区块 id
     */
    @TableField(value = "section_id")
    @ApiModelProperty(value = "区块 id")
    private Long sectionId;

    /**
     * 所属分组 id
     */
    @TableField(value = "group_id")
    @ApiModelProperty(value = "所属分组 id")
    private Long groupId;

    /**
     * 字段名
     */
    @TableField(value = "name")
    @ApiModelProperty(value = "字段名")
    private String name;

    /**
     * 显示标签
     */
    @TableField(value = "label")
    @ApiModelProperty(value = "显示标签")
    private String label;

    /**
     * 标签宽度
     */
    @TableField(value = "label_width")
    @ApiModelProperty(value = "标签宽度")
    private Integer labelWidth;

    /**
     * 描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 数据类型
     */
    @TableField(value = "field_type")
    @ApiModelProperty(value = "数据类型")
    private String fieldType;

    /**
     * 所属字典
     */
    @TableField(value = "dict")
    @ApiModelProperty(value = "所属字典")
    private String dict;

    /**
     * 单位
     */
    @TableField(value = "unit")
    @ApiModelProperty(value = "单位")
    private String unit;

    /**
     * 默认值
     */
    @TableField(value = "default_value")
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    /**
     * 最大值
     */
    @TableField(value = "max_value")
    @ApiModelProperty(value = "最大值")
    private String maxValue;

    /**
     * 最小值
     */
    @TableField(value = "min_value")
    @ApiModelProperty(value = "最小值")
    private String minValue;

    /**
     * 是否必填
     */
    @TableField(value = "is_required")
    @ApiModelProperty(value = "是否必填")
    private String isRequired;

    /**
     * 是否只读
     */
    @TableField(value = "readonly")
    @ApiModelProperty(value = "是否只读")
    private String readonly;

    /**
     * 宽
     */
    @TableField(value = "width")
    @ApiModelProperty(value = "宽")
    private Integer width;

    /**
     * 高
     */
    @TableField(value = "height")
    @ApiModelProperty(value = "高")
    private Integer height;

    /**
     * 横轴坐标
     */
    @TableField(value = "position_x")
    @ApiModelProperty(value = "横轴坐标")
    private Integer positionX;

    /**
     * 纵轴坐标
     */
    @TableField(value = "position_y")
    @ApiModelProperty(value = "纵轴坐标")
    private Integer positionY;

    /**
     * 正则表达式
     */
    @TableField(value = "validation_rule")
    @ApiModelProperty(value = "正则表达式")
    private String validationRule;

    /**
     * 显示条件
     */
    @TableField(value = "visibility_condition")
    @ApiModelProperty(value = "显示条件")
    private String visibilityCondition;

    @TableField(value = "matrix_column_id")
    @ApiModelProperty(value = "矩阵列id")
    private Long matrixColumnId;

    /**
     * 顺序
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "顺序")
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
