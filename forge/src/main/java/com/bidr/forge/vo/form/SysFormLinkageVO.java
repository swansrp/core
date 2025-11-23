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
 * 表单项联动配置VO
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "表单项联动配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysFormLinkageVO extends BaseVO {
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "表单配置ID")
    private Long formConfigId;

    @PortalNameField
    @ApiModelProperty(value = "联动名称")
    private String linkageName;

    @ApiModelProperty(value = "触发事件(change/blur/focus)")
    private String triggerEvent;

    @ApiModelProperty(value = "条件脚本(JS)")
    private String conditionScript;

    @ApiModelProperty(value = "执行脚本(JS)")
    private String actionScript;

    @ApiModelProperty(value = "目标字段(逗号分隔)")
    private String targetFields;

    @ApiModelProperty(value = "优先级")
    private Integer priority;

    @ApiModelProperty(value = "是否启用")
    private String isEnabled;

    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
