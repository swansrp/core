package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 宽表收集配置
 *
 * @author sharp
 */
@ApiModel(description = "宽表收集配置")
@Data
@AccountContextFill
@TableName(value = "form_wide_table_config")
public class FormWideTableConfig {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 关联表单 ID
     */
    @TableField(value = "form_id")
    @ApiModelProperty(value = "关联表单 ID")
    private String formId;

    /**
     * 物理宽表名
     */
    @TableField(value = "table_name")
    @ApiModelProperty(value = "物理宽表名")
    private String tableName;

    /**
     * 配置名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "配置名称")
    private String title;

    /**
     * 描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 状态: draft/active/inactive
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态: draft/active/inactive")
    private String status;

    /**
     * 关联 Portal 表 ID
     */
    @TableField(value = "portal_id")
    @ApiModelProperty(value = "关联 Portal 表 ID")
    private Long portalId;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
