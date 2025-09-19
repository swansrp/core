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
 * 低代码表格表视图
 *
 * @author Sharp
 */
@ApiModel(description = "低代码表格表视图")
@Data
@TableName(value = "sys_portal_dataset")
public class SysPortalDataset {
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
     * 多源数据库配置名称
     */
    @TableField(value = "data_source")
    @ApiModelProperty(value = "多源数据库配置名称")
    private String dataSource;

    /**
     * 表顺序
     */
    @TableField(value = "dataset_order")
    @ApiModelProperty(value = "表顺序")
    private Integer datasetOrder;

    /**
     * 关联表
     */
    @TableField(value = "dataset_sql")
    @ApiModelProperty(value = "关联表")
    private String datasetSql;

    /**
     * 表别名
     */
    @TableField(value = "dataset_alias")
    @ApiModelProperty(value = "表别名")
    private String datasetAlias;

    /**
     * JOIN类型，主表可为空
     */
    @TableField(value = "join_type")
    @ApiModelProperty(value = "JOIN类型，主表可为空")
    private String joinType;

    /**
     * ON 条件，主表可为空
     */
    @TableField(value = "join_condition")
    @ApiModelProperty(value = "ON 条件，主表可为空")
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