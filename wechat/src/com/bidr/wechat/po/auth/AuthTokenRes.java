package com.bidr.wechat.po.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: AuthTokenRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/19 11:12
 */
@Data
public class AuthTokenRes {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expires_in")
    private Integer expiresIn;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("openid")
    private String openId;
    private String scope;
}
