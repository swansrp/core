package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 数据集列配置
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集列配置")
@Data
@AccountContextFill
@TableName(value = "sys_dataset_column")
public class SysDatasetColumn {
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
     * 字段SQL表达式
     */
    @TableField(value = "column_sql")
    @ApiModelProperty(value = "字段SQL表达式")
    private String columnSql;

    /**
     * 字段别名
     */
    @TableField(value = "column_alias")
    @ApiModelProperty(value = "字段别名")
    private String columnAlias;

    /**
     * 是否是聚合字段
     */
    @TableField(value = "is_aggregate")
    @ApiModelProperty(value = "是否是聚合字段")
    private String isAggregate;

    /**
     * 前端显示排序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "前端显示排序")
    private Integer displayOrder;

    /**
     * 是否显示在结果集中
     */
    @TableField(value = "is_visible")
    @ApiModelProperty(value = "是否显示在结果集中")
    private String isVisible;

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
