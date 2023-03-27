package com.bidr.kernel.vo.common;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Title: IdReqVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/22 09:26
 */
@Data
public class IdReqVO {
    @ApiModelProperty("id")
    private Serializable id;
}
