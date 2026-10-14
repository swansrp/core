package com.bidr.authorization.utils.jwt;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: SsoJwtUtil
 * Description: 墨枢自签 SSO 票的 JDK 原生 RSA-JWT 工具（M15 棒 0b-1 纯加法，零第三方 JWT 依赖）。
 * <p>
 * 票形状＝三段 base64url 点分串（与 auth0 java-jwt／现网 idm 验签器字节级互通，取证 M-1/M-2/M-3）：
 * 头固定 {@code {"alg":"RS256","typ":"JWT"}}，payload 只有 {@code username} 与 {@code exp}（epoch 秒）两个 claim，
 * 签名算法 {@code SHA256withRSA}（＝RS256）。密钥解析口径照现网 idm RsaUtil（M-6）：
 * 公钥＝X.509 DER 的 base64（{@link X509EncodedKeySpec}）、私钥＝PKCS#8 DER 的 base64
 * （{@link PKCS8EncodedKeySpec}），配置值容忍 PEM 头尾与任意空白混入。
 * </p>
 * <p>
 * 🔴 两条承重语义（不许改）：
 * <ol>
 *   <li>{@link #verifyAndGetUsername(String, String)} <b>必须自判 exp</b>——JDK {@code Signature.verify}
 *       只验签名、对过期票照样回 true（M-4 实测）；exp 缺失同样视为无效；</li>
 *   <li>验票面<b>任何失败一律回 null、绝不抛</b>（错钥时 {@code Signature.verify} 回 false 而非抛，M-5；
 *       脏串/坏钥/解析失败统一收敛为 null），调用方以 null 判拒。</li>
 * </ol>
 * </p>
 * <p>
 * exp 判定含固定 {@value #LEEWAY_SECONDS} 秒时钟偏移容忍（承诺面 38／G-5：出票与验票常跨两台机，
 * NTP 漂移就会把刚签出的票判死；红线骨架，非运维旋钮，故类内常量、不开配置键）。
 * 🔴 由此合成＝票的<b>实际最大存活＝ttl(60)＋leeway(30)＝90 秒</b>，对外口径与接入指南红线按 90 秒写，不得写 60 秒。
 * JSON 段解析走 {@code JsonUtil.readJsonOrThrow}（G-7：{@code readJson} 系列 catch 一切后
 * {@code log.error("", e)}＝无主全栈进日志、且 Jackson 消息回显被解析内容＝外部可控文本进 ERROR 日志，
 * 按 ERROR 计数告警的通道会被一条垃圾票放大）。目标形态＝拒票＝单行 WARN、无 stack、无回显。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Slf4j
public class SsoJwtUtil {

    /** 本工具只认这一种 alg；票头与验票判定共用单一真相（NIT-9，消双真相） */
    private static final String ALG_RS256 = "RS256";
    /** 票头（RS256 固定形状，与 auth0/idm 产票同形；由 {@link #ALG_RS256} 拼出，字节形态与原字面量一致） */
    private static final String HEADER_JSON = "{\"alg\":\"" + ALG_RS256 + "\",\"typ\":\"JWT\"}";
    /** JDK 签名算法名（RSA/SHA-256，即 JWT 的 RS256） */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    /** 出票存活秒数上界（秒，硬编码红线非配置键，NIT-8/G-5 同源纪律） */
    private static final long MAX_TTL_SECONDS = 900L;
    /**
     * exp 判定的时钟偏移容忍（秒，G-5）：{@code exp + LEEWAY <= now} 才判过期——跨机 NTP 漂移不得把刚签出的票判死。
     * <p>🔴 合成后果（10-14 review F-B）＝票的<b>实际最大存活＝ttl + 本常量</b>：四档 yml 的 ttl＝60 ⇒
     * 最长 90 秒而非 60 秒。本批票走 URL 传且无 jti 吊销，评估暴露窗口时按 90 秒算，接入指南的红线同口径。</p>
     */
    private static final long LEEWAY_SECONDS = 30L;
    /** claim：登录用户名（对应 ac_user 口径） */
    public static final String CLAIM_USERNAME = "username";
    /** claim：过期时刻（epoch 秒） */
    public static final String CLAIM_EXP = "exp";

    private SsoJwtUtil() {
        // 静态工具，禁实例化
    }

    /**
     * 签票：RSA 私钥（PKCS#8 base64，容忍 PEM 头尾/空白）＋username＋存活秒数 ⇒ 三段 base64url 点分串。
     * <p>参数语义＝<b>相对存活秒数 ttl</b>（exp = 当前时刻 + ttl）；与 {@link #signWithExp} 的
     * <b>绝对 exp</b> 参数语义不同，两者不得混用。ttl 上界 {@value #MAX_TTL_SECONDS} 秒硬闸（NIT-8）：
     * 本批无 jti 吊销且票走 URL 传，运维按毫秒口径误填 86400000 ＝发一枚约 2.7 年不可撤的 bearer 凭证。</p>
     *
     * @param privateKeyBase64 PKCS#8 私钥配置值
     * @param username         票内用户名 claim
     * @param ttlSeconds       票据存活秒数（exp = 当前时刻 + ttl），必须为正且不超过 900
     * @return RS256 票（header.payload.signature，均 base64url 无填充）
     */
    public static String sign(String privateKeyBase64, String username, long ttlSeconds) {
        // ttl 的两端判据必须在委托之前做完：signWithExp 收到的是绝对 exp，非正 ttl 折算后仍是"很大的正数"
        Validator.assertTrue(ttlSeconds > 0, ErrCodeSys.PA_PARAM_FORMAT, "SSO票据有效期");
        Validator.assertTrue(ttlSeconds <= MAX_TTL_SECONDS, ErrCodeSys.PA_PARAM_FORMAT,
                "SSO票据有效期超出允许上限（最大" + MAX_TTL_SECONDS + "秒）");
        long exp = System.currentTimeMillis() / 1000L + ttlSeconds;
        return signWithExp(privateKeyBase64, username, exp);
    }

    /**
     * 签票（绝对 exp 形态，G-8）：调用方先算好 exp epoch 秒，签入票内后同一值回吐给响应体，
     * 保证 {@code expiresAt} 与票内 {@code exp} 严格等值（双读 currentTimeMillis 的跨秒差 1 秒即根因）。
     * <p>🔴 参数语义＝<b>绝对过期时刻（epoch 秒）</b>；与 {@link #sign} 的相对 ttl 语义相反，不得混用。
     * 上界同样硬闸（NIT-8）：{@code exp - now} 不得超过 {@value #MAX_TTL_SECONDS} 秒——
     * 出票编排走的是本方法（G-8 单次取值），只在 {@link #sign} 设闸等于给编排留了绕过上界的洞。</p>
     *
     * @param privateKeyBase64 PKCS#8 私钥配置值
     * @param username         票内用户名 claim
     * @param expEpochSeconds  过期时刻（epoch 秒，绝对值），必须<b>不早于当前时刻</b>且距今不超过 900 秒
     * @return RS256 票（header.payload.signature，均 base64url 无填充）
     */
    public static String signWithExp(String privateKeyBase64, String username, long expEpochSeconds) {
        Validator.assertNotBlank(privateKeyBase64, ErrCodeSys.PA_PARAM_FORMAT, "SSO出票私钥");
        Validator.assertNotBlank(username, ErrCodeSys.PA_PARAM_FORMAT, "用户名");
        // 🔴 下界判据＝exp 不得早于当前时刻：{@link #sign} 的 ttl 是相对口径、本方法是绝对口径，
        // 只闸 exp>0 时把 ttl 值（如 60）误当 epoch 传入会签出一枚"签出来就已过期"的票且全程静默（10-14 review F-C）。
        // 取"不早于"而非"严格晚于"＝ttl=1 时 now+1 恰好跨秒会让严格判据随机翻红，而 exp==now 的票仍在 30 秒容忍内可用
        Validator.assertTrue(expEpochSeconds >= System.currentTimeMillis() / 1000L,
                ErrCodeSys.PA_PARAM_FORMAT, "SSO票据过期时刻不得早于当前时刻（疑似把 ttl 口径当 epoch 传入）");
        Validator.assertTrue(expEpochSeconds - System.currentTimeMillis() / 1000L <= MAX_TTL_SECONDS,
                ErrCodeSys.PA_PARAM_FORMAT, "SSO票据有效期超出允许上限（最大" + MAX_TTL_SECONDS + "秒）");
        RSAPrivateKey privateKey = (RSAPrivateKey) parsePrivateKey(privateKeyBase64);
        Map<String, Object> claims = new LinkedHashMap<>(4);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_EXP, expEpochSeconds);
        // ignoreIndent=true ⇒ 紧凑无换行 JSON（与上游产票形态一致）
        String payload = JsonUtil.toJson(claims, false, false, true);
        Validator.assertNotNull(payload, ErrCodeSys.SYS_ERR_MSG, "SSO票据内容序列化失败");
        String signingInput = base64UrlEncode(HEADER_JSON.getBytes(StandardCharsets.UTF_8))
                + "." + base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64UrlEncode(signer.sign());
        } catch (Exception e) {
            log.error("[SSO出票]RS256签名失败", e);
            return Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "SSO票据签名失败");
        }
    }

    /**
     * 验票并取用户名：验签 + 自判 exp（M-4，含 {@value #LEEWAY_SECONDS} 秒时钟偏移容忍）+ alg 钉死 RS256（防算法混淆）。
     * <p>🔴 契约＝任何失败一律返回 null，绝不抛（承诺面 2；棒 0b-2 的 SSO 内置验票 handler 依赖此签名）。</p>
     *
     * @param token            三段点分票串
     * @param publicKeyBase64  X.509 公钥配置值（容忍 PEM 头尾/空白）
     * @return 验签通过且未过期时的 username claim；其余一律 null
     */
    public static String verifyAndGetUsername(String token, String publicKeyBase64) {
        return verifyAndGetUsername(token, publicKeyBase64, System.currentTimeMillis() / 1000L, LEEWAY_SECONDS);
    }

    /**
     * 验票的可注入时钟/偏移重载（仅为测试性，G-5；两参形态的行为契约不变）。
     *
     * @param token            三段点分票串
     * @param publicKeyBase64  X.509 公钥配置值
     * @param nowEpochSeconds  注入的"当前时刻"（epoch 秒）
     * @param leewaySeconds    exp 判定的时钟偏移容忍（秒）；判据＝{@code exp + leeway <= now} 才拒
     * @return 验签通过且未过期（含容忍）时的 username claim；其余一律 null
     */
    public static String verifyAndGetUsername(String token, String publicKeyBase64,
                                              long nowEpochSeconds, long leewaySeconds) {
        try {
            if (StringUtils.isBlank(token) || StringUtils.isBlank(publicKeyBase64)) {
                return null;
            }
            // NIT-7：limit=-1 保留尾部空段，挡 "h.p.s." 被缺省 split 规整成合法三段
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
            // M-5：错钥时 verify() 回 false 而非抛——false 即拒
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                return null;
            }
            Map<String, Object> claims = parseJsonSegment(parts[1]);
            if (claims == null) {
                return null;
            }
            // M-4：JDK Signature.verify 不判 exp（过期票回 true），必须在此自判；exp 缺失同样拒。
            // G-5：exp + leeway <= now 才拒（时钟偏移容忍）；拒因走单行 WARN，只打三个数，不打票不打 claim。
            Long exp = toEpochSeconds(claims.get(CLAIM_EXP));
            if (exp == null) {
                return null;
            }
            if (exp + leewaySeconds <= nowEpochSeconds) {
                log.warn("[SSO验票]票据已过期拒签: exp={}, now={}, exp-now={}", exp, nowEpochSeconds, exp - nowEpochSeconds);
                return null;
            }
            // NIT-6：username 只接受 String 形态 claim，非字符串（对象/数组/数字）一律拒——
            // 否则 String.valueOf 会把 {a=1} 形态的 Map 当用户名返回给消费侧查人
            Object rawUsername = claims.get(CLAIM_USERNAME);
            if (rawUsername == null) {
                return null;
            }
            if (!(rawUsername instanceof String)) {
                log.warn("[SSO验票]username claim 非字符串形态，按拒票处理: type={}",
                        rawUsername.getClass().getSimpleName());
                return null;
            }
            String username = (String) rawUsername;
            return StringUtils.isBlank(username) ? null : username;
        } catch (Throwable t) {
            // 兜底收窄契约：包括 base64/解析/KeySpec 等一切异常 ⇒ 拒票不抛出。
            // 只打异常类名（G-7）：Jackson 消息会回显被解析内容，外部可控文本不得进日志
            log.warn("[SSO验票]票或公钥不可解析，按拒票处理: {}", t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析私钥配置值（PKCS#8）。剥离 PEM 头尾与全部空白后按标准 base64 解 DER。
     * 供出票面内部使用；解析失败按参数格式业务拒绝（出票是同步业务动作，须给出根因文案）。
     */
    private static PrivateKey parsePrivateKey(String privateKeyBase64) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeKeyMaterial(privateKeyBase64)));
        } catch (Exception e) {
            log.error("[SSO出票]私钥解析失败（需PKCS#8 base64）", e);
            return Validator.assertException(ErrCodeSys.PA_PARAM_FORMAT, "SSO出票私钥");
        }
    }

    /**
     * 解析公钥配置值（X.509）。剥 PEM 头尾与空白（M-6 口径＝先 replaceAll 头尾再 replaceAll \s+）。
     * 验票面调用方已包 try/catch，这里直接抛由上层收敛为 null。
     */
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
     * base64url 段 ⇒ JSON Map（G-7：走 readJsonOrThrow，本方法不 catch）。
     * <p>🔴 不得改回 {@code JsonUtil.readJson}：它 catch 一切后 {@code log.error("", e)}＝无主全栈进日志、
     * 且 Jackson 异常消息回显被解析内容＝外部可控文本进 ERROR 日志，按 ERROR 计数告警的通道会被一条垃圾票放大。
     * 解析失败/非法 base64 一律冒到 {@code verifyAndGetUsername} 的兜底 catch，收敛为单行 WARN（只打类名）＋null。
     * HashMap 双类型参是 JsonUtil 泛型构造的必填形态。</p>
     */
    private static Map<String, Object> parseJsonSegment(String base64UrlSegment) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(base64UrlSegment), StandardCharsets.UTF_8);
        return JsonUtil.readJsonOrThrow(json, HashMap.class, String.class, Object.class);
    }

    /** exp 兼容 Integer/Long/String 数值形态，非数回 null */
    private static Long toEpochSeconds(Object value) {
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
