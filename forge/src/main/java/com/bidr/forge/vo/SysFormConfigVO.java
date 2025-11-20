package com.bidr.forge.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动态表单配置VO
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "动态表单配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysFormConfigVO extends BaseVO {
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    @ApiModelProperty(value = "字段ID")
    private Long columnId;

    @PortalNameField
    @ApiModelProperty(value = "显示标签")
    private String label;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "字段类型")
    private String fieldType;

    @ApiModelProperty(value = "所属字典")
    private String dict;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    @ApiModelProperty(value = "最大值")
    private String maxValue;

    @ApiModelProperty(value = "最小值")
    private String minValue;

    @ApiModelProperty(value = "是否必填")
    private String isRequired;

    @ApiModelProperty(value = "是否只读")
    private String readonly;

    @ApiModelProperty(value = "正则表达式")
    private String validationRule;

    @ApiModelProperty(value = "宽")
    private Integer width;

    @ApiModelProperty(value = "高")
    private Integer height;

    @ApiModelProperty(value = "横轴坐标")
    private Integer positionX;

    @ApiModelProperty(value = "纵轴坐标")
    private Integer positionY;

    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
