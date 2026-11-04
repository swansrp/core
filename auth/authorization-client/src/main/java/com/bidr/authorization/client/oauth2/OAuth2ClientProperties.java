package com.bidr.authorization.client.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: OAuth2ClientProperties
 * Description: IdP OAuth2 授权码客户端配置（T3.1 棒0 新增）——宿主以三行 yml 接入换票腿。
 *
 * <pre>
 * my:
 *   oauth2:
 *     client:
 *       enabled: true
 *       base-url: http://idp-host/inkhub
 *       client-id: epc-ai
 *       client-secret: &lt;ac_partner.app_secret&gt;
 *       redirect-uri: http://127.0.0.1:18081/oauth2/callback
 * </pre>
 *
 * <p><b>默认 enabled=false＝不装配</b>（{@link OAuth2ClientAutoConfiguration} 按开关条件装配），
 * 宿主不配置零影响。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@ConfigurationProperties(prefix = "my.oauth2.client")
public class OAuth2ClientProperties {

    /** 是否装配 OAuth2 授权码客户端（默认 false——显式开启） */
    private boolean enabled = false;

    /** IdP 服务基地址（尾斜杠会被裁掉；authorize/token 两条腿都挂在其下） */
    private String baseUrl = "";

    /** OAuth2 客户端标识（＝ac_partner.app_key） */
    private String clientId = "";

    /** OAuth2 客户端密钥（＝ac_partner.app_secret；走 form body，不走 Basic 头） */
    private String clientSecret = "";

    /** 授权回调地址（authorize 与 exchange 两处必须同值） */
    private String redirectUri = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}
