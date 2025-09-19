package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 低代码表格列
 *
 * @author Sharp
 */
@ApiModel(description = "低代码表格列")
@Data
@TableName(value = "sys_portal_dataset_column")
public class SysPortalDatasetColumn {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    private Long id;

    /**
     * 关联的表格ID
     */
    @TableField(value = "table_id")
    @ApiModelProperty(value = "关联的表格ID")
    private String tableId;

    /**
     * 字段SQL表达
     */
    @TableField(value = "column_sql")
    @ApiModelProperty(value = "字段SQL表达")
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

    @TableField(value = "remark")
    @ApiModelProperty(value = "")
    private String remark;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}