package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Title: IdpTokenVerifier
 * Description: IdP access_token（RS256 JWT）本地验签器（T3.1 棒0：自 epc 的 InkHubJwtVerifier
 * 迁入 core，语义源逐条保持）。
 *
 * <p>实现走 api 的 {@link IdpJwtUtil#verify(String, String)}（与 IdP 签发侧同一工具：RS256 同源口径，
 * 验签＋alg 钉死＋自判 exp＋时钟偏移容忍），在其上补三条 IdP 语义：</p>
 * <ul>
 *   <li>{@code username} claim 必须非空——账号对齐键（宿主按 user_name 查人）；</li>
 *   <li>{@code iss} claim <b>在场时</b>必须等于 {@link #EXPECTED_ISS}（缺席容忍老签发器）——
 *       防别的 RS256 源用同形 token 混入；</li>
 *   <li>受众：配置了期望接入方时，{@code app_id} claim 必须与之全串相等，<b>缺失同样拒</b>——
 *       IdP 同钥给所有接入方签票，缺这一闸等于"签给 A 的票 B 也认"。期望值来自
 *       {@link IdpBearerProperties#getAppId()}（缺省由装配层回退 {@code my.oauth2.client.client-id}）。</li>
 * </ul>
 *
 * <p>契约与 {@link IdpBearerVerifier} 同族：验票面任何失败一律回 {@code null}、绝不抛，
 * 调用方以 null 判拒；公钥未配置时同样恒拒（fail-closed）。
 * 🔴 只打异常类名不回显内容（外部可控文本不进日志）。</p>
 *
 * <p>不剥离 "Bearer " 前缀——入参是 token 本体（前缀剥离属 HTTP 头解析面，见 IdpBearerVerifier）。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@Slf4j
public class IdpTokenVerifier {

    /** IdP 固定签发方标识（与 IdpJwtUtil.CLAIM_ISS 的签发值同源） */
    public static final String EXPECTED_ISS = "inkhub";

    /** 取钥器（JWKS 动态优先，静态兜底）；null＝未配置，验票恒拒 */
    private final JwksKeyProvider keyProvider;

    /** 期望接入方（票面 app_id）；null＝不做受众校验（仅兼容"未接 OAuth2 client"的老形态） */
    private final String expectedAppId;

    public IdpTokenVerifier(IdpBearerProperties properties) {
        this(properties == null ? null
                        : new JwksKeyProvider(properties.getServerBaseUrl(), properties.getPublicKeyBase64()),
                properties == null ? null : properties.getAppId());
    }

    /** 直构形态（单测/手工接线用）：只有静态公钥，不拉 JWKS，不做受众校验 */
    public IdpTokenVerifier(String publicKeyBase64) {
        this(new JwksKeyProvider(null, publicKeyBase64));
    }

    /** 注入取钥器（JWKS 动态 / 静态 / 测试桩），不做受众校验 */
    public IdpTokenVerifier(JwksKeyProvider keyProvider) {
        this(keyProvider, null);
    }

    /**
     * @param keyProvider   取钥器（可 null＝恒拒）
     * @param expectedAppId 期望接入方（{@code ac_partner.app_key}）；空＝不做受众校验
     */
    public IdpTokenVerifier(JwksKeyProvider keyProvider, String expectedAppId) {
        this.keyProvider = keyProvider;
        this.expectedAppId = StringUtils.trimToNull(expectedAppId);
    }

    /**
     * 验签 IdP access_token 并解析身份 claims。
     *
     * @param token 三段 base64url 点分 JWT（IdP /web/oauth2/token 的 access_token）
     * @return 身份 principal；验签失败/过期/username 缺失/iss 不符/app_id 不符/无可用钥时一律 {@code null}（不抛）
     */
    public IdpPrincipal verify(String token) {
        if (StringUtils.isBlank(token) || keyProvider == null) {
            return null;
        }
        // 取钥：按票头 kid（JWKS 缓存 → 刷新 → 快照 → 静态兜底）；无可用钥即拒（fail-closed）
        String publicKeyBase64 = keyProvider.resolveForToken(token);
        if (StringUtils.isBlank(publicKeyBase64)) {
            log.warn("[IdP验签]无可用公钥（JWKS 未拉到且无静态兜底），按拒票处理");
            return null;
        }
        Map<String, Object> claims;
        try {
            claims = IdpJwtUtil.verify(token, publicKeyBase64);
        } catch (Throwable t) {
            log.warn("[IdP验签]access_token 验签失败，按拒票处理: {}", t.getClass().getSimpleName());
            return null;
        }
        if (claims == null) {
            log.warn("[IdP验签]access_token 验签未通过（签名/过期/钥未配），按拒票处理");
            return null;
        }
        // iss 校验（更稳腿）：claim 缺席容忍（老签发器），在场则必须等于 inkhub
        Object iss = claims.get(IdpJwtUtil.CLAIM_ISS);
        if (iss != null && !EXPECTED_ISS.equals(String.valueOf(iss))) {
            log.warn("[IdP验签]iss 不符，按拒票处理");
            return null;
        }
        // 受众校验：本宿主只收"签给自己"的票；配置了期望值而票面缺失或不符一律拒
        if (!matchesExpectedAppId(claims)) {
            log.warn("[IdP验签]app_id 缺失或与期望接入方不符，按拒票处理");
            return null;
        }
        Object usernameValue = claims.get(IdpJwtUtil.CLAIM_USERNAME);
        String username = usernameValue == null ? null : String.valueOf(usernameValue);
        if (StringUtils.isBlank(username)) {
            log.warn("[IdP验签]username claim 缺失，无法对齐本地账号，按拒票处理");
            return null;
        }
        IdpPrincipal principal = new IdpPrincipal();
        principal.setUsername(username);
        Object sub = claims.get(IdpJwtUtil.CLAIM_SUB);
        principal.setCustomerNumber(sub == null ? null : String.valueOf(sub));
        principal.setUid(toUid(claims.get(IdpJwtUtil.CLAIM_UID)));
        Object name = claims.get(IdpJwtUtil.CLAIM_NAME);
        principal.setName(name == null ? null : String.valueOf(name));
        return principal;
    }

    /**
     * 受众判定：未配置期望值＝不校验（老形态兼容）；配置了则要求票面 app_id 全串相等。
     * 票面缺失/空值同样判拒——"没有受众"不等于"谁都收"。
     */
    private boolean matchesExpectedAppId(Map<String, Object> claims) {
        if (expectedAppId == null) {
            return true;
        }
        Object appId = claims.get(IdpJwtUtil.CLAIM_APP_ID);
        return appId != null && expectedAppId.equals(String.valueOf(appId));
    }

    /** uid 容 Number/String 两形态；坏值（非数字）回 null，不抛 */
    private static Long toUid(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            log.warn("[IdP验签]uid claim 非数字，按缺失处理");
            return null;
        }
    }
}
