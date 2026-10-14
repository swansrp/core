package com.bidr.authorization.utils.jwt;

import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: SsoJwtUtilTest
 * Description: 出票密码学面离线单测（M15 棒 0b-1，纯 JDK，无 Spring 上下文）。
 * <p>覆盖矩阵（承诺面 1/2/3/13）：签票三段形状与头/payload 内容；签验往返；
 * <b>黄金票腿</b>＝JDK 手写验签消费 fixture 里 auth0 产的真票（字节级互通钉死，
 * fixture 由 docs/poc/m15-jwt-interop/GoldenTicketProbe.java 自生成临时钥产出、非本工具所签，
 * 不许用自签自验替代）；过期拒（M-4，配"原始 Signature.verify 对同一过期票回 true"的反对照，
 * 删掉 util 的 exp 分支本用例即翻红）；缺 exp 拒；错钥拒且不抛（M-5）；
 * PEM 头尾/空白混入的密钥配置值可解（M-6）；脏串/坏钥一律回 null 绝不抛。</p>
 * <p>0b-6 密码学面修复轮新增腿：exp 时钟偏移容忍（G-5，容忍内验通／容忍外仍拒，各一条独立断言）；
 * alg 钉死活针（G-6，alg=none 与 alg=HS256 两张<b>真 RSA 签名</b>票各自"裸验签 true ＋ util 回 null"两条独立断言）；
 * 解析失败不得走 readJson 的无主全栈（G-7 形态腿）；ttl/exp 双口径 900 秒上界（NIT-8）；
 * 非字符串 username claim 拒（NIT-6）；尾点四段串拒（NIT-7）；unicode／引号／反斜杠用户名往返（NIT-12）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
public class SsoJwtUtilTest {

