package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单字段属性 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单字段属性")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormSchemaAttributeVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 依赖属性 ID
     */
    @ApiModelProperty(value = "依赖属性 ID")
    private Long parentAttributeId;

    /**
     * 区块 id
     */
    @ApiModelProperty(value = "区块 id")
    private Long sectionId;

    /**
     * 所属分组 id
     */
    @ApiModelProperty(value = "所属分组 id")
    private Long groupId;

    /**
     * 字段名
     */
    @ApiModelProperty(value = "字段名")
    private String name;

    /**
     * 显示标签
     */
    @PortalNameField
    @ApiModelProperty("显示标签")
    private String label;

    /**
     * 标签宽度
     */
    @ApiModelProperty(value = "标签宽度")
    private Integer labelWidth;

    /**
     * 描述
     */
    @ApiModelProperty("描述")
    private String description;

    /**
     * 数据类型
     */
    @ApiModelProperty(value = "数据类型")
    private String fieldType;

    /**
     * 所属字典
     */
    @ApiModelProperty(value = "所属字典")
    private String dict;

    /**
     * 单位
     */
    @ApiModelProperty(value = "单位")
    private String unit;

    /**
     * 默认值
     */
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    /**
     * 最大值
     */
    @ApiModelProperty(value = "最大值")
    private String maxValue;

    /**
     * 最小值
     */
    @ApiModelProperty(value = "最小值")
    private String minValue;

    /**
     * 是否必填
     */
    @ApiModelProperty(value = "是否必填")
    private String isRequired;

    /**
     * 是否只读
     */
    @ApiModelProperty(value = "是否只读")
    private String readonly;

    /**
     * 宽
     */
    @ApiModelProperty(value = "宽")
    private Integer width;

    /**
     * 高
     */
    @ApiModelProperty(value = "高")
    private Integer height;

    /**
     * 横轴坐标
     */
    @ApiModelProperty(value = "横轴坐标")
    private Integer positionX;

    /**
     * 纵轴坐标
     */
    @ApiModelProperty(value = "纵轴坐标")
    private Integer positionY;

    /**
     * 正则表达式
     */
    @ApiModelProperty(value = "正则表达式")
    private String validationRule;

    /**
     * 显示条件
     */
    @ApiModelProperty(value = "显示条件")
    private String visibilityCondition;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;
}
