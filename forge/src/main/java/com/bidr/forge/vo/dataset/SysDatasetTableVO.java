package com.bidr.forge.vo.dataset;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.config.PortalTextAreaField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集关联表 VO
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集关联表")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDatasetTableVO extends BaseVO {
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
     * 多源数据库配置名称
     */
    @ApiModelProperty(value = "多源数据库配置名称")
    private String dataSource;

    /**
     * 表顺序
     */
    @PortalOrderField
    @ApiModelProperty(value = "表顺序")
    private Integer tableOrder;

    /**
     * 关联表SQL
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "关联表SQL")
    private String tableSql;

    /**
     * 表别名
     */
    @ApiModelProperty(value = "表别名")
    private String tableAlias;

    /**
     * JOIN类型（主表可为空）
     */
    @ApiModelProperty(value = "JOIN类型（主表可为空）")
    private String joinType;

    /**
     * ON条件（主表可为空）
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "ON条件（主表可为空）")
    private String joinCondition;

    /**
     * 备注
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "备注")
    private String remark;
}
