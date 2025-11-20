package com.bidr.forge.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 矩阵配置VO
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "矩阵配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMatrixVO extends BaseVO {
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @PortalNameField
    @ApiModelProperty(value = "表名")
    private String tableName;

    @ApiModelProperty(value = "表注释")
    private String tableComment;

    @ApiModelProperty(value = "数据源名称")
    private String dataSource;

    @ApiModelProperty(value = "主键字段")
    private String primaryKey;

    @ApiModelProperty(value = "索引配置(JSON)")
    private String indexConfig;

    @ApiModelProperty(value = "存储引擎")
    private String engine;

    @ApiModelProperty(value = "字符集")
    private String charset;

    @ApiModelProperty(value = "状态(0:未创建,1:已创建,2:已同步)")
    private String status;

    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
