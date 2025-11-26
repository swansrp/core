package com.bidr.forge.vo.dataset;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Dataset列配置请求
 *
 * @author Sharp
 * @since 2025-11-25
 */
@Data
@ApiModel(description = "Dataset列配置请求")
public class DatasetColumnReq {

    @ApiModelProperty(value = "主键ID（更新时必填）")
    private Long id;

    @NotNull(message = "datasetId不能为空")
    @ApiModelProperty(value = "关联的数据集ID", required = true)
    private Long datasetId;

    @NotBlank(message = "字段SQL表达不能为空")
    @ApiModelProperty(value = "字段SQL表达式", required = true, example = "SUM(amount)")
    private String columnSql;

    @NotBlank(message = "字段别名不能为空")
    @ApiModelProperty(value = "字段别名", required = true, example = "totalAmount")
    private String columnAlias;

    @ApiModelProperty(value = "是否是聚合字段（Y/N）", example = "Y")
    private String isAggregate;

    @ApiModelProperty(value = "前端显示排序")
    private Integer displayOrder;

    @ApiModelProperty(value = "是否显示在结果集中（Y/N）", example = "Y")
    private String isVisible;

    @ApiModelProperty(value = "备注")
    private String remark;
}
