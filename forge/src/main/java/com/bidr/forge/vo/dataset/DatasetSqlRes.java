package com.bidr.forge.vo.dataset;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Dataset SQL 回显
 * <p>
 * 不落库 SQL，通过已保存的 table/column 配置拼装，供前端回显编辑。
 */
@Data
@ApiModel(description = "Dataset SQL 回显")
public class DatasetSqlRes {

    @ApiModelProperty("datasetId")
    private Long datasetId;

    @ApiModelProperty("拼装后的 SQL（可选择包含列备注注释）")
    private String sql;
}

