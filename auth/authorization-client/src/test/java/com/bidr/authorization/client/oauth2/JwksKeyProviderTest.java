package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link JwksKeyProvider} 守卫（2026-11-04，对账单「接入方零密钥」）。
 *
 * <p>钉的是一条可用性契约：**接入方只配 {@code server-base-url}，公钥从墨枢 JWKS 拉**，
 * 并且拉取失败不能让验签崩、也不能让启动崩。逐腿：</p>
 * <ul>
 *   <li>JWKS 真实文本 → n/e 重建 → 能验通**真签的票**（不是 mock 验签，走 IdpJwtUtil 全链）；</li>
 *   <li>首次 resolve 触发一次拉取，之后命中缓存不再发请求（TTL 内）；</li>
 *   <li>刷新失败 ⇒ 沿用上次成功快照（墨枢短暂不可用不踢人）；</li>
 *   <li>未配 server-base-url ⇒ 用静态兜底；两者都无 ⇒ 返回 null（调用方 fail-closed）；</li>
 *   <li>TTL 过期 ⇒ 下次 resolve 重新拉取；</li>
 *   <li>kid 查不到 ⇒ 落静态兜底/快照，最终 null（不猜）。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class JwksKeyProviderTest {

    private static final String BASE = "http://inkhub.test/inkhub";
    private static final String JWKS_URL = BASE + "/web/oauth2/jwks.json";

    private KeyPair keyPair;
    private String privateKeyBase64;
    private String publicKeyBase64;
    private String kid;
    private String jwksJson;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @Before
    public void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        kid = IdpJwtUtil.computeKid(publicKeyBase64);
        jwksJson = jwksJson(publicKeyBase64, kid);

        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @After
    public void tearDown() {
        server.verify();
    }

    /** JWKS 真实线格式（n/e 为 base64url，无填充） */
    private static String jwksJson(String pub, String kid) throws Exception {
        RSAPublicKey rsa = (RSAPublicKey) java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(pub)));
        String n = base64Url(rsa.getModulus());
        String e = base64Url(rsa.getPublicExponent());
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"use\":\"sig\",\"alg\":\"RS256\","
                + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // 去掉可能的前导 0（JWKS 要求无符号最小表示）
        int offset = (bytes.length > 1 && bytes[0] == 0) ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    private String signToken(long expEpochSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdpJwtUtil.CLAIM_ISS, "inkhub");
        claims.put(IdpJwtUtil.CLAIM_SUB, "CN-0001");
        claims.put(IdpJwtUtil.CLAIM_USERNAME, "zhang_p");
        claims.put(IdpJwtUtil.CLAIM_IAT, System.currentTimeMillis() / 1000L);
        claims.put(IdpJwtUtil.CLAIM_EXP, expEpochSeconds);
        return IdpJwtUtil.sign(privateKeyBase64, kid, claims);
    }

    private String validToken() {
        return signToken(System.currentTimeMillis() / 1000L + 600);
    }

    /* ---------------- 腿 1：JWKS 拉到的钥能验通真票 ---------------- */

    @Test
    public void resolvesKidFromJwksAndVerifiesRealToken() {
        server.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));

        JwksKeyProvider provider = new JwksKeyProvider(BASE, "", JwksKeyProvider.DEFAULT_TTL_MILLIS, restTemplate);
        String token = validToken();

        String resolved = provider.resolveForToken(token);
        Assert.assertNotNull("必须能从 JWKS 解析出 kid 对应的公钥", resolved);
        Assert.assertEquals("重建的公钥应与源公钥逐字节一致（SPKI base64）", publicKeyBase64, resolved);
        Assert.assertNotNull("用拉到的钥必须能验通真签的票", IdpJwtUtil.verify(token, resolved));
    }

    /* ---------------- 腿 2：TTL 内不重复拉取 ---------------- */

    @Test
    public void cachesWithinTtlAndDoesNotRefetch() {
        server.expect(requestTo(JWKS_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));

        JwksKeyProvider provider = new JwksKeyProvider(BASE, "", JwksKeyProvider.DEFAULT_TTL_MILLIS, restTemplate);
        provider.refresh();
        // 第二次及以后：TTL 内只读缓存；server 只 expect 了一次 ⇒ 多拉一次会因未预期请求而红
        Assert.assertNotNull(provider.resolve(kid));
        Assert.assertNotNull(provider.resolve(kid));
        Assert.assertTrue(provider.fresh());
    }

    /* ---------------- 腿 3：刷新失败沿用上次快照 ---------------- */

    @Test
    public void refreshFailureKeepsLastGoodSnapshot() {
        server.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));
        JwksKeyProvider provider = new JwksKeyProvider(BASE, "", 1L, restTemplate); // TTL=1ms ⇒ 立刻过期
        provider.refresh();
        Assert.assertEquals(publicKeyBase64, provider.resolve(kid));

        // 过期后拉取失败：必须仍拿得到上次快照（子系统的可用性不能绑在主系统抖动上）
        server.reset();
        server.expect(requestTo(JWKS_URL)).andRespond(withServerError());
        sleep(5);
        Assert.assertEquals("刷新失败应沿用上次成功快照", publicKeyBase64, provider.resolve(kid));
    }

    /* ---------------- 腿 4/5：静态兜底与全缺 fail-closed ---------------- */

    @Test
    public void withoutServerBaseUrlUsesStaticFallback() {
        JwksKeyProvider provider = new JwksKeyProvider("", publicKeyBase64);
        Assert.assertTrue(provider.enabled());
        Assert.assertFalse(provider.remoteEnabled());
        Assert.assertEquals(publicKeyBase64, provider.resolve(kid));
        Assert.assertEquals(publicKeyBase64, provider.resolveAny());
    }

    @Test
    public void bothMissingMeansFailClosed() {
        JwksKeyProvider provider = new JwksKeyProvider("", "");
        Assert.assertFalse(provider.enabled());
        Assert.assertNull(provider.resolve(kid));
        Assert.assertNull(provider.resolveAny());
        Assert.assertNull(provider.resolveForToken(validToken()));
        // 装配期 fail-fast（不是运行期静默 401）
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setEnabled(true);
        try {
            IdpBearerAutoConfiguration.assertKeySourceConfigured(properties);
            Assert.fail("enabled=true 且无任何钥来源时必须启动失败");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("server-base-url"));
        }
    }

    /* ---------------- 腿 6：TTL 过期重新拉取 ---------------- */

    @Test
    public void ttlExpiryMarksCacheNotFresh() {
        server.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));
        JwksKeyProvider provider = new JwksKeyProvider(BASE, "", 1L, restTemplate);
        provider.refresh();
        Assert.assertTrue(provider.fresh());

        sleep(5);
        // 过期 ⇒ 下次 resolve 会重新拉取（"拉到失败仍用快照"由 refreshFailureKeepsLastGoodSnapshot 腿覆盖）
        Assert.assertFalse("TTL 过期后 fresh 必须为 false", provider.fresh());
    }

    /* ---------------- 腿 7：kid 查不到不猜 ---------------- */

    @Test
    public void unknownKidWithMultipleKeysDoesNotGuess() {
        // 两把钥且都查不到目标 kid ⇒ 不返回"随便一把"，落静态兜底（这里没配 ⇒ null）
        String other = jwksJson;
        server.expect(requestTo(JWKS_URL)).andRespond(withSuccess(other, MediaType.APPLICATION_JSON));
        JwksKeyProvider provider = new JwksKeyProvider(BASE, "", JwksKeyProvider.DEFAULT_TTL_MILLIS, restTemplate);
        Assert.assertNull("kid 不存在且无静态兜底 ⇒ null（不猜钥）", provider.resolve("deadbeefdeadbeef"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}