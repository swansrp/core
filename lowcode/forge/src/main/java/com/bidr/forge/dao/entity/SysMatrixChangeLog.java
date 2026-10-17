package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 矩阵表结构变更日志
 *
 * @author sharp
 * @since 2025-11-21
 */
@ApiModel(description = "矩阵表结构变更日志")
@Data
@AccountContextFill
@TableName(value = "sys_matrix_change_log")
public class SysMatrixChangeLog {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 矩阵ID
     */
    @TableField(value = "matrix_id")
    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    /**
     * 版本号
     */
    @TableField(value = "version")
    @ApiModelProperty(value = "版本号")
    private Integer version;

    /**
     * 变更类型：1-创建表，2-添加字段，3-调整字段顺序，4-添加索引，5-删除索引
     */
    @TableField(value = "change_type")
    @ApiModelProperty(value = "变更类型：1-创建表，2-添加字段，3-调整字段顺序，4-添加索引，5-删除索引")
    private String changeType;

    /**
     * 变更描述
     */
    @TableField(value = "change_desc")
    @ApiModelProperty(value = "变更描述")
    private String changeDesc;

    /**
     * 执行的DDL语句
     */
    @TableField(value = "ddl_statement")
    @ApiModelProperty(value = "执行的DDL语句")
    private String ddlStatement;

    /**
     * 影响的字段名
     */
    @TableField(value = "affected_column")
    @ApiModelProperty(value = "影响的字段名")
    private String affectedColumn;

    /**
     * 执行状态：0-失败，1-成功
     */
    @TableField(value = "execute_status")
    @ApiModelProperty(value = "执行状态：0-失败，1-成功")
    private String executeStatus;

    /**
     * 错误信息
     */
    @TableField(value = "error_msg")
    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    /**
     * 排序
     */
    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
    private Integer sort;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 创建人工号
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人工号")
    private String createBy;


    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 更新人工号
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新人工号")
    private String updateBy;

}
