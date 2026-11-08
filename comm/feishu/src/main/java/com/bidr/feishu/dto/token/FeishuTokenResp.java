package com.bidr.feishu.dto.token;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: FeishuTokenResp
 * Description: 飞书 tenant_access_token 响应
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Data
public class FeishuTokenResp {

    /**
     * 错误码，0 表示成功
     */
    private Integer code;

    /**
     * 错误信息
     */
    private String msg;

    /**
     * 访问令牌
     */
    @JsonProperty("tenant_access_token")
    private String tenantAccessToken;

    /**
     * 有效期（秒）
     */
    private Integer expire;
}
