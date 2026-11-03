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
 * 验签＋alg 钉死＋自判 exp＋时钟偏移容忍），在其上补两条 IdP 语义：</p>
 * <ul>
 *   <li>{@code username} claim 必须非空——账号对齐键（宿主按 user_name 查人）；</li>
 *   <li>{@code iss} claim <b>在场时</b>必须等于 {@link #EXPECTED_ISS}（缺席容忍老签发器）——
 *       防别的 RS256 源用同形 token 混入。</li>
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

    /** 验签公钥（X.509 base64，容忍 PEM 头尾/空白）；空＝未配置，验票一律拒 */
    private final String publicKeyBase64;

    public IdpTokenVerifier(IdpBearerProperties properties) {
        this(properties == null ? null : properties.getPublicKeyBase64());
    }

    /** 直构形态（单测/手工接线用）：公钥空白即恒拒 */
    public IdpTokenVerifier(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64 == null ? "" : publicKeyBase64.trim();
    }

    /**
     * 验签 IdP access_token 并解析身份 claims。
     *
     * @param token 三段 base64url 点分 JWT（IdP /web/oauth2/token 的 access_token）
     * @return 身份 principal；验签失败/过期/username 缺失/iss 不符/未配钥时一律 {@code null}（不抛）
     */
    public IdpPrincipal verify(String token) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(publicKeyBase64)) {
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
