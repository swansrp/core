package com.bidr.authorization.client.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: IdpBearerProperties
 * Description: IdP Bearer JWT 验签腿配置（T2.3c 棒1 新增；2026-11-04 改为 JWKS 动态取钥）。
 *
 * <p>接入方式（**推荐：只配地址，零密钥**）：</p>
 * <pre>
 * my:
 *   idp:
 *     bearer:
 *       enabled: true
 *       server-base-url: http://10.3.6.60/inkhub    # 墨枢根地址，公钥运行时从 /web/oauth2/jwks.json 取
 * </pre>
 *
 * <p>静态兜底（可选，冷启动/纯离线场景）：{@code public-key-base64: <X.509 公钥 base64>}。</p>
 *
 * <p>🔴 <b>本类不再内置任何公钥常量</b>（2026-11-04 用户裁定）：墨枢已改为**按部署生成密钥对**
 * （每个部署一套），公钥不可能是"标准值"——写死进 starter 会让非标准部署静默验签失败。
 * 因此接入方只有两种合法配置：①{@code server-base-url}（从主系统拉）②显式 {@code public-key-base64}
 * （手工搬运，适合离线/排查）。两者都缺而 {@code enabled=true} ⇒ 由
 * {@link IdpBearerAutoConfiguration} 装配时 fail-fast，不再静默 401。</p>
 *
 * <p><b>默认未配置＝该腿禁用</b>（enabled 默认 false）——宿主不配置零影响。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03；JWKS 化 2026-11-04)
 * @since 2026-11-03
 */
@ConfigurationProperties(prefix = "my.idp.bearer")
public class IdpBearerProperties {

    /** 是否装配 IdP Bearer 验签腿（默认 false——显式开启） */
    private boolean enabled = false;

    /**
     * 墨枢根地址（如 {@code http://10.3.6.60/inkhub}）：公钥运行时向
     * {@code <server-base-url>/web/oauth2/jwks.json} 拉取（公开端点、无需鉴权）。
     * 空＝不拉 JWKS（纯静态形态，靠 {@link #publicKeyBase64} 兜底）。
     */
    private String serverBaseUrl = "";

    /**
     * 静态兜底公钥（X.509 SPKI base64，容忍 PEM 头尾/空白）。
     * 仅在「未配 server-base-url」或「JWKS 拉取失败且无内存快照」时使用；空＝无兜底。
     */
    private String publicKeyBase64 = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    public void setServerBaseUrl(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }
}