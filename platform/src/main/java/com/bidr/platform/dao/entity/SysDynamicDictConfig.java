package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 动态字典配置表
 * <p>
 * 存储动态字典的查询配置，刷新缓存时按配置重新执行SQL，将结果写入 sys_biz_dict。
 *
 * @author Sharp
 * @since 2026-07-14
 */
@ApiModel(description = "动态字典配置表")
@Data
@TableName(value = "sys_dynamic_dict_config")
public class SysDynamicDictConfig {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @TableField(value = "dict_code")
    @ApiModelProperty(value = "字典编码（业务字典中的dict_code）")
    private String dictCode;

    @TableField(value = "dict_name")
    @ApiModelProperty(value = "字典显示名称")
    private String dictName;

    @TableField(value = "data_source")
    @ApiModelProperty(value = "数据源名称（可选，为空则使用默认数据源）")
    private String dataSource;

    @TableField(value = "database_name")
    @ApiModelProperty(value = "数据库名（可选）")
    private String databaseName;

    @TableField(value = "table_name")
    @ApiModelProperty(value = "表名")
    private String tableName;

    @TableField(value = "value_column")
    @ApiModelProperty(value = "value字段列名")
    private String valueColumn;

    @TableField(value = "label_column")
    @ApiModelProperty(value = "label字段列名")
    private String labelColumn;

    @TableField(value = "order_by")
    @ApiModelProperty(value = "排序方式")
    private String orderBy;

    @TableField(value = "pid_column")
    @ApiModelProperty(value = "父级ID列名（有值则为树形字典模式）")
    private String pidColumn;

    @TableField(value = "conditions")
    @ApiModelProperty(value = "筛选条件（JSON格式，List<DynamicDictCondition>）")
    private String conditions;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "是否有效")
    private String valid;
}
