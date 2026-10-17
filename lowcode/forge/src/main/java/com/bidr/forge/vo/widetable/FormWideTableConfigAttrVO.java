package com.bidr.forge.vo.widetable;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 宽表字段配置 VO
 *
 * @author sharp
 */
@ApiModel(description = "宽表字段配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormWideTableConfigAttrVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 关联宽表配置 ID
     */
    @ApiModelProperty(value = "关联宽表配置 ID")
    private Long configId;

    /**
     * 关联表单字段 ID
     */
    @ApiModelProperty(value = "关联表单字段 ID")
    private Long attributeId;

    /**
     * 宽表中的物理列名
     */
    @ApiModelProperty(value = "宽表中的物理列名")
    private String columnName;

    /**
     * 宽表列显示名
     */
    @ApiModelProperty(value = "宽表列显示名")
    private String columnLabel;

    /**
     * 列 SQL 类型
     */
    @ApiModelProperty(value = "列 SQL 类型")
    private String columnType;

    /**
     * 是否字典字段: 1=是, 0=否
     */
    @ApiModelProperty(value = "是否字典字段: 1=是, 0=否")
    private String isDict;

    /**
     * 字典 ID
     */
    @ApiModelProperty(value = "字典 ID")
    private String dictId;

    /**
     * 排序
     */
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
