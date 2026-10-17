package com.bidr.authorization.vo.login;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: QrCodeReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/3/12 13:58
 */
@Data
public class QrCodeReq {
    @ApiModelProperty("token")
    private String token;
    @ApiModelProperty("用户id")
    private String userId;
}
