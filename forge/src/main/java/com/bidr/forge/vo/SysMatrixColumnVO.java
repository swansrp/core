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
 * 矩阵字段配置VO
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "矩阵字段配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMatrixColumnVO extends BaseVO {
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    @PortalNameField
    @ApiModelProperty(value = "字段名")
    private String columnName;

    @ApiModelProperty(value = "字段注释")
    private String columnComment;

    @ApiModelProperty(value = "字段类型")
    private String columnType;

    @ApiModelProperty(value = "表单字段类型")
    private String fieldType;

    @ApiModelProperty(value = "字段长度")
    private Integer columnLength;

    @ApiModelProperty(value = "小数位数")
    private Integer decimalPlaces;

    @ApiModelProperty(value = "是否可空")
    private String isNullable;

    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    @ApiModelProperty(value = "是否主键")
    private String isPrimaryKey;

    @ApiModelProperty(value = "是否索引")
    private String isIndex;

    @ApiModelProperty(value = "是否唯一")
    private String isUnique;

    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
