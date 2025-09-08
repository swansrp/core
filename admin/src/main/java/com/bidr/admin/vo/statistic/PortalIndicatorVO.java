package com.bidr.admin.vo.statistic;

import com.bidr.admin.config.*;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: PortalIndicatorVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/8 15:10
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalIndicatorVO extends BaseVO {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "指标分组id")
    private Long groupId;

    @ApiModelProperty(value = "指标项值")
    private String itemValue;

    @PortalNameField
    @ApiModelProperty(value = "指标项名称")
    private String itemName;

    @PortalTextAreaField
    @ApiModelProperty(value = "条件json")
    private String condition;

    @PortalTextAreaField
    @ApiModelProperty(value = "动态字段map")
    private String dynamicColumn;

    @PortalOrderField
    @ApiModelProperty(value = "指标项排序")
    private Integer displayOrder;

    @PortalTextAreaField
    @ApiModelProperty(value = "备注")
    private String remark;
}
