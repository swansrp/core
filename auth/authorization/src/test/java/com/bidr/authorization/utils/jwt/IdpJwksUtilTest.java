package com.bidr.authorization.utils.jwt;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Title: IdpJwksUtilTest
 * Description: JWKS 构建可解析且 kid 匹配（T1.2 棒 6 任务书钉的 1 条）：JWK 六字段形态、
 * n/e 反解回真实模数/指数、kid 与 IdpJwtUtil.computeKid 同一真相、坏公钥拒绝。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class IdpJwksUtilTest {

    private static String publicKeyBase64;
    private static RSAPublicKey publicKey;

    @BeforeClass
    public static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        publicKey = (RSAPublicKey) pair.getPublic();
    }

    @Test
    public void jwks_parseableAndKidMatches() {
        String kid = IdpJwtUtil.computeKid(publicKeyBase64);
        Map<String, Object> jwks = IdpJwksUtil.buildJwks(publicKeyBase64, kid);
        Assert.assertNotNull(jwks);
        Object keysObj = jwks.get("keys");
        Assert.assertTrue(keysObj instanceof List);
        List<?> keys = (List<?>) keysObj;
        Assert.assertEquals("单钥对＝keys 恒一条", 1, keys.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = (Map<String, Object>) keys.get(0);
        Assert.assertEquals("RSA", jwk.get("kty"));
        Assert.assertEquals("sig", jwk.get("use"));
        Assert.assertEquals("RS256", jwk.get("alg"));
        Assert.assertEquals("kid 与票头同一真相", kid, jwk.get("kid"));

        // n/e 反解：base64url 无填充 → 无符号大端 → 与真实公钥参数一致
        byte[] nBytes = Base64.getUrlDecoder().decode(String.valueOf(jwk.get("n")));
        Assert.assertArrayEquals("n 必须等于公钥模数（无符号位）", publicKey.getModulus().toByteArray(),
                new BigInteger(1, nBytes).toByteArray());
        byte[] eBytes = Base64.getUrlDecoder().decode(String.valueOf(jwk.get("e")));
        Assert.assertEquals("e 必须等于公钥指数", publicKey.getPublicExponent(),
                new BigInteger(1, eBytes));
    }

    @Test
    public void jwks_badPublicKey_rejected() {
        try {
            IdpJwksUtil.buildJwks("not-a-valid-key", "kid");
            Assert.fail("坏公钥必须抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("公钥解析失败"));
        }
    }
}
