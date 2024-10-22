package com.bidr.qcc.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: QccReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:17
 */

@Data
public class QccReq {
    @ApiModelProperty("企查查账号(默认不填)")
    private String key;
}
