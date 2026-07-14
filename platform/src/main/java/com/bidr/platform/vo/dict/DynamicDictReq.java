package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 动态字典生成请求
 * <p>
 * 从指定数据源的表中，通过 GROUP BY 两个列（value + label），
 * 加上可选的筛选条件和排序，自动生成字典选项列表。
 *
 * @author Sharp
 * @since 2026-07-14
 */
@ApiModel(description = "动态字典生成请求")
@Data
public class DynamicDictReq {

    @ApiModelProperty(value = "字典编码（保存配置时必填，作为业务字典的dict_code）")
    private String dictCode;

    @ApiModelProperty(value = "字典显示名称（保存配置时必填）")
    private String dictName;

    @ApiModelProperty(value = "数据源名称（可选，为空则使用默认数据源）")
    private String dataSource;

    @ApiModelProperty(value = "数据库名（可选，为空则不限定库，仅当数据源未指定默认库时需传入）")
    private String database;

    @ApiModelProperty(value = "表名（必填）", required = true)
    private String tableName;

    @ApiModelProperty(value = "value字段列名（必填，字典项的值）", required = true)
    private String valueColumn;

    @ApiModelProperty(value = "label字段列名（必填，字典项的显示名称）", required = true)
    private String labelColumn;

    @ApiModelProperty(value = "排序方式（可选，如: 'value ASC' 或 'label DESC'），默认按 value ASC")
    private String orderBy;

    @ApiModelProperty(value = "筛选条件（可选，支持=、!=、IS NULL、IS NOT NULL、LIKE等操作符，多个条件之间为AND关系）")
    private List<DynamicDictCondition> conditions;
}
