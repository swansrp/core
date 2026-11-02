package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: IdpBearerVerifierTest
 * Description: JWT 解析腿同源三断言（T2.3c 棒1）——与 api {@link IdpJwtUtil} 的同源性：
 * <ol>
 *   <li>测试钥对签的票验过（claims 原样产出，Bearer 前缀兼容）；</li>
 *   <li>篡改 payload 拒（签名面失配 ⇒ null）；</li>
 *   <li>未配置公钥时该腿禁用（enabled=false ⇒ verify 恒拒）。</li>
 * </ol>
 * 纯 JUnit4，不起 Spring——测试钥对现场生成（RSA 2048），签发走同一 IdpJwtUtil。
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public class IdpBearerVerifierTest {

    private static String privateKeyBase64;
    private static String publicKeyBase64;
    private static String validToken;

    @BeforeClass
    public static void generateKeyPairAndSign() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        Map<String, Object> claims = new LinkedHashMap<>(4);
        claims.put(IdpJwtUtil.CLAIM_ISS, "inkhub");
        claims.put(IdpJwtUtil.CLAIM_SUB, "000001");
        claims.put(IdpJwtUtil.CLAIM_UID, 1);
        claims.put(IdpJwtUtil.CLAIM_IAT, System.currentTimeMillis() / 1000L);
        claims.put(IdpJwtUtil.CLAIM_EXP, System.currentTimeMillis() / 1000L + 3600L);
        validToken = IdpJwtUtil.sign(privateKeyBase64, IdpJwtUtil.computeKid(publicKeyBase64), claims);
    }

    /** 断言①：同源签发（IdpJwtUtil.sign）的票，经 Bearer 前缀形态验过并产出 claims */
    @Test
    public void 同源签发的票验过并产出claims() {
        IdpBearerVerifier verifier = new IdpBearerVerifier(publicKeyBase64);
        assertTrue("公钥已配置 ⇒ 该腿启用", verifier.enabled());
        Map<String, Object> claims = verifier.verifyBearer("Bearer " + validToken);
        assertNotNull("测试钥对签的票必须验过", claims);
        assertEquals("claims 原样产出（sub）", "000001", String.valueOf(claims.get(IdpJwtUtil.CLAIM_SUB)));
        assertEquals("claims 原样产出（uid）", "1", String.valueOf(claims.get(IdpJwtUtil.CLAIM_UID)));
        assertNotNull("裸 token（无 Bearer 前缀）同口径验过", verifier.verifyBearer(validToken));
    }

    /** 断言②：篡改 payload（换 uid claim 重编码）⇒ 签名面失配拒（null） */
    @Test
    public void 篡改payload拒() {
        IdpBearerVerifier verifier = new IdpBearerVerifier(publicKeyBase64);
        String tampered = tamperUidClaim(validToken);
        assertNull("篡改 payload 后签名必然失配 ⇒ 拒", verifier.verifyBearer("Bearer " + tampered));
        assertNull("篡改后的裸 token 同样拒", verifier.verifyBearer(tampered));
    }

    /** 断言③：未配置公钥（空串/null）⇒ 该腿禁用，verify 恒拒（fail-closed） */
    @Test
    public void 未配置公钥时禁用且恒拒() {
        IdpBearerVerifier blankKey = new IdpBearerVerifier("");
        assertFalse("公钥空串 ⇒ 禁用", blankKey.enabled());
        assertNull("禁用腿对合法票也恒拒", blankKey.verifyBearer(validToken));
        IdpBearerVerifier nullProps = new IdpBearerVerifier((IdpBearerProperties) null);
        assertFalse("Properties 缺席 ⇒ 禁用", nullProps.enabled());
        assertNull(nullProps.verifyBearer(validToken));
    }

    /** 换掉 payload 里的 uid claim 值后重编码（签名仍是对原 payload 的 ⇒ 必失配） */
    private static String tamperUidClaim(String token) {
        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // 兼容序列化空白形态（"uid":1 / "uid": 1）——换值后签名面对不上原 payload
        String tamperedJson = payloadJson.replaceFirst("\"uid\"\\s*:\\s*1", "\"uid\":999");
        if (tamperedJson.equals(payloadJson)) {
            throw new IllegalStateException("测试前提不成立：payload 未发生篡改");
        }
        return parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }
}
