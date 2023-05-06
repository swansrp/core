package com.bidr.kernel.vo.common;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: IdPidReqVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/27 10:28
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IdPidReqVO extends IdReqVO {
    @ApiModelProperty("pid")
    private Object pid;
}
