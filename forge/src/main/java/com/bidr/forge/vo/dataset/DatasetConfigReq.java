package com.bidr.forge.vo.dataset;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Dataset配置解析请求
 *
 * @author Sharp
 * @since 2025-11-25
 */
@Data
@ApiModel(description = "Dataset配置解析请求")
public class DatasetConfigReq {

    @ApiModelProperty(value = "关联的数据集ID（parseSql预览时可为空，parseSqlAndSave保存时必填）", required = false)
    private Long datasetId;

    @ApiModelProperty(value = "数据集名称（当新增保存时必填，替换保存可为空）", required = false)
    private String datasetName;

    @ApiModelProperty("备注")
    private String remark;

    @NotBlank(message = "SQL不能为空")
    @ApiModelProperty(value = "完整的SELECT SQL语句", required = true, example = "SELECT a.id, a.name FROM table_a a LEFT JOIN table_b b ON a.id = b.aid")
    private String sql;

    @ApiModelProperty(value = "数据源配置名称（可选，为空则使用默认数据源）")
    private String dataSource;
}
