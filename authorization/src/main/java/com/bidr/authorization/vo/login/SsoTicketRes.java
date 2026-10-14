package com.bidr.authorization.vo.login;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: SsoTicketRes
 * Description: SSO 出票响应（M15 棒 0b-1 纯加法，承诺面 11＝三字段）。
 * <p>前端中转页拿 {@code idToken} 原样回跳（{@code redirect_uri + "?id_token=" + 票}），
 * {@code redirectUri} 为服务端白名单已验过的回填值，前端不再自拼。</p>
 * <p>🔴 G-8（承诺面 41）：{@code expiresAt} 与票内 {@code exp} claim 严格等值——出票编排只算一次 exp，
 * 同一个值既签入票内又回填本字段（曾两次读 {@code System.currentTimeMillis()} 致跨秒差 1 秒）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Data
@ApiModel("SSO票据签发结果")
public class SsoTicketRes {

    @ApiModelProperty(value = "RS256签名的SSO票（三段base64url点分串，claims只含username与exp），子系统以配置公钥验签")
    private String idToken;

    @ApiModelProperty(value = "票据过期时刻（epoch秒）＝票内 exp claim 严格等值（同一 exp 先算一次、既签入票又回吐本字段）；ttl属部署配置，core不设兜底默认值")
    private Long expiresAt;

    @ApiModelProperty(value = "回调地址回填（已通过白名单全串等值校验，前端据此回跳，不再自拼）")
    private String redirectUri;
}
