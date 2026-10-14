package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.jwt.SsoJwtUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.SsoTicketRes;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: SsoTicketServiceTest
 * Description: 出票编排判定矩阵（M15 棒 0b-4 改拍回灌：SPI 整层撤销，mock 目标＝core 自有仓储
 * AcPartnerService＋AcSsoRedirectService；纯 Mockito + MockHttpServletRequest，离线）。
 * <p>覆盖：组合判据 (app_id→partner_id, redirect_uri) 全串等值命中签出且票可被公钥验回 username／
 * redirect_uri 未登记拒／<b>🔴 判别针：地址登记在另一家名下必拒（防"全局按 URI 查"回潮）</b>／
 * app_id 空白或查不到 ac_partner 拒且不透出白名单查询／前缀或子域形态地址不命中且入查的是原串全值
 * （防子域绕过）／未登录拒且 verifyNoInteractions 扩到 ac_user／ac_partner／ac_sso_redirect 三侧／
 * 会话无效拒／脏 Authorization 头按框架口径业务拒绝／私钥缺席拒（非 NPE）／ttl 缺席或非正拒且不静默兜底 60。
 * 🔴 取号口径腿：票的 username claim＝ac_user.user_name（登录名）而非工号——工号与登录名取两个不同串；
 * 工号查不到 ac_user 行 ⇒ 拒签。</p>
 * <p>0b-6 密码学面修复轮：G-8 把 {@code expiresAt} 的 ±2 秒宽容窗换成**严格等值**断言（等值于票内解出的 exp），
 * 另加一条"压到秒边界再出票"的判别腿；NIT-8 加 ttl 超 900 秒上界的编排侧拒绝腿
 * （证明上界不是只设在 {@code sign} 的相对 ttl 口径上）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
public class SsoTicketServiceTest {

    /** 票里该带的值＝登录名（消费侧按这一列查人） */
    private static final String USERNAME = "BidrAdmin";
    /** token 载体里的工号——与登录名故意取两个不同的串，防"塞工号"这类缺陷被单一常量掩盖 */
    private static final String CUSTOMER_NUMBER = "000001";
    private static final String APP_ID = "inkhub-sub-a";
    /** app_id 空白腿用的另一个合法 app（证明空白拒因在 partner 查询之前就发生） */
    private static final String REGISTERED_URI = "https://sub.example.com/sso/cb";
    /** 登记在"另一家"（OTHER_PARTNER_ID）名下的合法地址——组合判据判别针的靶子 */
    private static final String OTHER_PARTNER_URI = "https://sub.example.com/other/cb";
    private static final long PARTNER_ID = 9L;
    private static final long OTHER_PARTNER_ID = 8L;

    private TokenService tokenService;
    private AcUserService acUserService;
    private AcPartnerService acPartnerService;
    private AcSsoRedirectService acSsoRedirectService;
    private String privCfg;
    private String pubCfg;
    private final String callerToken =
            AuthTokenUtil.getToken(AuthTokenUtil.buildToken("tk-ticket", TokenType.WEB_ACCESS_TOKEN, CUSTOMER_NUMBER, 1200));