    private static final String USERNAME = "Sharp631";
    private static RSAPrivateKey priv;
    private static RSAPublicKey pub;
    /** 配置值形态一：裸 base64（X.509/PKCS#8 DER） */
    private static String pubCfg;
    private static String privCfg;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        priv = (RSAPrivateKey) keyPair.getPrivate();
        pub = (RSAPublicKey) keyPair.getPublic();
        pubCfg = Base64.getEncoder().encodeToString(pub.getEncoded());
        privCfg = Base64.getEncoder().encodeToString(priv.getEncoded());
    }

    @Test
    public void signProducesThreePartRs256TicketWithOnlyUsernameAndExp() throws Exception {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60L);
        String[] parts = token.split("\\.");
        Assert.assertEquals("票必须是三段点分串", 3, parts.length);
        for (String part : parts) {
            Assert.assertFalse("base64url 不应带填充等号: " + part, part.contains("="));
            Assert.assertTrue("段内不应出现标准base64的 +/ 字符", !part.contains("+") && !part.contains("/"));
        }
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        Assert.assertEquals("NIT-9：票头由 ALG_RS256 拼出后字节形态必须与原字面量完全一致",
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}", header);
        Assert.assertTrue("头应钉死 RS256: " + header, header.contains("\"alg\":\"RS256\""));
        Assert.assertTrue("头应声明 typ=JWT: " + header, header.contains("\"typ\":\"JWT\""));
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Assert.assertTrue("payload 应含 username: " + payload, payload.contains("\"username\":\"" + USERNAME + "\""));
        Assert.assertTrue("payload 应含 exp: " + payload, payload.contains("\"exp\":"));
        Assert.assertEquals("claims 只允许 username 与 exp 两个（票形状跨棒契约）",
                2, jsonKeyCount(payload));
    }

    @Test
    public void signVerifyRoundTripReturnsUsername() {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60L);
        Assert.assertEquals(USERNAME, SsoJwtUtil.verifyAndGetUsername(token, pubCfg));
    }

    // ==================== 黄金票腿（承诺面 13：字节级互通，不许自签自验替代） ====================

    @Test
    public void goldenTicketProducedByAuth0VerifiesWithJdkUtil() throws IOException {
        String goldenToken = readResource("sso/golden-token.txt");
        String goldenPublic = readResource("sso/golden-public-key.txt");
        Assert.assertFalse("golden-token.txt fixture 缺失或为空", goldenToken.isEmpty());
        Assert.assertFalse("golden-public-key.txt fixture 缺失或为空", goldenPublic.isEmpty());
        // 来源自证（针还活着）：fixture 票头的段字节与本项目 sign 的票头不同串
        //（auth0 序列化头＝{"typ":"JWT","alg":"RS256"}，我们＝{"alg":"RS256","typ":"JWT"}），
        // 证明这条腿验的不是"自签自验"，而是外部产票的字节级互通
        String goldenHeader = new String(Base64.getUrlDecoder().decode(goldenToken.split("\\.")[0]),
                StandardCharsets.UTF_8);
        String ourHeader = new String(Base64.getUrlDecoder().decode(
                SsoJwtUtil.sign(privCfg, USERNAME, 60L).split("\\.")[0]), StandardCharsets.UTF_8);
        Assert.assertNotEquals("黄金票必须产自外部（auth0），不得是本工具自签", ourHeader, goldenHeader);
        // auth0 java-jwt 产的真票（探针自生成临时钥）⇒ 本 JDK 手写验签必须消费得动
        Assert.assertEquals("黄金票腿：JDK 手写验签必须验通 auth0 产票并取回 username",
                USERNAME, SsoJwtUtil.verifyAndGetUsername(goldenToken, goldenPublic));
    }

    @Test
    public void goldenTicketWithForeignPublicKeyRejected() throws IOException {
        String goldenToken = readResource("sso/golden-token.txt");
        String goldenPublic = readResource("sso/golden-public-key.txt");
        // 反对照：黄金票签发钥＝探针自生成的临时钥，与本测试类临时钥必然不同；
        // 若哪天两者意外同钥，说明 fixture 生成方式已漂，此腿翻绿报信
        Assert.assertNotEquals("黄金票公钥必须独立于测试临时钥（否则错钥拒是死针）", pubCfg, goldenPublic);
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(goldenToken, pubCfg));
    }

    // ==================== exp 语义（M-4 承重：JDK Signature 不判 exp） ====================

    @Test
    public void expiredTicketRejectedEvenThoughRawSignatureVerifyIsTrue() throws Exception {
        // 手工签一张"签名有效但已过期"的票（util 的 sign 拒收非正 ttl，故这里走裸 Signature）
        long pastExp = System.currentTimeMillis() / 1000L - 30;
        String expired = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + pastExp + "}");
        // 反对照（针还活着）：JDK Signature.verify 对同一过期票回 true ⇒ util 若删掉自判 exp 分支本用例翻红
        Assert.assertTrue("M-4 前提复核：原始 Signature.verify 不判 exp", rawSignatureVerify(expired));
        Assert.assertNull("util 必须自判 exp：过期票一律拒", SsoJwtUtil.verifyAndGetUsername(expired, pubCfg));
    }

    // ==================== exp 时钟偏移容忍（承诺面 38／G-5） ====================

    /**
     * G-5 容忍腿一：票刚过期 10 秒（< LEEWAY_SECONDS=30）⇒ 仍验通。
     * 挡的改写＝把判据退回 {@code exp <= now}（或删除 leeway 项）时本腿翻红。
     * 时钟与容忍度经重载显式注入，不受测试执行耗时影响。
     */
    @Test
    public void expiredWithinLeewayStillVerifies() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String drifted = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + (now - 10) + "}");
        Assert.assertTrue("对照：裸验签对这张过期票仍回 true（拒因只可能来自 exp 判定）",
                rawSignatureVerify(drifted));
        Assert.assertEquals("时钟偏移容忍内的票必须验通（跨机 NTP 漂移不得把刚签出的票判死）",
                USERNAME, SsoJwtUtil.verifyAndGetUsername(drifted, pubCfg, now, 30L));
    }

    /**
     * G-5 容忍腿二：超出容忍（过期 31 秒 > 30）⇒ 仍拒。
     * 挡的改写＝把 leeway 放大成"不过期"或删掉整段 exp 判定时本腿翻红。
     */
    @Test
    public void expiredBeyondLeewayRejected() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String dead = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + (now - 31) + "}");
        Assert.assertTrue("对照：裸验签对这张过期票仍回 true", rawSignatureVerify(dead));
        Assert.assertNull("超出容忍必须拒", SsoJwtUtil.verifyAndGetUsername(dead, pubCfg, now, 30L));
    }

    /**
     * 🔴 承诺面 38 的<b>生产入口</b>腿（10-14 review F-A）：经两参 {@code verifyAndGetUsername(token, pub)} 打，
     * 时钟与容忍度都不注入 ⇒ 本腿消费的正是 {@code LEEWAY_SECONDS} 常量本体。
     * <p>上面两条容忍腿走的是四参重载（leeway 由调用方显式传 30），把类内常量改成 0 时它们恒绿，
     * 而接入指南"两侧 NTP 同步、偏移预算 &lt; 30 秒"那条红线当场失去守护——本腿补的就是这一格：
     * 变异自证＝{@code LEEWAY_SECONDS} 改成 0 ⇒ 本腿翻红；改成 ≥120 ⇒ 下面那条反向腿翻红。</p>
     */
    @Test
    public void driftWithinConstantLeewayVerifiesThroughProductionEntry() throws Exception {
        long realNow = System.currentTimeMillis() / 1000L;
        String drifted = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + (realNow - 10) + "}");
        Assert.assertTrue("对照：裸验签对这张过期票仍回 true", rawSignatureVerify(drifted));
        Assert.assertEquals("过期 10 秒仍在常量容忍内，生产入口必须验通", USERNAME,
                SsoJwtUtil.verifyAndGetUsername(drifted, pubCfg));
    }

    /**
     * 生产入口的反向腿：过期 120 秒（&gt; 常量 30 秒）必须拒——防有人把容忍放大成"永不过期"。
     */
    @Test
    public void driftBeyondConstantLeewayRejectedThroughProductionEntry() throws Exception {
        long realNow = System.currentTimeMillis() / 1000L;
        String dead = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + (realNow - 120) + "}");
        Assert.assertTrue("对照：裸验签对这张过期票仍回 true", rawSignatureVerify(dead));
        Assert.assertNull("超出常量容忍必须拒", SsoJwtUtil.verifyAndGetUsername(dead, pubCfg));
    }

    // ==================== alg 钉死的活针（承诺面 39／G-6） ====================
    /**
     * 头写 {@code alg:none} 但<b>签段是真 RSA 签名</b>（不能用假签 AAA，否则拒因被"签名坏"掩盖）：
     * 先断言裸 {@code Signature.verify} 回 true（自证拒因只能来自 alg 判定），再断言 util 回 null。
     * 挡的删除＝{@code SsoJwtUtil} 里 {@code !ALG_RS256.equals(...)} 那段判定。
     */
    @Test
    public void algNoneHeaderWithRealSignatureRejected() throws Exception {
        long exp = System.currentTimeMillis() / 1000L + 60;
        String noneAlg = signRaw("{\"alg\":\"none\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + exp + "}");
        Assert.assertTrue("对照失效检查：本票签名对本实现必须有效（否则 alg 针是死的）",
                rawSignatureVerify(noneAlg));
        Assert.assertNull("alg 非 RS256 的票必须拒（算法混淆/none 攻击面）",
                SsoJwtUtil.verifyAndGetUsername(noneAlg, pubCfg));
    }

    /**
     * 头写 {@code alg:HS256} 且签段仍是真 RSA 签名（对称算法混淆的经典形态）：
     * 同样先自证裸验签 true，再断言 util 回 null。与 none 腿拆成两条独立断言。
     */
    @Test
    public void algHs256HeaderWithRealSignatureRejected() throws Exception {
        long exp = System.currentTimeMillis() / 1000L + 60;
        String hs256Alg = signRaw("{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\",\"exp\":" + exp + "}");
        Assert.assertTrue("对照失效检查：本票签名对本实现必须有效", rawSignatureVerify(hs256Alg));
        Assert.assertNull("alg 声明 HS256 的票必须拒", SsoJwtUtil.verifyAndGetUsername(hs256Alg, pubCfg));
    }

    // ==================== claim 形态边界（NIT-6 / NIT-7） ====================

    /** username claim 非字符串（对象形态）⇒ 拒；挡的改写＝退回 {@code String.valueOf}（会把 {a=1} 当用户名返回） */
    @Test
    public void nonStringUsernameClaimRejected() throws Exception {
        long exp = System.currentTimeMillis() / 1000L + 60;
        String objectUsername = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":{\"a\":1},\"exp\":" + exp + "}");
        Assert.assertTrue("对照：裸验签有效，拒因只能来自 username 形态判定", rawSignatureVerify(objectUsername));
        Assert.assertNull("非字符串 username claim 必须拒", SsoJwtUtil.verifyAndGetUsername(objectUsername, pubCfg));
    }

    /** 尾点串 {@code h.p.s.} 不得被 split 缺省行为规整成合法三段（NIT-7） */
    @Test
    public void trailingDotTokenRejected() {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60L);
        Assert.assertEquals("合法票必须验通（否则本腿成了死针）", USERNAME,
                SsoJwtUtil.verifyAndGetUsername(token, pubCfg));
        Assert.assertNull("带尾点的四段串必须拒（split 必须用 limit=-1）",
                SsoJwtUtil.verifyAndGetUsername(token + ".", pubCfg));
    }

    // ==================== 用户名序列化往返（NIT-12） ====================

    /**
     * unicode／需转义用户名的 sign → verify 往返腿。
     * <p>理由＝username 直接取自 {@code ac_user.user_name}，转义若有问题的症状是"某人永远登不上子系统"，
     * 报错点离根因极远（出票侧看起来完全成功）。</p>
     */
    @Test
    public void unicodeAndEscapedUsernameRoundTrips() {
        for (String name : new String[]{"张三O'Brien", "李四\"Duane\" 王", "user\\back"}) {
            String token = SsoJwtUtil.sign(privCfg, name, 60L);
            Assert.assertEquals("含非 ASCII/引号/反斜杠的登录名必须原样往返: " + name,
                    name, SsoJwtUtil.verifyAndGetUsername(token, pubCfg));
        }
    }

    // ==================== G-7：拒票不得走 readJson 的无主全栈（形态针） ====================

    /** 坏 JSON 段的票必须走兜底 catch 收敛为 null（且不得抛）——拒票＝单行 WARN、无 stack、无回显 */
    @Test
    public void unparsableJsonSegmentRejectedWithoutThrowing() throws Exception {
        // 头段是合法 base64url 但内容不是 JSON 对象
        String badHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("[1,2,3]".getBytes(StandardCharsets.UTF_8));
        String badPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"" + USERNAME + "\",\"exp\":"
                        + (System.currentTimeMillis() / 1000L + 60) + "}").getBytes(StandardCharsets.UTF_8));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(badHeader + "." + badPayload + "." + badPayload, pubCfg));
        // payload 段合法 base64url 但内容非 JSON：签名有效，拒因只能来自解析失败
        String badPayloadToken = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}", "not-json");
        Assert.assertTrue("对照：裸验签有效", rawSignatureVerify(badPayloadToken));
        Assert.assertNull("payload 非 JSON 形态同样必须收敛为 null",
                SsoJwtUtil.verifyAndGetUsername(badPayloadToken, pubCfg));
    }

    @Test
    public void ticketWithoutExpClaimRejected() throws Exception {
        // exp 缺失同样拒（承诺面 3 判据的边界：只有"验通且未过期"才回 username）
        String noExp = signRaw("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"username\":\"" + USERNAME + "\"}");
        Assert.assertTrue("无exp票签名本身应有效", rawSignatureVerify(noExp));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(noExp, pubCfg));
    }

    @Test
    public void wrongPublicKeyRejectsWithoutThrowing() {
        // M-5：错钥时 Signature.verify 回 false 而非抛——util 收敛为 null，调用方以 null 判拒
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60L);
        KeyPair other;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            other = generator.generateKeyPair();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        String otherPub = Base64.getEncoder().encodeToString(other.getPublic().getEncoded());
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(token, otherPub));
    }

    @Test
    public void tamperedPayloadRejected() {
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 600L);
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"attacker\",\"exp\":" + (System.currentTimeMillis() / 1000L + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(parts[0] + "." + forgedPayload + "." + parts[2], pubCfg));
    }

    // ==================== 密钥配置值形态（M-6：容忍 PEM 头尾与空白） ====================

    @Test
    public void pemWrappedAndWhitespaceKeyValuesAreAccepted() {
        String pemPub = "-----BEGIN PUBLIC KEY-----\n" + pubCfg + "\n-----END PUBLIC KEY-----\n";
        String pemPriv = "-----BEGIN PRIVATE KEY-----\r\n" + privCfg + "\r\n-----END PRIVATE KEY-----";
        String token = SsoJwtUtil.sign(pemPriv, USERNAME, 60L);
        Assert.assertNotNull(token);
        Assert.assertEquals("带PEM头尾与换行的配置值必须与裸base64同解（口径照现网RsaUtil剥空白）",
                USERNAME, SsoJwtUtil.verifyAndGetUsername(token, pemPub));
        // 公钥值里混入多余空格同样可解
        String spacedPub = pemPub.replace("\n", "  \n ");
        Assert.assertEquals(USERNAME, SsoJwtUtil.verifyAndGetUsername(token, spacedPub));
    }

    // ==================== 永不抛契约（承诺面 2） ====================

    @Test
    public void garbageInputsAlwaysReturnNullNeverThrow() {
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(null, pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("", pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("   ", pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("not-a-jwt", pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("a.b", pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("a.b.c.d", pubCfg));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername("a.b.!!!bad-base64!!!", pubCfg));
        String token = SsoJwtUtil.sign(privCfg, USERNAME, 60L);
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(token, null));
        Assert.assertNull(SsoJwtUtil.verifyAndGetUsername(token, ""));
        Assert.assertNull("坏公钥串不许抛，回 null 即拒", SsoJwtUtil.verifyAndGetUsername(token, "%%%%not-base64%%%%"));
    }

    @Test
    public void signRejectsBlankKeyAndNonPositiveTtlAsServiceException() {
        try {
            SsoJwtUtil.sign("", USERNAME, 60L);
            Assert.fail("空白私钥必须业务拒绝");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("私钥"));
        }
        try {
            SsoJwtUtil.sign(privCfg, USERNAME, 0L);
            Assert.fail("非正 ttl 必须业务拒绝（core 不兜底）");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("有效期"));
        }
        try {
            SsoJwtUtil.sign(privCfg, "", 60L);
            Assert.fail("空白用户名必须业务拒绝");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("用户名"));
        }
        try {
            SsoJwtUtil.sign("%%%broken%%%", USERNAME, 60L);
            Assert.fail("不可解析私钥必须业务拒绝");
        } catch (ServiceException expected) {
            Assert.assertTrue(expected.getMessage().contains("私钥"));
        }
    }

    /**
     * NIT-8 上界腿：ttl 超过 900 秒必须业务拒绝（挡的删除＝sign 里的 {@code ttl <= MAX_TTL_SECONDS} 断言）。
     * 86400000 是"运维按毫秒口径误填"的真实形态——本批无 jti 吊销＋票走 URL 传，
     * 不拦就是发一枚约 2.7 年不可撤的 bearer 凭证。
     */
    @Test
    public void signRejectsTtlAboveHardUpperBound() {
        for (long tooLong : new long[]{901L, 86400L, 86400000L}) {
            try {
                SsoJwtUtil.sign(privCfg, USERNAME, tooLong);
                Assert.fail("ttl=" + tooLong + " 必须业务拒绝（超出允许上限）");
            } catch (ServiceException expected) {
                Assert.assertTrue("文案必须点名上限: " + expected.getMessage(),
                        expected.getMessage().contains("SSO票据有效期超出允许上限"));
            }
        }
        // 夹紧：上界内的合法值仍可签出
        Assert.assertEquals(USERNAME,
                SsoJwtUtil.verifyAndGetUsername(SsoJwtUtil.sign(privCfg, USERNAME, 900L), pubCfg));
    }

    /**
     * NIT-8 的第二处闸（绝对 exp 口径）腿：出票编排实际走 {@code signWithExp}（G-8 单次取值），
     * 只拦 {@code sign} 就等于给编排留洞——本腿挡的删除＝signWithExp 里的 {@code exp - now <= 900} 断言。
     */
    @Test
    public void signWithExpRejectsExpBeyondHardUpperBound() {
        long now = System.currentTimeMillis() / 1000L;
        for (long exp : new long[]{now + 901, now + 86400, now + 86400000L}) {
            try {
                SsoJwtUtil.signWithExp(privCfg, USERNAME, exp);
                Assert.fail("exp=" + exp + "（距今超出上限）必须业务拒绝");
            } catch (ServiceException expected) {
                Assert.assertTrue("文案必须点名上限: " + expected.getMessage(),
                        expected.getMessage().contains("SSO票据有效期超出允许上限"));
            }
        }
    }

    /**
     * 🔴 F-C 下界腿：{@code signWithExp} 的 exp 不得早于当前时刻——挡的形态＝把 ttl 口径的值（60）当 epoch 传入，
     * 旧判据只闸 {@code exp > 0} ⇒ 静默签出一枚"生来即过期"的票，出票口回 200 而子系统永远验不通。
     * <p>判据取"不早于"（{@code >=}）而非"严格晚于"＝{@code SsoTicketService} 先算 exp 再签，ttl=1 的档跨秒时
     * exp 会恰好等于签票时的 now，严格判据会让它随机被拒；而 exp==now 的票仍在 30 秒容忍内可用，不构成放行洞。</p>
     */
    @Test
    public void signWithExpRejectsExpInThePast() {
        long now = System.currentTimeMillis() / 1000L;
        for (long exp : new long[]{60L, now - 1, now - 3600}) {
            try {
                SsoJwtUtil.signWithExp(privCfg, USERNAME, exp);
                Assert.fail("exp=" + exp + "（早于当前时刻）必须业务拒绝");
            } catch (ServiceException expected) {
                Assert.assertTrue("文案必须点名过期时刻判据: " + expected.getMessage(),
                        expected.getMessage().contains("不得早于当前时刻"));
            }
        }
        // 夹紧＝exp==now 这界必须放行（防把下界写成严格大于，那会让 ttl=1 跨秒随机翻红）
        Assert.assertNotNull(SsoJwtUtil.signWithExp(privCfg, USERNAME, now));
        // 正向对照：未来 60 秒可签出且经生产入口验通
        Assert.assertEquals(USERNAME, SsoJwtUtil.verifyAndGetUsername(
                SsoJwtUtil.signWithExp(privCfg, USERNAME, now + 60), pubCfg));
    }

    // ==================== helpers ====================

    /** 用裸 JDK 签一张任意 payload 的票（构造 util 自身拒绝生成的边界票形） */
    private static String signRaw(String headerJson, String payloadJson) throws Exception {
        String signingInput = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(priv);
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signer.sign());
    }

    /** 原始 Signature.verify（不看 exp）——M-4 反对照用 */
    private static boolean rawSignatureVerify(String token) throws Exception {
        String[] parts = token.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pub);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    private static int jsonKeyCount(String json) {
        Map<?, ?> map = com.bidr.kernel.utils.JsonUtil.readJson(json, HashMap.class, String.class, Object.class);
        Assert.assertNotNull("payload 必须是可解析 JSON: " + json, map);
        return map.size();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = SsoJwtUtilTest.class.getClassLoader().getResourceAsStream(path)) {
            Assert.assertNotNull("测试资源缺失: " + path, is);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }
}
