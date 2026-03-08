package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.config.PortalPidField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单字段分组 VO - 树形结构
 *
 * @author sharp
 */
@ApiModel(description = "表单字段分组")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormSchemaAttributeGroupVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 父 ID
     */
    @PortalPidField
    @ApiModelProperty(value = "父 ID")
    private Long pid;

    /**
     * 所属区块 ID
     */
    @ApiModelProperty(value = "所属区块 ID")
    private Long sectionId;

    /**
     * 分组标题
     */
    @PortalNameField
    @ApiModelProperty("分组标题")
    private String title;

    /**
     * 分组描述
     */
    @ApiModelProperty("分组描述")
    private String description;

    /**
     * 是否支持多行：0=单组，1=多组子表
     */
    @ApiModelProperty(value = "是否支持多行：0=单组，1=多组子表")
    private String multi;

    /**
     * 是否必填
     */
    @ApiModelProperty(value = "是否必填")
    private String required;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;
}
