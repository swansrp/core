package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Title: IdpBearerVerifier
 * Description: client 侧 IdP Bearer JWT 解析腿（T2.3c 棒1 新增）——与"本地 Redis 票"
 * （{@code AuthTokenUtil}/{@code TokenServiceImpl}）并立的第二条令牌解析腿：
 * 用 api {@link IdpJwtUtil}（与 IdP 签发侧同一工具，RS256 同源口径）对 Bearer JWT 验签，
 * 产出最小 claims 载体（{@code Map<String, Object>} 原样 claims）。
 *
 * <p><b>禁用语义（fail-closed）</b>：公钥未配置（{@link #enabled()} false）时
 * {@link #verifyBearer(String)} 恒拒（null）；验签/过期/脏串同样一律 null 绝不抛
 * （IdpJwtUtil 契约），调用方以 null 判拒。公钥来源＝
 * {@link IdpBearerProperties#getPublicKeyBase64()}（可配置属性，默认未配置）。</p>
 *
 * <p>消费方：棒4 RemoteAuthzChecker/登录链接 client 运行时的宿主；本棒只交付组件
 * ＋同源单测（测试钥对签的票验过／篡改 payload 拒／未配置公钥禁用）。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public class IdpBearerVerifier {

    private final String publicKeyBase64;

    public IdpBearerVerifier(IdpBearerProperties properties) {
        this(properties == null ? null : properties.getPublicKeyBase64());
    }

    /** 直构形态（单测/手工接线用）：公钥空白即禁用腿 */
    public IdpBearerVerifier(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    /** 该腿是否启用＝公钥已配置（未配置则 verify 恒拒） */
    public boolean enabled() {
        return StringUtils.isNotBlank(publicKeyBase64);
    }

    /**
     * 验 Bearer JWT：容忍 "Bearer &lt;token&gt;" 前缀与裸 token 两种写法（统一取末段，
     * 口径同 {@code AuthTokenUtil.resolveToken}）。
     *
     * @param bearerToken Authorization 头值或裸 JWT
     * @return 验签通过且未过期时的 claims（最小载体）；腿禁用／验签失败／过期／脏串一律 null
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
        return IdpJwtUtil.verify(token, publicKeyBase64);
    }
}
