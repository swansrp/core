package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import com.bidr.platform.redis.service.RedisService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: OAuth2TokenServiceTest
 * Description: 授权码换 token 判定矩阵（T1.2 棒 6，任务书"授权码生命周期≥5"的主载体）：
 * 签发→换 token 全通＋token 形态/claims 断言；二次用失效、过期失效、redirect 不匹配拒绝、
 * client 错误拒绝、白名单变动拒绝、grant_type 错误拒绝、跨 client code 拒绝。
 * 仓储/Redis 全 mock（离线腿），钥匙用例内现生成（RSA-2048）。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class OAuth2TokenServiceTest {

    private static final String CLIENT_ID = "demo-app";
    private static final String CLIENT_SECRET = "ppkxE7Wxx9tfM9vz";
    // 用公网串做白名单腿：localhost/127/10 自 2026-11-04 起免登记（见 OAuth2ClientServiceTest），
    // 拿本地串测"白名单变动必须拒"会恒过、失去判别力
    private static final String REDIRECT_URI = "https://sub.example.com/oauth2/callback";

    private static String privateKeyBase64;
    private static String publicKeyBase64;

    private Map<String, Object> redisStore;
    private AcSsoRedirectService acSsoRedirectService;
    private OAuth2AuthorizationCodeService codeService;
    private OAuth2TokenService tokenService;
    private IdentityIdpProperties properties;

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
        properties = new IdentityIdpProperties();
        properties.setPrivateKey(privateKeyBase64);
        properties.setPublicKey(publicKeyBase64);
        properties.setTokenTtlSeconds(3600L);
        properties.setCodeTtlSeconds(300L);

        AcPartner partner = new AcPartner();
        partner.setId(1L);
        partner.setAppKey(CLIENT_ID);
        partner.setAppSecret(CLIENT_SECRET);
        AcPartnerService acPartnerService = Mockito.mock(AcPartnerService.class);
        Mockito.when(acPartnerService.getByAppKey(CLIENT_ID)).thenReturn(partner);

        AcSsoRedirect whitelistRow = new AcSsoRedirect();
        whitelistRow.setPartnerId(1L);
        whitelistRow.setRedirectUri(REDIRECT_URI);
        acSsoRedirectService = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(acSsoRedirectService.listValid(1L))
                .thenReturn(Collections.singletonList(whitelistRow));

        OAuth2ClientService clientService = new OAuth2ClientService(acPartnerService,
                acSsoRedirectService);

        RedisService redisService = Mockito.mock(RedisService.class);
        redisStore = new ConcurrentHashMap<>();
        Mockito.doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0, String.class), invocation.getArgument(2));
            return null;
        }).when(redisService).set(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any());
        Mockito.when(redisService.get(ArgumentMatchers.anyString(),
                ArgumentMatchers.<Class<?>>any(), ArgumentMatchers.<Class<?>>any()))
                .thenAnswer(invocation -> redisStore.get(invocation.getArgument(0, String.class)));
        Mockito.when(redisService.delete(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            redisStore.remove(invocation.getArgument(0, String.class));
            return Boolean.TRUE;
        });
        codeService = new OAuth2AuthorizationCodeService(redisService, properties);

        tokenService = new OAuth2TokenService(clientService, codeService, properties);
    }

    /** 走真实签发链落一个合法 code（绑定本测试的 client/redirect/用户） */
    private String issueBoundCode() {
        OAuth2AuthorizationCode ctx = new OAuth2AuthorizationCode();
        ctx.setClientId(CLIENT_ID);
        ctx.setRedirectUri(REDIRECT_URI);
        ctx.setUserId(42L);
        ctx.setCustomerNumber("000001");
        ctx.setUserName("zhangsan");
        ctx.setName("张三");
        ctx.setTenantIds(new ArrayList<>(Arrays.asList(5L, 7L)));
        return codeService.issue(ctx);
    }

    @Test
    public void grant_fullLifecycle_issuesStandardToken() {
        String code = issueBoundCode();
        Map<String, Object> res = tokenService.grantByAuthorizationCode(
                OAuth2TokenService.GRANT_AUTHORIZATION_CODE, code, CLIENT_ID, CLIENT_SECRET,
                REDIRECT_URI);
        Assert.assertEquals("Bearer", res.get("token_type"));
        Assert.assertEquals(3600L, res.get("expires_in"));
        String accessToken = String.valueOf(res.get("access_token"));
        Map<String, Object> claims = IdpJwtUtil.verify(accessToken, publicKeyBase64);
        Assert.assertNotNull("换到的 access_token 必须能用 JWKS 同钥验过", claims);
        Assert.assertEquals("inkhub", claims.get(IdpJwtUtil.CLAIM_ISS));
        Assert.assertEquals("000001", claims.get(IdpJwtUtil.CLAIM_SUB));
        Assert.assertEquals(42L, ((Number) claims.get(IdpJwtUtil.CLAIM_UID)).longValue());
        Assert.assertEquals("zhangsan", claims.get(IdpJwtUtil.CLAIM_USERNAME));
        Assert.assertEquals("张三", claims.get(IdpJwtUtil.CLAIM_NAME));
        // 数值逐位比较（同 IdpJwtUtilTest：JWT 载荷 JSON 往返可能绑成 Integer，List 整体 equals 假红）
        List<?> tenantClaim = (List<?>) claims.get(IdpJwtUtil.CLAIM_TENANT_IDS);
        Assert.assertEquals(2, tenantClaim.size());
        Assert.assertEquals(5L, ((Number) tenantClaim.get(0)).longValue());
        Assert.assertEquals(7L, ((Number) tenantClaim.get(1)).longValue());
        Assert.assertEquals(CLIENT_ID, claims.get(IdpJwtUtil.CLAIM_APP_ID));
        Assert.assertEquals(3600L, ((Number) claims.get(IdpJwtUtil.CLAIM_EXP)).longValue()
                - ((Number) claims.get(IdpJwtUtil.CLAIM_IAT)).longValue());
    }

    @Test
    public void grant_reusedCode_rejected() {
        String code = issueBoundCode();
        Map<String, Object> first = tokenService.grantByAuthorizationCode("authorization_code",
                code, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);
        Assert.assertNotNull(first);
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI), "invalid_grant", "授权码二次使用必须拒");
    }

    @Test
    public void grant_expiredCode_rejected() {
        String code = issueBoundCode();
        redisStore.clear(); // 模拟 Redis TTL 到期清键
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI), "invalid_grant", "过期授权码必须拒");
    }

    @Test
    public void grant_redirectMismatch_rejected() {
        String code = issueBoundCode();
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, CLIENT_SECRET, "http://127.0.0.1:5174/evil"), "invalid_grant",
                "redirect 与签发时不一致必须拒");
    }

    @Test
    public void grant_wrongClientSecret_rejected() {
        String code = issueBoundCode();
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, "wrong-secret", REDIRECT_URI), "invalid_client", "client 错误必须拒");
    }

    @Test
    public void grant_unknownClient_rejected() {
        String code = issueBoundCode();
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                "ghost-client", CLIENT_SECRET, REDIRECT_URI), "invalid_client", "未登记 client 必须拒");
    }

    @Test
    public void grant_wrongGrantType_rejected() {
        String code = issueBoundCode();
        assertTokenError(() -> tokenService.grantByAuthorizationCode("refresh_token", code,
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI), "unsupported_grant_type",
                "本棒只做授权码腿");
    }

    @Test
    public void grant_codeBoundToOtherClient_rejected() {
        // code 绑定面 client 与请求面 client 不一致（签发串改绑）→ invalid_grant
        OAuth2AuthorizationCode ctx = new OAuth2AuthorizationCode();
        ctx.setClientId("other-client");
        ctx.setRedirectUri(REDIRECT_URI);
        ctx.setUserId(42L);
        ctx.setCustomerNumber("000001");
        ctx.setUserName("zhangsan");
        String code = codeService.issue(ctx);
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI), "invalid_grant", "跨 client code 必须拒");
    }

    @Test
    public void grant_redirectWhitelistChanged_rejected() {
        String code = issueBoundCode();
        // 白名单在签发后被清（登记行停用/删除）→ 再闸拒绝（code 已随消费烧掉，fail-closed）
        Mockito.when(acSsoRedirectService.listValid(1L)).thenReturn(Collections.emptyList());
        assertTokenError(() -> tokenService.grantByAuthorizationCode("authorization_code", code,
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI), "invalid_grant", "白名单变动必须拒");
    }

    private void assertTokenError(Runnable action, String expectedError, String message) {
        try {
            action.run();
            Assert.fail(message + "——应当抛 OAuth2TokenException");
        } catch (OAuth2TokenException e) {
            Assert.assertEquals(message, expectedError, e.getError());
        }
    }
}
