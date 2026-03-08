package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单区块
 *
 * @author sharp
 */
@ApiModel(description = "表单区块")
@Data
@AccountContextFill
@TableName(value = "form_schema_section")
public class FormSchemaSection {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 模块 id
     */
    @TableField(value = "module_id")
    @ApiModelProperty(value = "模块 id")
    private Long moduleId;

    /**
     * 数据宽表名称
     */
    @TableField(value = "table_name")
    @ApiModelProperty(value = "数据宽表名称")
    private String tableName;

    /**
     * 名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "名称")
    private String title;

    /**
     * 描述
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 是否必填
     */
    @TableField(value = "required")
    @ApiModelProperty(value = "是否必填")
    private String required;

    /**
     * 顺序
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "顺序")
    private Integer sort;

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
