package com.bidr.authorization.constants;

/**
 * Title: SsoConfigKeys
 * Description: SSO 出票/验票面的配置键名收口（M15 棒 0b-1 纯加法）。
 * <p>
 * 键名正源只在本类声明一次，消费方（如 {@code SsoTicketService} 的 {@code @Value}）经编译期
 * 字符串常量拼接引用，避免键名散落。🔴 墨枢侧只配私钥——公钥键 {@link #JWT_PUBLIC_KEY}
 * 属"接入方（子系统）"配置，<b>永不进墨枢任何配置文件</b>（M15 承诺面 20 的口径来源）。
 * 本批 sys_config 无新增键（白名单＝core 自有表 ac_sso_redirect，(partner_id, redirect_uri) 组合判定，
 * 10-14 改拍：反向 SPI 整层撤销、表移入 framework core）。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
public class SsoConfigKeys {

    /**
     * 出票 RSA 私钥（PKCS#8 DER 的 base64，容忍 PEM 头尾与空白混入）。
     * 缺席/空白 ⇒ 出票口业务拒绝（core 不设兜底值）。
     */
    public static final String JWT_PRIVATE_KEY = "sso.jwt.private-key";

    /**
     * 验票 RSA 公钥（X.509 SubjectPublicKeyInfo DER 的 base64，容忍 PEM 头尾与空白）。
     * 🔴 仅子系统/接入方配置；墨枢自身不声明此键。误声明的后果分两形态（与 {@code SsoJwtUserHandler} 类注释同口径）＝
     * <b>bidr 形态</b>（classpath 另有 {@code IdmSsoUserHandler}）＝双 handler 冲突态，业务侧能看到"实现冲突"拒绝；
     * <b>plsintec／子系统形态</b>（只有一只 handler）＝<b>没有任何冲突信号</b>，handler 静默生效，
     * 墨枢自签的票就成了打开墨枢本账号的通道——所以墨枢侧的纪律是"完全不声明该键"，不是"声明成空串"
     * （{@code @ConditionalOnProperty} 不带 havingValue＝键存在即出生）。机器针＝主仓
     * {@code SsoConfigSurfaceConventionTest}（射程＝本仓四档 yml 的字面声明）。
     */
    public static final String JWT_PUBLIC_KEY = "sso.jwt.public-key";

    /**
     * 票据存活秒数（exp = 签发时刻 + ttl）。缺省值 60 属部署档 yml（棒 0b-2 落），
     * 🔴 core 不写死兜底默认——配置缺席即拒绝出票。
     * <p>🔴 上界 <b>900 秒</b>（硬编码红线，不是配置键、不是 yml 值，见 NIT-8）：断言落在
     * {@code SsoJwtUtil.sign}（相对 ttl 口径）与 {@code SsoJwtUtil.signWithExp}（绝对 exp 口径，
     * 出票编排实际走这条）两处——只设一处等于给另一处留绕过洞。理由＝本批<b>无 jti 吊销</b>且票
     * <b>走 URL 参数传</b>（浏览器历史／nginx access log 可捞），
     * 运维若按毫秒口径误填 86400000 ＝发一枚约 2.7 年不可撤的 bearer 凭证，
     * 而现状没有任何一层会拒绝该配置——上界断言是唯一拦截点。</p>
     * <p>🔴 实际存活窗口＝<b>ttl + 30 秒</b>（{@code SsoJwtUtil} 的 exp 判定含 30 秒时钟偏移容忍，
     * 跨机 NTP 漂移不得把刚签出的票判死）⇒ ttl 60 的票最长 90 秒有效；对外文案与风险评估按 90 秒算。</p>
     */
    public static final String TICKET_TTL_SECONDS = "sso.ticket.ttl-seconds";

    /**
     * 出票请求参数名（回调地址）。
     */
    public static final String TICKET_PARAM_REDIRECT_URI = "redirect_uri";

    /**
     * 出票请求参数名（子系统应用标识，对应 ac_partner.app_key）。
     */
    public static final String TICKET_PARAM_APP_ID = "app_id";

    private SsoConfigKeys() {
        // 常量收口类，禁实例化
    }
}
