package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 数据集 SQL 快照
 * 每次保存数据集且 SQL 有变化时留一份快照, 用于误粘贴后回滚
 *
 * @author sharp
 * @since 2026-10-30
 */
@ApiModel(description = "数据集SQL快照")
@Data
@AccountContextFill
@TableName(value = "sys_dataset_sql_snapshot")
public class SysDatasetSqlSnapshot {
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
     * 该次保存的完整SQL
     */
    @TableField(value = "sql_text")
    @ApiModelProperty(value = "该次保存的完整SQL")
    private String sqlText;

    /**
     * 数据源配置名称
     */
    @TableField(value = "data_source")
    @ApiModelProperty(value = "数据源配置名称")
    private String dataSource;

    /**
     * 数据集备注(保存时点)
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "数据集备注(保存时点)")
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
}