    @Before
    public void setUp() throws Exception {
        tokenService = Mockito.mock(TokenService.class);
        acUserService = Mockito.mock(AcUserService.class);
        acPartnerService = Mockito.mock(AcPartnerService.class);
        acSsoRedirectService = Mockito.mock(AcSsoRedirectService.class);
        AcUser callerUser = new AcUser();
        callerUser.setUserName(USERNAME);
        callerUser.setCustomerNumber(CUSTOMER_NUMBER);
        Mockito.when(acUserService.getByCustomerNumber(CUSTOMER_NUMBER)).thenReturn(callerUser);
        Mockito.when(acUserService.getByCustomerNumber(Mockito.argThat(cn -> !CUSTOMER_NUMBER.equals(cn))))
                .thenReturn(null);
        // partner 桩：APP_ID → id=PARTNER_ID；其余 app_id 查不到（返回 null）
        AcPartner partner = new AcPartner();
        partner.setId(PARTNER_ID);
        partner.setAppKey(APP_ID);
        Mockito.when(acPartnerService.getByAppKey(APP_ID)).thenReturn(partner);
        // 白名单桩：组合 (partner_id, redirect_uri) 全串等值 ∧ valid='1' 才命中——
        // (9, REGISTERED_URI) 登记过；OTHER_PARTNER_URI 只登记在 (8, ·) 名下，(9, ·) 组合不命中。
        Mockito.when(acSsoRedirectService.existsValid(Mockito.anyLong(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    Long partnerId = invocation.getArgument(0);
                    String uri = invocation.getArgument(1);
                    if (PARTNER_ID == partnerId && REGISTERED_URI.equals(uri)) {
                        return true;
                    }
                    return OTHER_PARTNER_ID == partnerId && OTHER_PARTNER_URI.equals(uri);
                });
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class)))
                .thenAnswer(invocation -> "tk-ticket".equals(((TokenInfo) invocation.getArgument(0)).getToken()));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privCfg = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        pubCfg = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private SsoTicketService newService(String privateKey, Long ttlSeconds) {
        return new SsoTicketService(tokenService, acUserService, acPartnerService, acSsoRedirectService,
                privateKey, ttlSeconds);
    }

    private MockHttpServletRequest loggedInRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, callerToken);
        return request;
    }

    // ==================== 通腿：白名单内签出 ====================

    @Test
    public void whitelistedRedirectIssuesTicketVerifiableByPublicKey() {
        SsoTicketRes res = newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
        Assert.assertNotNull(res);
        Assert.assertNotNull(res.getIdToken());
        // 签出的票必须能被配对公钥验回当前会话用户的**登录名**（与验票面同一口径）
        String ticketUserName = SsoJwtUtil.verifyAndGetUsername(res.getIdToken(), pubCfg);
        Assert.assertEquals(USERNAME, ticketUserName);
        Assert.assertNotEquals("票的 username claim 不得是工号", CUSTOMER_NUMBER, ticketUserName);
        Assert.assertEquals(REGISTERED_URI, res.getRedirectUri());
        // G-8（承诺面 41）：expiresAt 必须与票内 exp **严格等值**——原来的 ±2 秒宽容窗正是"两次读时钟"
        // 这个缺陷得以苟活的口子，改严之后跨秒边界差 1 秒直接翻红
        long expInTicket = readExpClaim(res.getIdToken());
        Assert.assertNotNull(res.getExpiresAt());
        Assert.assertEquals("expiresAt 必须等于票内解出的 exp（单次取时钟）", expInTicket,
                res.getExpiresAt().longValue());
        Assert.assertTrue("exp 仍须落在 now+ttl 量级: " + expInTicket,
                expInTicket >= System.currentTimeMillis() / 1000L + 59
                        && expInTicket <= System.currentTimeMillis() / 1000L + 61);
        // 组合判据自证：partner 解析出的 id 与原串整值一起入查（core 不截断不加工）
        Mockito.verify(acPartnerService).getByAppKey(APP_ID);
        Mockito.verify(acSsoRedirectService).existsValid(PARTNER_ID, REGISTERED_URI);
    }

    /**
     * G-8 判别腿（时间放大版）：把出票动作压到"秒边界前 ≤5ms"再执行——旧实现（sign 内读一次时钟、
     * service 再读一次）在 RSA 签名＋JSON 序列化耗掉的那几毫秒里必然跨界 ⇒ 两值差 1 秒 ⇒ 本腿翻红；
     * 新实现只算一次 exp ⇒ 恒等。挡的改写＝{@code SsoTicketService} 第 6 步退回双读时钟。
     */
    @Test
    public void expiresAtEqualsTicketExpAcrossSecondBoundary() throws Exception {
        awaitNearSecondBoundary();
        SsoTicketRes res = newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
        Assert.assertEquals("跨秒边界时 expiresAt 与票内 exp 仍须严格等值",
                readExpClaim(res.getIdToken()), res.getExpiresAt().longValue());
    }

    /** 睡到本秒剩余 ≤4ms 处（JDK8 无 onSpinWait，粒度靠 sleep(1)≈10ms 系统计时器，够用且不误伤正确实现） */
    private static void awaitNearSecondBoundary() throws Exception {
        long target = System.currentTimeMillis() / 1000L * 1000L + 1000L - 4L;
        while (System.currentTimeMillis() < target) {
            Thread.sleep(1L);
        }
    }

    /**
     * NIT-8（承诺面 43）编排侧腿：ttl 超 900 秒上界必须业务拒绝。
     * 🔴 关键点＝出票编排走的是 {@code signWithExp}（绝对 exp 口径），若上界只设在 {@code sign} 上，
     * 本腿就会签出一枚 86400000 秒（≈2.7 年）不可撤的 bearer 票并翻红。
     */
    @Test
    public void ttlAboveHardUpperBoundRejectedByOrchestration() {
        for (Long tooLong : new Long[]{901L, 86400L, 86400000L}) {
            try {
                newService(privCfg, tooLong).issue(APP_ID, REGISTERED_URI, loggedInRequest());
                Assert.fail("ttl=" + tooLong + " 必须拒绝出票（超出允许上限）");
            } catch (ServiceException e) {
                Assert.assertTrue("文案必须点名上限: " + e.getMessage(),
                        e.getMessage().contains("SSO票据有效期超出允许上限"));
            }
        }
    }

    /** 从票串里解出 exp claim（第二段 base64url JSON），不经 util 的验票面 */
    private static long readExpClaim(String token) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> claims = com.bidr.kernel.utils.JsonUtil.readJson(
                payload, HashMap.class, String.class, Object.class);
        Assert.assertNotNull("payload 必须是可解析 JSON: " + payload, claims);
        Object exp = claims.get(SsoJwtUtil.CLAIM_EXP);
        Assert.assertNotNull("票内必须有 exp claim: " + payload, exp);
        return ((Number) exp).longValue();
    }

    // ==================== 白名单拒法（组合判据，承诺面 7/8/9 的 0b-4 形态） ====================

    @Test
    public void unregisteredRedirectRejectedWithDedicatedMessage() {
        try {
            newService(privCfg, 60L).issue(APP_ID, "https://evil.example.com/cb", loggedInRequest());
            Assert.fail("白名单外必须业务拒绝");
        } catch (ServiceException e) {
            String msg = e.getMessage();
            Assert.assertTrue("文案应含根因短语: " + msg, msg.contains("redirect_uri 未登记"));
            Assert.assertFalse("文案不得带状态码数字: " + msg, msg.matches("(?s).*\\b[45]\\d{2}\\b.*"));
        }
    }

    /**
     * 🔴 判别针（防"全局按 URI 查"回潮，必须自证活着）：
     * app_id 合法（partner 9）、redirect_uri 也在白名单表里登记过——但登记在**另一家**（partner 8）名下，
     * 组合 (9, uri) 未命中 ⇒ 必拒；且入查参数必须带解析出的 partner_id，不得只按 URI 查。
     */
    @Test
    public void uriRegisteredUnderAnotherPartnerRejectedForThisApp() {
        try {
            newService(privCfg, 60L).issue(APP_ID, OTHER_PARTNER_URI, loggedInRequest());
            Assert.fail("地址登记在另一家名下，本 app 组合未命中必须拒");
        } catch (ServiceException e) {
            Assert.assertTrue("文案应含根因短语: " + e.getMessage(), e.getMessage().contains("redirect_uri 未登记"));
        }
        Mockito.verify(acSsoRedirectService).existsValid(PARTNER_ID, OTHER_PARTNER_URI);
        Mockito.verify(acSsoRedirectService, Mockito.never()).existsValid(OTHER_PARTNER_ID, OTHER_PARTNER_URI);
    }

    @Test
    public void prefixOrSubdomainLookalikeRedirectNotWhitelistedAndFullStringPassed() {
        SsoTicketService service = newService(privCfg, 60L);
        // 前缀延伸（子域/路径追加）不命中：命中判定＝全串等值，core 侧无 startsWith/like 逻辑
        for (String lookalike : new String[]{REGISTERED_URI + "/evil", "https://sub.example.com",
                REGISTERED_URI + ".evil.com"}) {
            try {
                service.issue(APP_ID, lookalike, loggedInRequest());
                Assert.fail("近似地址不得命中白名单: " + lookalike);
            } catch (ServiceException e) {
                Assert.assertTrue(e.getMessage().contains("redirect_uri 未登记"));
            }
        }
        // 针还活着：白名单必须以 (解析出的 partner_id, 原串整值) 被调用（若 core 截断/前缀化，这里参数对不上即翻红）
        Mockito.verify(acSsoRedirectService).existsValid(PARTNER_ID, REGISTERED_URI + "/evil");
        Mockito.verify(acSsoRedirectService).existsValid(PARTNER_ID, "https://sub.example.com");
        Mockito.verify(acSsoRedirectService).existsValid(PARTNER_ID, REGISTERED_URI + ".evil.com");
    }

    @Test
    public void blankRedirectRejectedAsParamFormat() {
        try {
            newService(privCfg, 60L).issue(APP_ID, "  ", loggedInRequest());
            Assert.fail("空白 redirect_uri 必须参数格式拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("redirect_uri"));
        }
    }

    // ==================== app_id 拒法（改拍新增必填） ====================

    @Test
    public void blankAppIdRejectedAsParamFormatWithoutQueryingWhitelist() {
        for (String blankApp : new String[]{"", "  ", null}) {
            try {
                newService(privCfg, 60L).issue(blankApp, REGISTERED_URI, loggedInRequest());
                Assert.fail("空白 app_id 必须参数格式拒绝: [" + blankApp + "]");
            } catch (ServiceException e) {
                Assert.assertTrue("文案应点名 app_id: " + e.getMessage(), e.getMessage().contains("app_id"));
            }
        }
        // 拒因在参数层：partner 与白名单都不许被触达
        Mockito.verifyNoInteractions(acPartnerService);
        Mockito.verifyNoInteractions(acSsoRedirectService);
    }

    @Test
    public void unknownAppIdRejectedWithoutQueryingWhitelist() {
        // app_id 在 ac_partner 查不到 ⇒ 拒且不透出白名单查询结果（不给"全局按 URI 查"留门）
        try {
            newService(privCfg, 60L).issue("no-such-app", REGISTERED_URI, loggedInRequest());
            Assert.fail("未登记 app_id 必须业务拒绝");
        } catch (ServiceException e) {
            String msg = e.getMessage();
            Assert.assertTrue("文案应含根因短语: " + msg, msg.contains("app_id 未登记"));
            Assert.assertFalse("文案不得带状态码数字: " + msg, msg.matches("(?s).*\\b[45]\\d{2}\\b.*"));
        }
        Mockito.verifyNoInteractions(acSsoRedirectService);
    }

    // ==================== 登录态闸（先于 partner/白名单，承诺面 7 的分层腿） ====================

    @Test
    public void notLoggedInRejectedWithoutTouchingWhitelistOrAcUserRepo() {
        try {
            newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, new MockHttpServletRequest());
            Assert.fail("未登录必须业务拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue("文案应点名未登录: " + e.getMessage(), e.getMessage().contains("未登录"));
        }
        // 未登录不得触达 partner、白名单、账户三侧查询（取号只能发生在登录判定之后）
        Mockito.verifyNoInteractions(acUserService);
        Mockito.verifyNoInteractions(acPartnerService);
        Mockito.verifyNoInteractions(acSsoRedirectService);
    }

    @Test
    public void callerWithoutAcUserRowRejectedAndNoTicketIssued() {
        // 🔴 取号口径的判别腿：token 工号在 ac_user 查不到行 ⇒ 业务拒绝，绝不退化成"把工号塞进票"
        Mockito.when(acUserService.getByCustomerNumber(CUSTOMER_NUMBER)).thenReturn(null);
        try {
            newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
            Assert.fail("工号查不到 ac_user 行必须拒绝出票");
        } catch (ServiceException e) {
            Assert.assertTrue("文案应点名无登录名: " + e.getMessage(), e.getMessage().contains("无登录名"));
        }
        Mockito.verify(acUserService).getByCustomerNumber(CUSTOMER_NUMBER);
    }

    @Test
    public void acUserRowWithBlankUserNameRejected() {
        // 第二腿：行在但 user_name 列空 ⇒ 同样拒（防"票里带 null/空串"）
        Mockito.when(acUserService.getByCustomerNumber(CUSTOMER_NUMBER)).thenReturn(new AcUser());
        try {
            newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
            Assert.fail("user_name 为空必须拒绝出票");
        } catch (ServiceException e) {
            Assert.assertTrue("文案应点名无登录名: " + e.getMessage(), e.getMessage().contains("无登录名"));
        }
    }

    @Test
    public void sessionUnknownToServerRejected() {
        Mockito.when(tokenService.verifyToken(Mockito.any(TokenInfo.class))).thenReturn(false);
        try {
            newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
            Assert.fail("会话无效必须拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("未登录"));
        }
        Mockito.verifyNoInteractions(acPartnerService);
        Mockito.verifyNoInteractions(acSsoRedirectService);
    }

    @Test
    public void dirtyAuthHeaderRejectedAsFrameworkContract() {
        // 脏头在 extractToken 内即抛 PA_PARAM_FORMAT（"安全令牌格式错误"）——出票口不吞不降级
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestConst.TOKEN, "!!!not-a-token!!!");
        try {
            newService(privCfg, 60L).issue(APP_ID, REGISTERED_URI, request);
            Assert.fail("脏 Authorization 头必须业务拒绝");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("安全令牌"));
        }
    }

    // ==================== 配置闸：缺席即拒，core 不兜底 ====================

    @Test
    public void missingPrivateKeyRejectedNotNpe() {
        try {
            newService("", 60L).issue(APP_ID, REGISTERED_URI, loggedInRequest());
            Assert.fail("私钥缺席必须业务拒绝（不得 NPE）");
        } catch (ServiceException e) {
            Assert.assertTrue("文案应点名私钥未配置: " + e.getMessage(), e.getMessage().contains("私钥未配置"));
        }
    }

    @Test
    public void missingOrNonPositiveTtlRejectedWithoutSilentDefault() {
        // 🔴 承诺口径：ttl 缺省值 60 属棒 0b-2 的 yml；core 配置缺席必须显式拒，不得静默用默认值出票
        for (Long badTtl : new Long[]{null, 0L, -60L}) {
            try {
                newService(privCfg, badTtl).issue(APP_ID, REGISTERED_URI, loggedInRequest());
                Assert.fail("ttl=" + badTtl + " 必须拒绝出票");
            } catch (ServiceException e) {
                Assert.assertTrue("文案应点名有效期未配置: " + e.getMessage(),
                        e.getMessage().contains("SSO票据有效期未配置"));
            }
        }
    }
}
