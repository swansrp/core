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

    /**
     * 墨枢标准 IdP 公钥（X.509 SubjectPublicKeyInfo DER 的 base64，392 字符）。
     * <p>
     * 🔴 内置默认值的目的＝**接入方引模块即零配置**：此前每个接入方都要在自己档 yml 手抄整串
     * （epc 三档各一份，抄错一个字符＝验签全挂且报错不指向公钥）。公钥是公开信息（InkHub 的
     * GET /web/oauth2/jwks.json 本就公开发布），内置不构成泄密；**私钥永不进本模块**
     * （只在墨枢部署侧 .env 的 IDENTITY_IDP_PRIVATE_KEY）。
     * <p>
     * 覆盖语义：接入方显式配 my.idp.bearer.public-key-base64 即以配置为准（多平台/密钥轮换）；
     * 不配则用本默认值。守卫＝本模块 IdpBearerPropertiesTest（非空/长度/可解析）＋ InkHub 仓
     * SsoConfigSurfaceConventionTest（与 sso-key/pub-oneline.txt 逐字比对，防轮换漏改）。
     */
    public static final String DEFAULT_PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqmFz48rjBy9CZQG0kBO/AygYsplLjliGH/W1Vp8fnMbk0x75G8tRV75iDsacbKt+2bzfXiy4snoipw48XgJCes+tSvRRaU/9KrG3Xeg8KI7QelM/5VdmTNZRzDj2+TBjuiXNkGtpLo0nXG52v69/So8MBv4t9xYhfTZiQSj1gdUZjxrbKhY43/47sX9FkRoFdsHeBIyo2nmHqbay1q2FUsx6HgkV7aurDitFBKrdYccVg1OJ9WC0dxvG0yyYeL1TSyiyvSsbXVngHP7Plflsw8YoWape2OfvIMBPUP54ClK/Z2FlfLf87Vuj4H2YMtTGU+VP+z5U2b/28gdOVd/UyQIDAQAB";

    /** 是否装配 IdP Bearer 验签腿（默认 false——显式开启） */
    private boolean enabled = false;

    /** IdP 签发方 X.509 公钥（base64，容忍 PEM 头尾/空白）；空＝该腿禁用（verify 恒拒） */
    private String publicKeyBase64 = DEFAULT_PUBLIC_KEY_BASE64;

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
