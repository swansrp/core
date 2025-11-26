package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 数据集主表
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集主表")
@Data
@AccountContextFill
@TableName(value = "sys_dataset")
public class SysDataset {
    /**
     * 数据集ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "数据集ID")
    private Long id;

    /**
     * 数据集名称
     */
    @TableField(value = "dataset_name")
    @ApiModelProperty(value = "数据集名称")
    private String datasetName;

    /**
     * 数据源配置名称
     */
    @TableField(value = "data_source")
    @ApiModelProperty(value = "数据源配置名称")
    private String dataSource;

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

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
