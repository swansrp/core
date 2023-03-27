package com.bidr.kernel.vo.common;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Title: IdOrderReqVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/22 10:16
 */
@Data
public class IdOrderReqVO {
    @ApiModelProperty("id")
    private Object id;
    @ApiModelProperty("显示顺序")
    private Integer showOrder;
}
