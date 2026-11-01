package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: OAuth2BearerTokenTest
 * Description: open-api Bearer 鉴权腿判定矩阵（T1.2 棒 6）：valid 票构造
 * AccountContext＋TenantContext、expired 拒、tamper 拒、无 Authorization 头静默放行
 * （双轨语义——X-App-Key 轨继续）、非 Bearer 形态拒。线程态上下文 @After 清理。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class OAuth2BearerTokenTest {

    private static String privateKeyBase64;
    private static String publicKeyBase64;

    private OAuth2BearerToken bearer;

    @BeforeClass
    public static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    @Before
    public void setUp() {
        IdentityIdpProperties properties = new IdentityIdpProperties();
        properties.setPrivateKey(privateKeyBase64);
        properties.setPublicKey(publicKeyBase64);
        bearer = new OAuth2BearerToken(properties);
    }

    @After
    public void tearDown() {
        AccountContext.remove();
        TenantContext.remove();
    }

    /** 任务书 claim 形态的票（exp 默认一小时） */
    private String token(long exp) {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(IdpJwtUtil.CLAIM_ISS, "inkhub");
        claims.put(IdpJwtUtil.CLAIM_SUB, "000001");
        claims.put(IdpJwtUtil.CLAIM_UID, 42L);
        claims.put(IdpJwtUtil.CLAIM_USERNAME, "zhangsan");
        claims.put(IdpJwtUtil.CLAIM_NAME, "张三");
        claims.put(IdpJwtUtil.CLAIM_TENANT_IDS, Arrays.<Object>asList(5L, 7L));
        claims.put(IdpJwtUtil.CLAIM_APP_ID, "demo-app");
        claims.put(IdpJwtUtil.CLAIM_IAT, now);
        claims.put(IdpJwtUtil.CLAIM_EXP, exp);
        return IdpJwtUtil.sign(privateKeyBase64, IdpJwtUtil.computeKid(publicKeyBase64), claims);
    }

    @Test
    public void validate_validToken_buildsContextAndTenantGate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(System.currentTimeMillis() / 1000L + 3600));
        bearer.validate(request);
        AccountInfo account = AccountContext.get();
        Assert.assertNotNull("合法票必须构造 AccountContext", account);
        Assert.assertEquals(Long.valueOf(42L), account.getUserId());
        Assert.assertEquals("000001", account.getCustomerNumber());
        Assert.assertEquals("zhangsan", account.getUserName());
        Assert.assertEquals("张三", account.getName());
        Assert.assertEquals("数据面租户闸依赖 TenantContext 就位", Arrays.asList(5L, 7L),
                TenantContext.getTenantIds());
    }

    @Test
    public void validate_expiredToken_rejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 已过期 120 秒（超出 30 秒容忍）
        request.addHeader("Authorization", "Bearer "
                + token(System.currentTimeMillis() / 1000L - 120));
        try {
            bearer.validate(request);
            Assert.fail("过期票必须拒");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("access_token 无效或已过期"));
        }
        Assert.assertNull("拒绝后不得残留上下文", AccountContext.get());
    }

    @Test
    public void validate_tamperedToken_rejected() throws Exception {
        String[] parts = token(System.currentTimeMillis() / 1000L + 3600).split("\\.");
        byte[] forged = "{\"sub\":\"hacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forged) + "." + parts[2];
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tampered);
        try {
            bearer.validate(request);
            Assert.fail("篡改票必须拒");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("access_token 无效或已过期"));
        }
        Assert.assertNull("拒绝后不得残留上下文", AccountContext.get());
    }

    @Test
    public void validate_noAuthorizationHeader_passThroughDualTrack() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        bearer.validate(request);
        Assert.assertNull("无 Bearer 头＝X-App-Key 轨继续，本轨静默放行且不置上下文",
                AccountContext.get());
    }

    @Test
    public void validate_nonBearerScheme_rejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        try {
            bearer.validate(request);
            Assert.fail("非 Bearer 形态必须拒");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("Authorization 头形态非法"));
        }
    }
}
