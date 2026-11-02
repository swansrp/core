package com.bidr.authorization.utils.jwt;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: IdpJwksUtil
 * Description: RSA 公钥 ⇒ JWK/JWKS 文档构建（T1.2 棒 6 纯加法，零第三方依赖——RFC 7517 形态
 * 用 {@code JsonUtil} 序列化）。
 *
 * <p>输出 JWK 字段：{@code kty=RSA, n=<modulus base64url>, e=<exponent base64url>, kid=<指纹>,
 * use=sig, alg=RS256}。kid 与票头 kid 同一真相（{@link IdpJwtUtil#computeKid}），消费方按
 * kid 选钥即可通过验签——同一钥对、一条 JWK，无需寻钥逻辑。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class IdpJwksUtil {

    /** JWK：密钥类型 */
    public static final String KTY_RSA = "RSA";
    /** JWK：用途（签名） */
    public static final String USE_SIG = "sig";
    /** JWK：算法 */
    public static final String ALG_RS256 = "RS256";

    private IdpJwksUtil() {
        // 静态工具，禁实例化
    }

    /**
     * 单把 RSA 公钥 ⇒ JWK Map（字段序稳定＝LinkedHashMap，序列化形态可预期）。
     *
     * @param publicKeyBase64 X.509 公钥配置值（容忍 PEM 头尾/空白，口径同 SsoJwtUtil）
     * @param kid             密钥标识（{@link IdpJwtUtil#computeKid} 产物）
     * @return JWK 字段 Map
     * @throws IllegalArgumentException 公钥不可解析
     */
    public static Map<String, Object> buildJwk(String publicKeyBase64, String kid) {
        RSAPublicKey publicKey;
        try {
            publicKey = IdpJwtUtil.parseRsaPublicKey(publicKeyBase64);
        } catch (Exception e) {
            throw new IllegalArgumentException("JWK公钥解析失败（需X.509 base64）");
        }
        Map<String, Object> jwk = new LinkedHashMap<>(8);
        jwk.put("kty", KTY_RSA);
        jwk.put("n", IdpJwtUtil.toBase64UrlUnsigned(publicKey.getModulus()));
        jwk.put("e", IdpJwtUtil.toBase64UrlUnsigned(publicKey.getPublicExponent()));
        jwk.put("kid", kid);
        jwk.put("use", USE_SIG);
        jwk.put("alg", ALG_RS256);
        return jwk;
    }

    /**
     * JWKS 文档：{@code {"keys":[<jwk>]}}。本 IdP 单钥对＝keys 恒一条（轮换面留 P2，登记待办）。
     *
     * @param publicKeyBase64 X.509 公钥配置值
     * @param kid             密钥标识
     * @return JWKS 文档 Map（直接交 JSON 序列化）
     */
    public static Map<String, Object> buildJwks(String publicKeyBase64, String kid) {
        List<Map<String, Object>> keys = new ArrayList<>(1);
        keys.add(buildJwk(publicKeyBase64, kid));
        Map<String, Object> doc = new LinkedHashMap<>(2);
        doc.put("keys", keys);
        return doc;
    }
}
