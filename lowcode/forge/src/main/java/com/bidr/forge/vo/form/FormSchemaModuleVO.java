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
 * 表单模块 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单模块")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormSchemaModuleVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 产品 id
     */
    @ApiModelProperty(value = "表单 id")
    private String formId;

    /**
     * 模块名称
     */
    @PortalNameField
    @ApiModelProperty("模块名称")
    private String title;

    /**
     * 描述
     */
    @ApiModelProperty("描述")
    private String description;

    /**
     * 是否多组数据
     */
    @ApiModelProperty(value = "是否多组数据")
    private String multi;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;
}
