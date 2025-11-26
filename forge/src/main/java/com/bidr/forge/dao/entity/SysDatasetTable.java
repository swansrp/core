package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 数据集关联表
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集关联表")
@Data
@AccountContextFill
@TableName(value = "sys_dataset_table")
public class SysDatasetTable {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 关联的数据集ID
     */
    @TableField(value = "dataset_id")
    @ApiModelProperty(value = "关联的数据集ID")
    private Long datasetId;

    /**
     * 表顺序
     */
    @TableField(value = "table_order")
    @ApiModelProperty(value = "表顺序")
    private Integer tableOrder;

    /**
     * 关联表SQL
     */
    @TableField(value = "table_sql")
    @ApiModelProperty(value = "关联表SQL")
    private String tableSql;

    /**
     * 表别名
     */
    @TableField(value = "table_alias")
    @ApiModelProperty(value = "表别名")
    private String tableAlias;

    /**
     * JOIN类型（主表可为空）
     */
    @TableField(value = "join_type")
    @ApiModelProperty(value = "JOIN类型（主表可为空）")
    private String joinType;

    /**
     * ON条件（主表可为空）
     */
    @TableField(value = "join_condition")
    @ApiModelProperty(value = "ON条件（主表可为空）")
    private String joinCondition;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;

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
}
