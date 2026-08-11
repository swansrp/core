package com.bidr.admin.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 透视报表父表头列配置 VO - 前端交互
 *
 * @author Sharp
 */
@ApiModel(description = "透视报表父表头列配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalPivotColumnVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * table_id
     */
    @ApiModelProperty(value = "table_id")
    private Long tableId;

    /**
     * 列标识
     */
    @PortalNameField
    @ApiModelProperty(value = "列标识")
    private String itemValue;

    /**
     * 表头名称
     */
    @PortalNameField
    @ApiModelProperty(value = "表头名称")
    private String itemName;

    /**
     * 列条件json
     */
    @ApiModelProperty(value = "列条件json")
    private String condition;

    /**
     * 显示顺序
     */
    @PortalOrderField
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;

    /**
     * 状态
     */
    @ApiModelProperty(value = "状态")
    private String status;
}
