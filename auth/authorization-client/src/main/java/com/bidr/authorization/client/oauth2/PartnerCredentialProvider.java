package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.platform.openapi.OpenApiAuthProvider;
import org.springframework.http.HttpHeaders;

/**
 * Title: PartnerCredentialProvider
 * Description: 开放口<b>机器凭据腿</b>（方案B 棒4 §2「core 凭据」层的 authorization-client 实现）——
 * 把 {@code my.oauth2.client.client-id/client-secret}（＝provider 侧 ac_partner 发放的
 * appKey/appSecret）按 {@code x-api-key: Base64(appKey:appSecret)} 写入请求头，供场景客户端
 * （菜单树等 /open-api/v1/* 端点）经 {@link OpenApiAuthProvider} 注入面使用。
 *
 * <p><b>口径对称</b>：与 provider 侧 {@code AuthPartner} 验签口径对称（kernel {@link Base64Util}
 * urlSafe 编码＋服务端双解码兜底，round-trip 同一口径），头名取 authorization-api
 * {@link RequestConst#X_API_KEY}——与既有 {@code OAuth2Client#exchangePassword}／
 * {@code IdentitySnapshotClient} 的机器凭证头同款。</p>
 *
 * <p>🔴 <b>≠Bearer JWT 腿</b>：本类只管「调用方是谁」（机器对机器的 partner 身份），不产出
 * access_token／不碰 {@code Authorization} 头——「用户是谁」由 IdP 换票腿
 * （{@link OAuth2Client#exchangeCode}/{@link OAuth2Client#exchangePassword}）与验票腿
 * （{@link IdpTokenVerifier}）承担，两腿互不越界。</p>
 *
 * <p>装配：{@link OAuth2ClientAutoConfiguration} 同 {@code my.oauth2.client.enabled=true} 条件
 * 注册（partner 凭据与 OAuth2 客户端共用同一组 ac_partner 三件套，开关一体）。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class PartnerCredentialProvider implements OpenApiAuthProvider {

    private final OAuth2ClientProperties properties;

    public PartnerCredentialProvider(OAuth2ClientProperties properties) {
        this.properties = properties;
    }

    /** 机器凭据头写入口径：{@code x-api-key: Base64(clientId:clientSecret)}（Base64Util urlSafe） */
    @Override
    public void apply(HttpHeaders headers) {
        headers.set(RequestConst.X_API_KEY,
                Base64Util.encode(properties.getClientId() + ":" + properties.getClientSecret()));
    }
}
