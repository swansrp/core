package com.bidr.forge.vo.dataset;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalTextAreaField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集主表 VO
 *
 * @author sharp
 * @since 2025-11-25
 */
@ApiModel(description = "数据集主表")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDatasetVO extends BaseVO {
    /**
     * 数据集ID
     */
    @PortalIdField
    @ApiModelProperty(value = "数据集ID")
    private Long id;

    /**
     * 数据集名称
     */
    @PortalNameField
    @ApiModelProperty(value = "数据集名称")
    private String datasetName;

    /**
     * 数据源配置名称
     */
    @ApiModelProperty(value = "数据源配置名称")
    private String dataSource;

    /**
     * 备注
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "备注")
    private String remark;
}
