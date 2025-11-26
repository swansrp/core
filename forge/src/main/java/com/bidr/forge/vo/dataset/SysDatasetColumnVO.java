package com.bidr.forge.vo.dataset;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.config.PortalTextAreaField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集列配置 VO
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集列配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDatasetColumnVO extends BaseVO {
    /**
     * 主键ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 关联的数据集ID
     */
    @ApiModelProperty(value = "关联的数据集ID")
    private Long datasetId;

    /**
     * 字段SQL表达式
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "字段SQL表达式")
    private String columnSql;

    /**
     * 字段别名
     */
    @PortalNameField
    @ApiModelProperty(value = "字段别名")
    private String columnAlias;

    /**
     * 是否是聚合字段
     */
    @ApiModelProperty(value = "是否是聚合字段")
    private String isAggregate;

    /**
     * 前端显示排序
     */
    @PortalOrderField
    @ApiModelProperty(value = "前端显示排序")
    private Integer displayOrder;

    /**
     * 是否显示在结果集中
     */
    @ApiModelProperty(value = "是否显示在结果集中")
    private String isVisible;

    /**
     * 备注
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "备注")
    private String remark;
}
