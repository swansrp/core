package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: JwksKeyProvider
 * Description: IdP 公钥动态获取（JWKS 客户端）——**接入方零密钥配置**的实现件。
 *
 * <p>为什么需要它（2026-11-04 定稿）：墨枢改为**按部署生成密钥对**（不再是全公司共用一把，
 * 见 project-setup 的 hub/client 两种角色），公钥就不可能预先写进接入方代码或配置；
 * 否则 N 个墨枢部署 × M 个接入子系统 = N×M 次手工配公钥，且每次换钥要全量同步。
 * 接入方只配 {@code my.idp.bearer.server-base-url}，公钥在运行时向
 * {@code <base>/web/oauth2/jwks.json} 取（该端点公开、无需鉴权）。</p>
 *
 * <p><b>取钥优先级链</b>（任一步命中即返回，全部失败回 null ⇒ 调用方 fail-closed 拒票）：</p>
 * <ol>
 *   <li>内存缓存命中且未过期（{@link #ttlMillis}，默认 1 小时）——零网络；</li>
 *   <li>未配 {@code server-base-url}（纯静态形态）⇒ 直接用静态公钥（{@link #staticPublicKeyBase64}）；</li>
 *   <li>过期/未命中 ⇒ 刷新一次 JWKS（单飞：并发只发一个请求，防击穿）；</li>
 *   <li>刷新失败 ⇒ 用**上一次成功的内存快照**（{@link #lastGoodByKid}）＋ WARN；</li>
 *   <li>仍无 ⇒ 静态公钥兜底（冷启动 + 墨枢不可达时的最后一道）。</li>
 * </ol>
 *
 * <p>🔴 <b>启动不依赖墨枢在线</b>：构造只建空缓存，首次验签才拉（或由调用方显式 {@link #refresh()} 预取）。
 * 拉取失败不抛、不影响启动——这是与"硬依赖远端"的关键差别（子系统不能因主系统抖一下起不来）。</p>
 *
 * <p>🔴 选钥按票头 {@code kid}（=公钥指纹，与 JWKS 里的 kid 同一算法）：单钥形态下无 kid 也能用
 * （{@link #resolveAny()}），多钥并存时（未来平滑轮换）才真正需要区分。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
@Slf4j
public class JwksKeyProvider {

    /** JWKS 端点路径（与 OAuth2IdpController 的 @RequestMapping 一致） */
    private static final String JWKS_PATH = "/web/oauth2/jwks.json";

    /** 默认缓存 TTL：1 小时（换钥后最迟一小时内自动跟上；也可由宿主调 refresh 立即生效） */
    public static final long DEFAULT_TTL_MILLIS = 3_600_000L;

    private final String serverBaseUrl;
    private final String staticPublicKeyBase64;
    private final long ttlMillis;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** kid → X.509 SPKI base64（当前缓存） */
    private volatile Map<String, String> keysByKid = Collections.emptyMap();
    /** 上次成功拉取时刻（毫秒）；0＝从未成功 */
    private volatile long fetchedAtMillis = 0L;
    /** 上一次成功快照（刷新失败时回落到它；与 keysByKid 通常同值，分开存是为了刷新失败的时序清晰） */
    private volatile Map<String, String> lastGoodByKid = Collections.emptyMap();
    /** 刷新单飞闸 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public JwksKeyProvider(String serverBaseUrl, String staticPublicKeyBase64) {
        this(serverBaseUrl, staticPublicKeyBase64, DEFAULT_TTL_MILLIS, new RestTemplate());
    }

    /**
     * @param serverBaseUrl         墨枢根地址（如 {@code http://10.3.6.60/inkhub}）；空＝不拉 JWKS
     * @param staticPublicKeyBase64 静态兜底公钥（可空）
     * @param ttlMillis             缓存 TTL（毫秒，测试可注入短值）
     * @param restTemplate          拉取用（测试可注入 Mock）
     */
    public JwksKeyProvider(String serverBaseUrl, String staticPublicKeyBase64,
                           long ttlMillis, RestTemplate restTemplate) {
        this.serverBaseUrl = StringUtils.trimToEmpty(serverBaseUrl);
        this.staticPublicKeyBase64 = StringUtils.trimToEmpty(staticPublicKeyBase64);
        this.ttlMillis = ttlMillis <= 0 ? DEFAULT_TTL_MILLIS : ttlMillis;
        this.restTemplate = restTemplate == null ? new RestTemplate() : restTemplate;
    }

    /** 该 provider 是否可用＝有远端地址或有静态兜底（都无 ⇒ 任何票都拒） */
    public boolean enabled() {
        return StringUtils.isNotBlank(serverBaseUrl) || StringUtils.isNotBlank(staticPublicKeyBase64);
    }

    /** 是否配置了远端 JWKS（false＝纯静态形态） */
    public boolean remoteEnabled() {
        return StringUtils.isNotBlank(serverBaseUrl);
    }

    /**
     * 按 kid 取公钥（X.509 SPKI base64）。
     *
     * @param kid 票头 kid；可空（空则退化为"只有一把就用它"）
     * @return 公钥 base64；无可用钥 ⇒ null（调用方 fail-closed）
     */
    public String resolve(String kid) {
        String normalizedKid = StringUtils.trimToNull(kid);
        if (normalizedKid != null && fresh() && keysByKid.containsKey(normalizedKid)) {
            return keysByKid.get(normalizedKid);
        }
        if (!remoteEnabled()) {
            return staticKey();
        }
        if (!fresh()) {
            refresh();
        }
        if (normalizedKid != null && keysByKid.containsKey(normalizedKid)) {
            return keysByKid.get(normalizedKid);
        }
        if (normalizedKid != null && lastGoodByKid.containsKey(normalizedKid)) {
            return lastGoodByKid.get(normalizedKid);
        }
        if (normalizedKid == null) {
            return resolveAnyAfterLoaded();
        }
        log.warn("[JWKS]未找到 kid={} 对应的公钥（缓存 {} 把，快照 {} 把）",
                normalizedKid, keysByKid.size(), lastGoodByKid.size());
        return staticKey();
    }

    /** 票头无 kid 时的取钥：单钥形态直接用它；多钥且无法判定 ⇒ null（不猜） */
    public String resolveAny() {
        if (!remoteEnabled()) {
            return staticKey();
        }
        if (!fresh()) {
            refresh();
        }
        return resolveAnyAfterLoaded();
    }

    private String resolveAnyAfterLoaded() {
        if (keysByKid.size() == 1) {
            return keysByKid.values().iterator().next();
        }
        if (keysByKid.isEmpty() && lastGoodByKid.size() == 1) {
            return lastGoodByKid.values().iterator().next();
        }
        return keysByKid.isEmpty() && lastGoodByKid.isEmpty() ? staticKey() : null;
    }

    /**
     * 拉一次 JWKS 并刷新缓存（单飞；失败只 WARN 并沿用旧快照，不抛）。
     * 宿主可在换钥后显式调用以立即生效（否则等 TTL）。
     */
    public void refresh() {
        if (!remoteEnabled()) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            // 已有线程在拉：不排队阻塞，直接用当前缓存（调用方会走快照/静态兜底）
            return;
        }
        try {
            String url = trimTrailingSlash(serverBaseUrl) + JWKS_PATH;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response == null || !response.getStatusCode().is2xxSuccessful()
                    || StringUtils.isBlank(response.getBody())) {
                log.warn("[JWKS]拉取无有效响应（status={}），沿用旧快照",
                        response == null ? "null" : response.getStatusCode());
                return;
            }
            Map<String, String> parsed = parseJwks(response.getBody());
            if (parsed.isEmpty()) {
                log.warn("[JWKS]响应中无可用 RSA 公钥（keys 为空或字段缺失），沿用旧快照");
                return;
            }
            keysByKid = parsed;
            lastGoodByKid = parsed;
            fetchedAtMillis = System.currentTimeMillis();
            log.info("[JWKS]公钥已刷新：{} 把（kid={}）", parsed.size(), parsed.keySet());
        } catch (Throwable t) {
            // 不打内容（URL 可含内网地址）；类名足够定位
            log.warn("[JWKS]拉取失败，沿用旧快照: {}", t.getClass().getSimpleName());
        } finally {
            refreshing.set(false);
        }
    }

    /** 清缓存（测试/显式失效用） */
    public void invalidate() {
        keysByKid = Collections.emptyMap();
        lastGoodByKid = Collections.emptyMap();
        fetchedAtMillis = 0L;
    }

    public boolean fresh() {
        return fetchedAtMillis > 0 && (System.currentTimeMillis() - fetchedAtMillis) < ttlMillis;
    }

    private String staticKey() {
        return StringUtils.isBlank(staticPublicKeyBase64) ? null : staticPublicKeyBase64;
    }

    /** JWKS → kid→公钥base64。只认 kty=RSA 且 n/e/kid 齐的项；单条坏不影响其余 */
    Map<String, String> parseJwks(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode keys = root == null ? null : root.get("keys");
        if (keys == null || !keys.isArray()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode key : keys) {
            String kty = key.path("kty").asText("");
            String kid = key.path("kid").asText("");
            String n = key.path("n").asText("");
            String e = key.path("e").asText("");
            if (!"RSA".equals(kty) || StringUtils.isAnyBlank(kid, n, e)) {
                continue;
            }
            try {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(n)),
                        new BigInteger(1, Base64.getUrlDecoder().decode(e)));
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
                out.put(kid, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            } catch (Exception ignore) {
                log.warn("[JWKS]单条公钥不可解析，跳过：kid={}", kid);
            }
        }
        return out;
    }

    private static String trimTrailingSlash(String url) {
        String value = StringUtils.trimToEmpty(url);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** 供验签器统一取钥：先按 kid，再按"唯一一把"，最后静态兜底 */
    String resolveForToken(String token) {
        String kid = IdpJwtUtil.readKid(token);
        return kid == null ? resolveAny() : resolve(kid);
    }
}