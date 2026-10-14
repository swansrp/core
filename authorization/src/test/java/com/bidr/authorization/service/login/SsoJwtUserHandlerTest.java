package com.bidr.authorization.service.login;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.utils.jwt.SsoJwtUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Title: SsoJwtUserHandlerTest
 * Description: 内置自读票验票 handler 判定矩阵（M15 棒 0b-2，离线：MockHttpServletRequest＋
 * RequestContextHolder 手工绑定，不起容器——裁定 6）。覆盖：
 * <ol>
 *   <li>参数取到／header 回落取到（取序腿：参数空白才落 header）；</li>
 *   <li>无票回 null／脏票回 null／公钥空白回 null／拿不到当前请求回 null（承诺面 5 的各拒腿）；</li>
 *   <li><b>过期票回 null（M-4 承重针）</b>——JDK Signature 只验签不判 exp，过期票裸验仍是 true，
 *       本腿必须走 SsoJwtUtil.verifyAndGetUsername 的自判 exp 分支才翻得出绿；</li>
 *   <li>换错公钥回 null（M-5：verify() 返回 false 不抛，handler 以 null 判拒，不写 catch-猜异常）。</li>
 * </ol>
 * 0b-6 写法修正：过期腿的合取断言拆成两条独立断言（NIT-10，先裸验签 true、再 util 回 null）；
 * 错钥腿加"配对公钥先验通这张票"的前置夹紧（NIT-11，防 sign 产出坏票时错钥腿照样绿）。
 * 密钥＝测试内临时生成（裁定 7：绝不读取仓库根 sso-key/ 真钥）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
public class SsoJwtUserHandlerTest {

    private static final String USERNAME = "sso-sub-user";
    /** 与票内用户名故意分开的另一身份，防"验通即回常量"这类假绿 */
    private static final String WRONG_USER_TICKET_NAME = "someone-else";

    private String privCfg;
    private String pubCfg;
    private String alienPubCfg;

    @Before
    public void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privCfg = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        pubCfg = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        // 另一把不相交公钥（错钥拒腿用）
        KeyPair alien = generator.generateKeyPair();
        alienPubCfg = Base64.getEncoder().encodeToString(alien.getPublic().getEncoded());
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SsoJwtUserHandler newHandler(String publicKey) {
        return new SsoJwtUserHandler(publicKey);
    }

    /** 绑定 mock request 到线程上下文（RequestContextHolder 取用先例的同源形态） */
    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** 手工签一张 exp 已过期的合法 RS256 票（票形照 SsoJwtUtil：三段 base64url 点分） */
    private String signedExpiredToken(String username, long expEpochSec) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"" + username + "\",\"exp\":" + expEpochSec + "}")
                        .getBytes(StandardCharsets.UTF_8));
        PrivateKey privateKey = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privCfg)));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    // ==================== 通腿：参数／header 两条取票路 ====================

    @Test
    public void validTicketFromRequestParameterResolvesUserName() {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(RequestConst.ID_TOKEN, token);
        bind(request);
        Assert.assertEquals(USERNAME, newHandler(pubCfg).resolveUserName());
    }

    @Test
    public void validTicketFallsBackToHeaderWhenParameterBlank() {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60);
        // 腿 1：参数完全缺席
        MockHttpServletRequest absent = new MockHttpServletRequest();
        absent.addHeader(RequestConst.ID_TOKEN, token);
        bind(absent);
        Assert.assertEquals(USERNAME, newHandler(pubCfg).resolveUserName());
        // 腿 2：参数为空白串（取序判据＝空白也算不命中，仍须回落 header）
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addParameter(RequestConst.ID_TOKEN, "   ");
        blank.addHeader(RequestConst.ID_TOKEN, token);
        bind(blank);
        Assert.assertEquals(USERNAME, newHandler(pubCfg).resolveUserName());
    }

    // ==================== 拒腿：各种取不到／验不过一律 null 不抛 ====================

    @Test
    public void absentTicketResolvesNull() {
        bind(new MockHttpServletRequest());
        Assert.assertNull(newHandler(pubCfg).resolveUserName());
    }

    @Test
    public void malformedTicketResolvesNullWithoutThrowing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(RequestConst.ID_TOKEN, "!!!not-a-jwt!!!");
        bind(request);
        Assert.assertNull(newHandler(pubCfg).resolveUserName());
    }

    @Test
    public void blankPublicKeyResolvesNull() {
        // 键存在但值是空串＝@ConditionalOnProperty 仍会出生（M-7 口径），行为面由此腿兜住：必 null
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(RequestConst.ID_TOKEN, token);
        bind(request);
        Assert.assertNull(newHandler("").resolveUserName());
    }

    @Test
    public void noRequestBoundResolvesNull() {
        // 非 web 线程（无 RequestContextHolder 绑定）⇒ null 不抛（承诺面 5"拿不到 request"腿）
        RequestContextHolder.resetRequestAttributes();
        Assert.assertNull(newHandler(pubCfg).resolveUserName());
    }

    // ==================== 承重针：过期票（M-4）与错公钥（M-5） ====================

    @Test
    public void expiredTicketResolvesNullEvenThoughSignatureIsValid() throws Exception {
        // 🔴 M-4 针：JDK Signature.verify 对这张签名合法的过期票回 true——handler 走
        // SsoJwtUtil.verifyAndGetUsername 的自判 exp 分支才回 null。若哪天有人把 exp 判定删掉，本腿翻红。
        String expired = signedExpiredToken(USERNAME, System.currentTimeMillis() / 1000L - 30);
        // 自证针活着：裸验签（不带 exp 判定）对同一张票必须为 true，否则这条腿只是在验"签名坏票"
        String[] parts = expired.split("\\.");
        java.security.PublicKey verifierKey = java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(pubCfg)));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(verifierKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        // NIT-10：合取断言拆两条独立断言（顺序＝先裸验签 true、再 util 回 null，照 SsoJwtUtilTest 的活针写法）——
        // 合取形态下"裸验签变 false"（对照失效）与"util 不再判 exp"（真回归）两种翻红会被同一个 message 混成一句
        Assert.assertTrue("对照失效：裸验签应认可这张合法签名的过期票（M-4 前提）",
                verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
        Assert.assertNull("对照活着：util 必须自判 exp 把这张票判死",
                SsoJwtUtil.verifyAndGetUsername(expired, pubCfg));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(RequestConst.ID_TOKEN, expired);
        bind(request);
        Assert.assertNull("过期票必须回 null", newHandler(pubCfg).resolveUserName());
    }

    @Test
    public void wrongPublicKeyResolvesNullWithoutThrowing() {
        // M-5：错钥时 Signature.verify 返回 false 而非抛——null 判拒，不靠 catch 异常路径
        String token = SsoJwtUtil.sign(privCfg, WRONG_USER_TICKET_NAME, 60);
        // NIT-11 夹紧：先用**配对**公钥断言这张票验通——否则哪天 sign 产出坏票（或 fixture 漂了），
        // "错钥回 null"这条腿照样绿，拒因从"钥不对"悄悄变成"票本身验不过"
        Assert.assertEquals("配对公钥必须验通这张票（错钥腿的前置夹紧）",
                WRONG_USER_TICKET_NAME, SsoJwtUtil.verifyAndGetUsername(token, pubCfg));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(RequestConst.ID_TOKEN, token);
        bind(request);
        Assert.assertNull(newHandler(alienPubCfg).resolveUserName());
    }
}
