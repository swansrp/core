package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单
 *
 * @author sharp
 */
@ApiModel(description = "表单")
@Data
@AccountContextFill
@TableName(value = "form_schema")
public class FormSchema {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @ApiModelProperty(value = "id")
    private String id;

    /**
     * 父 id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父 id")
    private String pid;

    /**
     * 编码
     */
    @TableField(value = "code")
    @ApiModelProperty(value = "编码")
    private String code;

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
     * 状态: draft/published/archived
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态: draft/published/archived")
    private String status;

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
