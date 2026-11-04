package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: IdpTokenVerifierTest
 * Description: IdpTokenVerifier 离线单测（T3.1 棒0）：用 api {@link IdpJwtUtil#sign} 现场造票，
 * 覆盖正票／错钥／过期／缺 username／iss 不符五条腿，外加"iss 缺席容忍"与"公钥未配恒拒"两条边界。
 *
 * <p>密钥对在测试内现场生成（RSA 2048，无固定私钥常量）：造票与验票同源，
 * 错钥腿用第二对钥签名验证签名面真的在判。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
public class IdpTokenVerifierTest {

    private static final String KID = "kid-test";

    private static String publicKeyBase64;
    private static String privateKeyBase64;
    private static String otherPublicKeyBase64;
    private static String otherPrivateKeyBase64;

    @BeforeClass
    public static void generateKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        KeyPair otherKeyPair = generator.generateKeyPair();
        otherPrivateKeyBase64 = Base64.getEncoder().encodeToString(otherKeyPair.getPrivate().getEncoded());
        otherPublicKeyBase64 = Base64.getEncoder().encodeToString(otherKeyPair.getPublic().getEncoded());
    }

    @Test
    public void acceptsValidIdpToken() {
        IdpTokenVerifier verifier = new IdpTokenVerifier(publicKeyBase64);

        IdpPrincipal principal = verifier.verify(sign(privateKeyBase64, validClaims()));

        Assert.assertNotNull("同钥对签发的正票必须通过", principal);
        Assert.assertEquals("zhangsan", principal.getUsername());
        Assert.assertEquals("E1001", principal.getCustomerNumber());
        Assert.assertEquals(Long.valueOf(7L), principal.getUid());
        Assert.assertEquals("张三", principal.getName());
    }

    @Test
    public void rejectsTokenSignedByAnotherKey() {
        IdpTokenVerifier verifier = new IdpTokenVerifier(publicKeyBase64);

        Assert.assertNull("错钥签的票必须拒（签名面真的在判）",
                verifier.verify(sign(otherPrivateKeyBase64, validClaims())));
        Assert.assertNull("验错钥本身也不得误放行", new IdpTokenVerifier(otherPublicKeyBase64)
                .verify(sign(privateKeyBase64, validClaims())));
    }

    @Test
    public void rejectsExpiredToken() {
        Map<String, Object> claims = validClaims();
        long now = System.currentTimeMillis() / 1000L;
        claims.put(IdpJwtUtil.CLAIM_IAT, now - 7200L);
        claims.put(IdpJwtUtil.CLAIM_EXP, now - 3600L);

        Assert.assertNull("过期票必须拒（IdpJwtUtil 自判 exp 含 30 秒容忍）",
                new IdpTokenVerifier(publicKeyBase64).verify(sign(privateKeyBase64, claims)));
    }

    @Test
    public void rejectsTokenWithoutUsername() {
        Map<String, Object> claims = validClaims();
        claims.remove(IdpJwtUtil.CLAIM_USERNAME);

        Assert.assertNull("username 缺失＝无法对齐本地账号，必须拒",
                new IdpTokenVerifier(publicKeyBase64).verify(sign(privateKeyBase64, claims)));
    }

    @Test
    public void rejectsTokenWithForeignIssuer() {
        Map<String, Object> claims = validClaims();
        claims.put(IdpJwtUtil.CLAIM_ISS, "another-idp");

        Assert.assertNull("iss 在场且不等于 inkhub 必须拒",
                new IdpTokenVerifier(publicKeyBase64).verify(sign(privateKeyBase64, claims)));
    }

    @Test
    public void acceptsTokenWithAbsentIssuerAndStringUid() {
        Map<String, Object> claims = validClaims();
        claims.remove(IdpJwtUtil.CLAIM_ISS);
        claims.put(IdpJwtUtil.CLAIM_UID, "42");

        IdpPrincipal principal = new IdpTokenVerifier(publicKeyBase64).verify(sign(privateKeyBase64, claims));

        Assert.assertNotNull("iss 缺席容忍老签发器（在场才判）", principal);
        Assert.assertEquals("uid 容 String 形态", Long.valueOf(42L), principal.getUid());
    }

    @Test
    public void rejectsWhenPublicKeyMissingOrTokenBlank() {
        IdpTokenVerifier noKey = new IdpTokenVerifier("");
        IdpTokenVerifier verifier = new IdpTokenVerifier(publicKeyBase64);

        Assert.assertNull("公钥未配置＝恒拒（fail-closed）", noKey.verify(sign(privateKeyBase64, validClaims())));
        Assert.assertNull("空票恒拒", verifier.verify(null));
        Assert.assertNull("空串票恒拒", verifier.verify("  "));
        Assert.assertNull("脏串恒拒不抛", verifier.verify("not.a.jwt"));
    }

    @Test
    public void propertiesConstructorUsesConfiguredPublicKey() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setPublicKeyBase64(publicKeyBase64);

        IdpPrincipal principal = new IdpTokenVerifier(properties).verify(sign(privateKeyBase64, validClaims()));

        Assert.assertNotNull("properties 构造形态必须取同一把公钥", principal);
        Assert.assertEquals("E1001", principal.getCustomerNumber());
    }

    @Test
    public void enforcesAudienceWhenAppIdConfigured() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setPublicKeyBase64(publicKeyBase64);
        properties.setAppId("epc-ai");
        IdpTokenVerifier verifier = new IdpTokenVerifier(properties);

        Map<String, Object> forSelf = validClaims();
        forSelf.put(IdpJwtUtil.CLAIM_APP_ID, "epc-ai");
        Assert.assertNotNull("app_id 与期望接入方一致⇒放行",
                verifier.verify(sign(privateKeyBase64, forSelf)));

        Map<String, Object> forOther = validClaims();
        forOther.put(IdpJwtUtil.CLAIM_APP_ID, "another-subsystem");
        Assert.assertNull("签给其它子系统的票必须拒（跨子系统票混淆）",
                verifier.verify(sign(privateKeyBase64, forOther)));

        Map<String, Object> noAudience = validClaims();
        noAudience.remove(IdpJwtUtil.CLAIM_APP_ID);
        Assert.assertNull("配置了期望接入方而票面无 app_id⇒拒（没有受众≠谁都收）",
                verifier.verify(sign(privateKeyBase64, noAudience)));
    }

    @Test
    public void withoutExpectedAppIdKeepsLegacyBehavior() {
        Map<String, Object> legacy = validClaims();
        legacy.remove(IdpJwtUtil.CLAIM_APP_ID);

        Assert.assertNotNull("未配置期望接入方＝兼容老票（票面无 app_id 也放行）",
                new IdpTokenVerifier(publicKeyBase64).verify(sign(privateKeyBase64, legacy)));
    }

    private static Map<String, Object> validClaims() {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(IdpJwtUtil.CLAIM_ISS, IdpTokenVerifier.EXPECTED_ISS);
        claims.put(IdpJwtUtil.CLAIM_SUB, "E1001");
        claims.put(IdpJwtUtil.CLAIM_UID, 7);
        claims.put(IdpJwtUtil.CLAIM_USERNAME, "zhangsan");
        claims.put(IdpJwtUtil.CLAIM_NAME, "张三");
        claims.put(IdpJwtUtil.CLAIM_IAT, now);
        claims.put(IdpJwtUtil.CLAIM_EXP, now + 3600L);
        return claims;
    }

    private static String sign(String privateKey, Map<String, Object> claims) {
        return IdpJwtUtil.sign(privateKey, KID, claims);
    }
}
