package com.bidr.authorization.utils.jwt;

import com.bidr.kernel.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: IdpJwtUtil
 * Description: 产品内部 IdP 的 RS256 JWT 签发/验签工具（T1.2 棒 6 纯加法，零第三方 JWT 依赖）。
 *
 * <p>与 {@link SsoJwtUtil} 的分工：那边是 SSO 票专用——票形状钉死 {@code {username, exp}} 两个
 * claim、header 固定、ttl 硬上界 900 秒（M15 纪律，不许改）；本工具面向 IdP access_token 的
 * <b>任意 claims</b> 形态（iss/sub/uid/username/name/tenant_ids/app_id/iat/exp 九 claim，
 * 存活 3600 秒），三段 base64url 点分串、{@code SHA256withRSA}（＝RS256）签名与密钥解析口径
 * （PKCS#8/X.509 DER base64、容忍 PEM 头尾与空白）与 SsoJwtUtil 完全同源（M-10 复核结论：
 * 签发工具不可直接复用，口径复用）。pom 全仓无 nimbus-jose-jwt/jjwt（取证见 coder-report），
 * 依"不引新第三方依赖为原则"手写。</p>
 *
 * <p>🔴 两条承重语义（照抄 SsoJwtUtil 承重契约）：
 * <ol>
 *   <li>{@link #verify(String, String)} <b>必须自判 exp</b>——JDK {@code Signature.verify} 只验
 *       签名、对过期票照样回 true（SsoJwtUtil M-4 同一事实）；exp 缺失同样视为无效；</li>
 *   <li>验票面<b>任何失败一律回 null、绝不抛</b>（错钥时 {@code Signature.verify} 回 false 而非抛；
 *       脏串/坏钥/解析失败统一收敛为 null），调用方以 null 判拒。</li>
 * </ol>
 * exp 判定含 {@value #LEEWAY_SECONDS} 秒时钟偏移容忍（跨机 NTP 漂移不得把刚签出的票判死，
 * SsoJwtUtil G-5 同源）。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
public class IdpJwtUtil {

    /** 本工具只认这一种 alg；票头与验票判定共用单一真相 */
    private static final String ALG_RS256 = "RS256";
    /** JDK 签名算法名（RSA/SHA-256，即 JWT 的 RS256） */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    /** exp 判定的时钟偏移容忍（秒）：{@code exp + LEEWAY <= now} 才判过期 */
    private static final long LEEWAY_SECONDS = 30L;
    /** token header：typ 值（RFC 7519） */
    private static final String TYP_JWT = "JWT";
    /** claim：签发方标识（本产品固定 inkhub） */
    public static final String CLAIM_ISS = "iss";
    /** claim：主体＝customerNumber（工号，open-api 数据面对齐口径与 SSO 票一致） */
    public static final String CLAIM_SUB = "sub";
    /** claim：ac_user.user_id */
    public static final String CLAIM_UID = "uid";
    /** claim：登录名（ac_user.user_name） */
    public static final String CLAIM_USERNAME = "username";
    /** claim：展示名 */
    public static final String CLAIM_NAME = "name";
    /** claim：绑定租户 id 集合 */
    public static final String CLAIM_TENANT_IDS = "tenant_ids";
    /** claim：接入方应用标识（＝ac_partner.app_key＝OAuth2 client_id） */
    public static final String CLAIM_APP_ID = "app_id";
    /** claim：签发时刻（epoch 秒） */
    public static final String CLAIM_IAT = "iat";
    /** claim：过期时刻（epoch 秒） */
    public static final String CLAIM_EXP = "exp";
    /** JWK/header：密钥标识（公钥指纹 SHA-256 前 8 字节 hex） */
    public static final String HEADER_KID = "kid";

    private IdpJwtUtil() {
        // 静态工具，禁实例化
    }

    /**
     * 签票：RSA 私钥（PKCS#8 base64，容忍 PEM 头尾/空白）＋任意 claims ⇒ 三段 base64url 点分串。
     * <p>header 形态 {@code {"alg":"RS256","typ":"JWT","kid":<kid>}}（kid 非空才带——JWKS 消费方
     * 按 kid 选钥）；iat/exp 由调用方算好放入 claims（exp 只算一次的纪律与 SsoJwtUtil G-8 同源：
     * 签进票内的值与响应体回吐的 expires_in 必须同源，禁止本工具内部再读时钟）。</p>
     *
     * @param privateKeyBase64 PKCS#8 私钥配置值
     * @param kid              密钥标识（{@link #computeKid} 的产物；空白＝header 不带 kid）
     * @param claims           载荷 claims（iat/exp 必填由调用方保证；顺序保留＝LinkedHashMap）
     * @return RS256 JWT（header.payload.signature，均 base64url 无填充）
     * @throws IllegalArgumentException 私钥不可解析或签名失败（签发是同步业务动作，须给出根因）
     */
    public static String sign(String privateKeyBase64, String kid, Map<String, Object> claims) {
        if (StringUtils.isBlank(privateKeyBase64)) {
            throw new IllegalArgumentException("IdP签发私钥为空");
        }
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("IdP票claims为空");
        }
        RSAPrivateKey privateKey = parsePrivateKey(privateKeyBase64);
        Map<String, Object> header = new LinkedHashMap<>(4);
        header.put("alg", ALG_RS256);
        header.put("typ", TYP_JWT);
        if (StringUtils.isNotBlank(kid)) {
            header.put(HEADER_KID, kid);
        }
        String headerJson = JsonUtil.toJson(header, false, false, true);
        String payloadJson = JsonUtil.toJson(claims, false, false, true);
        if (headerJson == null || payloadJson == null) {
            throw new IllegalArgumentException("IdP票序列化失败");
        }
        String signingInput = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8))
                + "." + base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64UrlEncode(signer.sign());
        } catch (Exception e) {
            log.error("[IdP出票]RS256签名失败", e);
            throw new IllegalArgumentException("IdP票签名失败");
        }
    }

    /**
     * 验票：验签 ＋ alg 钉死 RS256（防算法混淆）＋ 自判 exp（含 {@value #LEEWAY_SECONDS} 秒容忍）。
     *
     * @param token           三段点分票串
     * @param publicKeyBase64 X.509 公钥配置值（容忍 PEM 头尾/空白）
     * @return 验签通过且未过期时的 claims 原样 Map；其余一律 null（契约：失败不抛）
     */
    public static Map<String, Object> verify(String token, String publicKeyBase64) {
        return verify(token, publicKeyBase64, System.currentTimeMillis() / 1000L, LEEWAY_SECONDS);
    }

    /**
     * 验票的可注入时钟/偏移重载（仅为测试性；两参形态的行为契约不变）。
     *
     * @param token            三段点分票串
     * @param publicKeyBase64  X.509 公钥配置值
     * @param nowEpochSeconds  注入的"当前时刻"（epoch 秒）
     * @param leewaySeconds    exp 判定的时钟偏移容忍（秒）；判据＝{@code exp + leeway <= now} 才拒
     * @return 验签通过且未过期（含容忍）时的 claims；其余一律 null
     */
    public static Map<String, Object> verify(String token, String publicKeyBase64,
                                             long nowEpochSeconds, long leewaySeconds) {
        try {
            if (StringUtils.isBlank(token) || StringUtils.isBlank(publicKeyBase64)) {
                return null;
            }
            // 尾部空段保留（SsoJwtUtil NIT-7 同款）：挡 "h.p.s." 被缺省 split 规整成合法三段
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || StringUtils.isBlank(parts[0])
                    || StringUtils.isBlank(parts[1]) || StringUtils.isBlank(parts[2])) {
                return null;
            }
            Map<String, Object> header = parseJsonSegment(parts[0]);
            if (header == null || !ALG_RS256.equals(String.valueOf(header.get("alg")))) {
                return null;
            }
            PublicKey publicKey = parsePublicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            // 错钥时 verify() 回 false 而非抛（SsoJwtUtil M-5）——false 即拒
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                return null;
            }
            Map<String, Object> claims = parseJsonSegment(parts[1]);
            if (claims == null) {
                return null;
            }
            // exp 必须自判（SsoJwtUtil M-4）：缺失同样拒；exp + leeway <= now 才判过期（G-5）
            Long exp = toEpochSeconds(claims.get(CLAIM_EXP));
            if (exp == null) {
                return null;
            }
            if (exp + leewaySeconds <= nowEpochSeconds) {
                log.warn("[IdP验票]票据已过期拒收: exp={}, now={}, exp-now={}", exp, nowEpochSeconds,
                        exp - nowEpochSeconds);
                return null;
            }
            return claims;
        } catch (Throwable t) {
            // 兜底收窄契约：base64/解析/KeySpec 等一切异常 ⇒ 拒票不抛。
            // 只打异常类名（SsoJwtUtil G-7）：Jackson 异常消息会回显被解析内容，外部可控文本不得进日志
            log.warn("[IdP验票]票或公钥不可解析，按拒票处理: {}", t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * kid＝公钥指纹：公钥 X.509 DER 编码的 SHA-256 摘要前 8 字节 hex（16 字符）。
     * 确定性函数：同一公钥任意两次计算同值（JWKS 与票头共用单一真相）。
     *
     * @param publicKeyBase64 X.509 公钥配置值
     * @return 16 字符小写 hex；公钥不可解析时抛 IllegalArgumentException
     */
    public static String computeKid(String publicKeyBase64) {
        try {
            byte[] der = parsePublicKey(publicKeyBase64).getEncoded();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(der);
            byte[] head = new byte[8];
            System.arraycopy(digest, 0, head, 0, 8);
            StringBuilder sb = new StringBuilder(16);
            for (byte b : head) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("公钥指纹计算失败（需X.509 base64）");
        }
    }

    /**
     * 从 PKCS#8 私钥（CRT 形态）派生 X.509 公钥的 base64 配置值。
     * 供 {@code IdentityIdpProperties.resolvedPublicKey()} 在部署方只配私钥时现场派生；
     * 🔴 公钥指数取 {@code getPublicExponent()}（SsoJwtUtil verifyAndGetUsernameByPrivateKey
     * 2026-10-27 实测教训：取成 getPrivateExponent 验签必败）。
     *
     * @param privateKeyBase64 PKCS#8 私钥配置值
     * @return X.509 公钥 base64
     * @throws Exception 私钥缺失 CRT 参数或解析失败（调用方收敛为未配置）
     */
    public static String derivePublicKeyBase64(String privateKeyBase64) throws Exception {
        RSAPrivateKey privateKey = parsePrivateKey(privateKeyBase64);
        if (!(privateKey instanceof RSAPrivateCrtKey)) {
            throw new IllegalArgumentException("IdP私钥缺少CRT参数，无法派生公钥");
        }
        RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** JWK 的 n/e base64url 值：RSA 参数的无符号大端字节（剥 BigInteger 补位符号 0x00），无填充 */
    static String toBase64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return base64UrlEncode(bytes);
    }

    /** 供 {@link IdpJwksUtil} 构建本与配置面派生腿复用的公钥解析（包内可见） */
    static RSAPublicKey parseRsaPublicKey(String publicKeyBase64) throws Exception {
        return (RSAPublicKey) parsePublicKey(publicKeyBase64);
    }

    /**
     * 解析私钥配置值（PKCS#8）。剥离 PEM 头尾与全部空白后按标准 base64 解 DER
     * （口径同 SsoJwtUtil.decodeKeyMaterial）。
     */
    private static RSAPrivateKey parsePrivateKey(String privateKeyBase64) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeKeyMaterial(privateKeyBase64)));
        } catch (Exception e) {
            throw new IllegalArgumentException("IdP私钥解析失败（需PKCS#8 base64）");
        }
    }

    /** 解析公钥配置值（X.509）。剥 PEM 头尾与空白（M-6 口径）。 */
    private static PublicKey parsePublicKey(String publicKeyBase64) throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decodeKeyMaterial(publicKeyBase64)));
    }

    /** PEM armor（-----BEGIN/END XXX-----）与任意空白剥离后的 base64 ⇒ DER 字节 */
    private static byte[] decodeKeyMaterial(String keyConfigValue) {
        String stripped = keyConfigValue
                .replaceAll("-----[A-Z ]*-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }

    /**
     * base64url 段 ⇒ JSON Map（SsoJwtUtil G-7 同款纪律：走 readJsonOrThrow，本方法不 catch；
     * 失败一律冒到 {@link #verify} 的兜底 catch 收敛为单行 WARN＋null）。
     */
    private static Map<String, Object> parseJsonSegment(String base64UrlSegment) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(base64UrlSegment), StandardCharsets.UTF_8);
        return JsonUtil.readJsonOrThrow(json, HashMap.class, String.class, Object.class);
    }

    /** 数值型 claim 兼容 Integer/Long/String 形态，非数回 null */
    static Long toEpochSeconds(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
