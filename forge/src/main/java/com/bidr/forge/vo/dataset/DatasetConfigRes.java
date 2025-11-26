package com.bidr.forge.vo.dataset;

import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Dataset配置解析响应
 *
 * @author Sharp
 * @since 2025-11-25
 */
@Data
@ApiModel(description = "Dataset配置解析响应")
public class DatasetConfigRes {

    @ApiModelProperty(value = "数据集ID")
    private Long datasetId;

    @ApiModelProperty(value = "数据集关联表配置列表")
    private List<SysDatasetTable> tables;

    @ApiModelProperty(value = "数据集列配置列表")
    private List<SysDatasetColumn> columns;
}
