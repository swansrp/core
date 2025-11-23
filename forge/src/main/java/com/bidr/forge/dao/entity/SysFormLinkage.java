package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单项联动配置
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "表单项联动配置")
@Data
@AccountContextFill
@TableName(value = "sys_form_linkage")
public class SysFormLinkage {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @TableField(value = "form_config_id")
    @ApiModelProperty(value = "表单配置ID")
    private Long formConfigId;

    @TableField(value = "linkage_name")
    @ApiModelProperty(value = "联动名称")
    private String linkageName;

    @TableField(value = "trigger_event")
    @ApiModelProperty(value = "触发事件(change/blur/focus)")
    private String triggerEvent;

    @TableField(value = "condition_script")
    @ApiModelProperty(value = "条件脚本(JS)")
    private String conditionScript;

    @TableField(value = "action_script")
    @ApiModelProperty(value = "执行脚本(JS)")
    private String actionScript;

    @TableField(value = "target_fields")
    @ApiModelProperty(value = "目标字段(逗号分隔)")
    private String targetFields;

    @TableField(value = "priority")
    @ApiModelProperty(value = "优先级")
    private Integer priority;

    @TableField(value = "is_enabled")
    @ApiModelProperty(value = "是否启用")
    private String isEnabled;

    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
    private Integer sort;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
