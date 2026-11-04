package com.bidr.authorization.utils.jwt;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: IdpJwtUtilTest
 * Description: IdP RS256 JWT 签发/验签往返矩阵（T1.2 棒 6，JUnit4 随 authorization 主流引擎）：
 * 全 claim 往返、篡改拒绝、过期拒绝、kid 确定性、私钥派生公钥可用。密钥用例内现生成（RSA-2048，
 * 同款做法，不依赖部署钥匙）。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class IdpJwtUtilTest {

    private static String privateKeyBase64;
    private static String publicKeyBase64;

    @BeforeClass
    public static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    /** 任务书定稿的 9-claim 形态 */
    private Map<String, Object> claims(long iat, long exp) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(IdpJwtUtil.CLAIM_ISS, "inkhub");
        claims.put(IdpJwtUtil.CLAIM_SUB, "000001");
        claims.put(IdpJwtUtil.CLAIM_UID, 42L);
        claims.put(IdpJwtUtil.CLAIM_USERNAME, "zhangsan");
        claims.put(IdpJwtUtil.CLAIM_NAME, "张三");
        claims.put(IdpJwtUtil.CLAIM_TENANT_IDS, Arrays.<Object>asList(5L, 7L));
        claims.put(IdpJwtUtil.CLAIM_APP_ID, "demo-app");
        claims.put(IdpJwtUtil.CLAIM_IAT, iat);
        claims.put(IdpJwtUtil.CLAIM_EXP, exp);
        return claims;
    }

    @Test
    public void signThenVerify_fullClaimsRoundTrip() {
        long now = System.currentTimeMillis() / 1000L;
        String token = IdpJwtUtil.sign(privateKeyBase64, IdpJwtUtil.computeKid(publicKeyBase64),
                claims(now, now + 3600));
        Map<String, Object> verified = IdpJwtUtil.verify(token, publicKeyBase64);
        Assert.assertNotNull("全 claim 合法票必须验过", verified);
        Assert.assertEquals("inkhub", verified.get(IdpJwtUtil.CLAIM_ISS));
        Assert.assertEquals("000001", verified.get(IdpJwtUtil.CLAIM_SUB));
        Assert.assertEquals(42L, ((Number) verified.get(IdpJwtUtil.CLAIM_UID)).longValue());
        Assert.assertEquals("zhangsan", verified.get(IdpJwtUtil.CLAIM_USERNAME));
        Assert.assertEquals("张三", verified.get(IdpJwtUtil.CLAIM_NAME));
        Assert.assertEquals("demo-app", verified.get(IdpJwtUtil.CLAIM_APP_ID));
        // 🔴 数值逐位比较：JWT 载荷经 JSON 往返，数字 claim 反序列化可能绑成 Integer（打印同形、类型异类），
        // List 整体 equals 会因 Integer.equals(Long)=false 假红——消费侧 OAuth2BearerToken.parseTenantIds
        // 已按 Number 收口，测试断言同口径
        List<?> tenantIds = (List<?>) verified.get(IdpJwtUtil.CLAIM_TENANT_IDS);
        Assert.assertEquals(2, tenantIds.size());
        Assert.assertEquals(5L, ((Number) tenantIds.get(0)).longValue());
        Assert.assertEquals(7L, ((Number) tenantIds.get(1)).longValue());
        Assert.assertEquals(((Number) verified.get(IdpJwtUtil.CLAIM_IAT)).longValue()
                        + 3600L,
                ((Number) verified.get(IdpJwtUtil.CLAIM_EXP)).longValue());
    }

    @Test
    public void verify_tamperedPayload_rejected() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String token = IdpJwtUtil.sign(privateKeyBase64, null, claims(now, now + 3600));
        String[] parts = token.split("\\.");
        // 篡改载荷：换成一个合法 base64url JSON（等长不同内容）
        byte[] forged = "{\"sub\":\"hacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forged) + "." + parts[2];
        Assert.assertNull("载荷篡改必须拒", IdpJwtUtil.verify(tampered, publicKeyBase64));

        // 篡改签名段：字节级翻转末字节再重编码——🔴 不能只翻末位 base64 字符，
        // 256 字节签名的 base64 末字符只携带 2 个有效位（4 个填充位），翻填充位解码出的字节不变、验签照样过
        byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);
        sigBytes[sigBytes.length - 1] = (byte) (sigBytes[sigBytes.length - 1] ^ 0x2A);
        String tamperedSig = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        Assert.assertNull("签名篡改必须拒", IdpJwtUtil.verify(tamperedSig, publicKeyBase64));
    }

    @Test
    public void verify_expired_rejectedAndLeewayHonored() {
        long now = System.currentTimeMillis() / 1000L;
        // 已过期 120 秒（leeway 30 秒兜不住）
        String expired = IdpJwtUtil.sign(privateKeyBase64, null, claims(now - 3720, now - 120));
        Assert.assertNull("过期票必须拒（含 30 秒容忍口径）",
                IdpJwtUtil.verify(expired, publicKeyBase64, now, 30L));
        // 仅过期 10 秒（30 秒容忍内放行——跨机 NTP 漂移不得把刚签出的票判死）
        String nearlyExpired = IdpJwtUtil.sign(privateKeyBase64, null, claims(now - 3610, now - 10));
        Assert.assertNotNull("容忍窗口内的票不得拒",
                IdpJwtUtil.verify(nearlyExpired, publicKeyBase64, now, 30L));
        // exp 缺失同样拒（M-4 同款：JDK 验签不判 exp）
        Map<String, Object> noExp = claims(now, now + 3600);
        noExp.remove(IdpJwtUtil.CLAIM_EXP);
        String noExpToken = IdpJwtUtil.sign(privateKeyBase64, null, noExp);
        Assert.assertNull("exp 缺失必须拒", IdpJwtUtil.verify(noExpToken, publicKeyBase64));
    }

    @Test
    public void computeKid_deterministicHex16() {
        String kid1 = IdpJwtUtil.computeKid(publicKeyBase64);
        String kid2 = IdpJwtUtil.computeKid(publicKeyBase64);
        Assert.assertEquals("同一公钥 kid 必须确定", kid1, kid2);
        Assert.assertEquals("kid＝SHA-256 前 8 字节 hex＝16 字符", 16, kid1.length());
        Assert.assertTrue("kid 必须全 hex", kid1.matches("[0-9a-f]{16}"));
    }

    @Test
    public void derivedPublicKeyFromPrivate_worksForVerify() throws Exception {
        String derived = IdpJwtUtil.derivePublicKeyBase64(privateKeyBase64);
        Assert.assertNotNull(derived);
        long now = System.currentTimeMillis() / 1000L;
        String token = IdpJwtUtil.sign(privateKeyBase64, null, claims(now, now + 3600));
        Assert.assertNotNull("派生公钥必须能验过私钥签的票", IdpJwtUtil.verify(token, derived));
        Assert.assertEquals("派生公钥与显式公钥同 kid", IdpJwtUtil.computeKid(publicKeyBase64),
                IdpJwtUtil.computeKid(derived));
    }
}
