package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Title: IdpBearerVerifier
 * Description: client 侧 IdP Bearer JWT 解析腿（T2.3c 棒1 新增；2026-11-04 接 JWKS 动态取钥）——
 * 与"本地 Redis 票"（{@code AuthTokenUtil}/{@code TokenServiceImpl}）并立的第二条令牌解析腿：
 * 用 api {@link IdpJwtUtil}（与 IdP 签发侧同一工具，RS256 同源口径）对 Bearer JWT 验签，
 * 产出最小 claims 载体（{@code Map<String, Object>} 原样 claims）。
 *
 * <p><b>取钥</b>＝{@link JwksKeyProvider}：按票头 kid 从「JWKS 缓存（{@code server-base-url}）→
 * 刷新 → 上次快照 → 静态兜底（{@code public-key-base64}）」依次取。两者都未配置＝该腿禁用，
 * {@link #verifyBearer(String)} 恒拒（fail-closed）；验签/过期/脏串同样一律 null 绝不抛。</p>
 *
 * <p><b>受众</b>：配置了期望接入方（{@link IdpBearerProperties#getAppId()}，缺省回退
 * {@code my.oauth2.client.client-id}）时，票面 {@code app_id} 必须全串相等，缺失同样拒——
 * 否则同钥下"签给 A 子系统的票"会被 B 子系统接受。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03；JWKS 化 2026-11-04)
 * @since 2026-11-03
 */
public class IdpBearerVerifier {

    private final JwksKeyProvider keyProvider;

    /** 期望接入方（票面 app_id）；null＝不做受众校验（仅兼容"未接 OAuth2 client"的老形态） */
    private final String expectedAppId;

    public IdpBearerVerifier(IdpBearerProperties properties) {
        this(properties == null ? null
                        : new JwksKeyProvider(properties.getServerBaseUrl(), properties.getPublicKeyBase64()),
                properties == null ? null : properties.getAppId());
    }

    /** 直构形态（单测/手工接线用）：只有静态公钥，不拉 JWKS，不做受众校验 */
    public IdpBearerVerifier(String publicKeyBase64) {
        this(new JwksKeyProvider(null, publicKeyBase64));
    }

    /** 注入取钥器（JWKS 动态 / 静态 / 测试桩），不做受众校验 */
    public IdpBearerVerifier(JwksKeyProvider keyProvider) {
        this(keyProvider, null);
    }

    /**
     * @param keyProvider   取钥器（可 null＝恒拒）
     * @param expectedAppId 期望接入方（{@code ac_partner.app_key}）；空＝不做受众校验
     */
    public IdpBearerVerifier(JwksKeyProvider keyProvider, String expectedAppId) {
        this.keyProvider = keyProvider;
        this.expectedAppId = StringUtils.trimToNull(expectedAppId);
    }

    /** 该腿是否启用＝有远端 JWKS 或静态公钥（都无则 verify 恒拒） */
    public boolean enabled() {
        return keyProvider != null && keyProvider.enabled();
    }

    /**
     * 验 Bearer JWT：容忍 "Bearer &lt;token&gt;" 前缀与裸 token 两种写法（统一取末段，
     * 口径同 {@code AuthTokenUtil.resolveToken}）。
     *
     * @param bearerToken Authorization 头值或裸 JWT
     * @return 验签通过、未过期且受众匹配时的 claims（最小载体）；腿禁用／无可用钥／验签失败／
     *         过期／脏串／app_id 缺失或不符一律 null
     */
    public Map<String, Object> verifyBearer(String bearerToken) {
        if (!enabled() || StringUtils.isBlank(bearerToken)) {
            return null;
        }
        String token = bearerToken.trim();
        int space = token.lastIndexOf(' ');
        if (space >= 0) {
            token = token.substring(space + 1).trim();
        }
        if (token.isEmpty()) {
            return null;
        }
        String publicKeyBase64 = keyProvider.resolveForToken(token);
        if (StringUtils.isBlank(publicKeyBase64)) {
            return null;
        }
        Map<String, Object> claims = IdpJwtUtil.verify(token, publicKeyBase64);
        if (claims == null) {
            return null;
        }
        // 受众：配置了期望值就只收"签给自己"的票；票面缺失同样拒（没有受众≠谁都收）
        if (expectedAppId != null) {
            Object appId = claims.get(IdpJwtUtil.CLAIM_APP_ID);
            if (appId == null || !expectedAppId.equals(String.valueOf(appId))) {
                return null;
            }
        }
        return claims;
    }
}