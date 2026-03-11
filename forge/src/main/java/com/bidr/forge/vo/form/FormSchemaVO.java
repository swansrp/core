package com.bidr.forge.vo.form;

import com.baomidou.mybatisplus.annotation.TableField;
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
 * 表单 VO - 树形结构
 *
 * @author sharp
 */
@ApiModel(description = "表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormSchemaVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 父 ID
     */
    @PortalPidField
    @ApiModelProperty(value = "父 ID")
    private String pid;

    /**
     * 编码
     */
    @ApiModelProperty(value = "编码")
    private String code;

    /**
     * 表单名称
     */
    @PortalNameField
    @ApiModelProperty("表单名称")
    private String title;

    /**
     * 描述
     */
    @ApiModelProperty("描述")
    private String description;

    /**
     * 状态: draft/published/archived
     */
    @ApiModelProperty(value = "状态: draft/published/archived")
    private String status;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;
}
