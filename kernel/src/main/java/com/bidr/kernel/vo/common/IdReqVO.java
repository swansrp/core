package com.bidr.kernel.vo.common;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: IdReqVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/22 09:26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdReqVO {
    @ApiModelProperty("id")
    private String id;
}
