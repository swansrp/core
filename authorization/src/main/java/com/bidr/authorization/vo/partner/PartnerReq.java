package com.bidr.authorization.vo.partner;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Title: PartnerReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/31 08:48
 */
@Data
@ApiModel(description = "三方应用对接申请")
public class PartnerReq {

    @ApiModelProperty(value = "所属平台")
    private String platform;

    @ApiModelProperty(value = "备注")
    @Size(max = 50, message = "备注最大长度要小于 50")
    private String remark;
}
