package com.bidr.authorization.client.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: IdpBearerProperties
 * Description: IdP Bearer JWT 验签腿配置（T2.3c 棒1 新增）——公钥来源＝可配置属性。
 *
 * <p>三行 yml 接入：</p>
 * <pre>
 * my:
 *   idp:
 *     bearer:
 *       enabled: true
 *       public-key-base64: &lt;X.509 公钥 base64（容忍 PEM 头尾/空白，口径同 IdpJwtUtil）&gt;
 * </pre>
 * <p><b>默认未配置＝该腿禁用</b>（enabled 默认 false；公钥空串时 {@link IdpBearerVerifier#enabled()}
 * 亦为 false，verify 恒拒）——宿主不配置零影响。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
@ConfigurationProperties(prefix = "my.idp.bearer")
public class IdpBearerProperties {

    /** 是否装配 IdP Bearer 验签腿（默认 false——显式开启） */
    private boolean enabled = false;

    /** IdP 签发方 X.509 公钥（base64，容忍 PEM 头尾/空白）；空＝该腿禁用（verify 恒拒） */
    private String publicKeyBase64 = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }
}
