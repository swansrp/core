package com.bidr.authorization.client.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: OAuth2TokenRes
 * Description: IdP token 端点响应（统一信封 payload 形状：access_token + token_type + expires_in）。
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@Data
public class OAuth2TokenRes {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;
}
