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
 * 表单区块 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单区块")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormSchemaSectionVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 模块 id
     */
    @ApiModelProperty(value = "模块 id")
    private Long moduleId;

    /**
     * 数据宽表名称
     */
    @ApiModelProperty(value = "数据宽表名称")
    private String tableName;

    /**
     * 区块名称
     */
    @PortalNameField
    @ApiModelProperty("区块名称")
    private String title;

    /**
     * 描述
     */
    @ApiModelProperty("描述")
    private String description;

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
