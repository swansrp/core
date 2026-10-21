package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.SsoConfigKeys;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.jwt.SsoJwtUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.SsoTicketRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Title: SsoTicketService
 * Description: SSO 出票编排（core 底座自带，M15 棒 0b-4 改拍回灌：SPI 整层撤销，白名单进 core 自有表）。
 * <p>
 * 判定顺序（每步都是业务拒绝，文案即根因、不带状态码数字）：
 * <ol>
 *   <li>app_id / redirect_uri 任一空白 ⇒ 参数格式拒绝；</li>
 *   <li>登录态：手搓 {@link AuthTokenUtil#extractToken(HttpServletRequest)} +
 *       {@link TokenService#verifyToken(TokenInfo)}（M-9 先例＝{@code SsoTokenIntrospectService}，
 *       不依赖线程态）。<b>未登录先拒</b>——partner 查询与白名单查询都排在本步之后，
 *       该分层判据由用例的 {@code verifyNoInteractions} 钉住；</li>
 *   <li>partner 闸：按 {@code app_id} 查 {@link AcPartnerService#getByAppKey}（app_key＝app_id）取 partner_id，
 *       查不到 ⇒ 拒（文案点名"app_id 未登记"）；</li>
 *   <li>白名单闸：按 partner_id 取该 partner 全部 valid='1' 登记行（{@link AcSsoRedirectService#listValid}），
 *       Java 侧做 <b>origin 等值</b>比较（{@link #originMatches}）：scheme 精确等值 ∧ host 等值（大小写不敏感）
 *       ∧ port 按「缺省口规格化（http→80、https→443）后等值」；任一侧解析失败（无 scheme／无 host／
 *       URISyntaxException／IllegalArgumentException）⇒ 不命中 ⇒ 拒（文案点名"redirect_uri 未登记"）。
 *       🔴 禁止退化成"只按 redirect_uri 全局查"——partner 组合判据是 10-14 用户改拍已定案；
 *       M-11 裁定：解析与比较放 Java 侧，mapper SQL 不动，登记行个位数无性能面。
 *       🔴 2026-10-21 回环豁免腿（用户拍板"127/localhost 别限制"）：回调 origin 是本机回环
 *       （{@link #isLoopbackCallback}＝localhost／127.0.0.0/8／::1，仅 http(s)、口任意）⇒ 跳过登记比对
 *       直接放行——开发联调的 vite 端口不固定，逐口登记是纯运维噪音；票落在用户自己机器的回环地址，
 *       不构成跨站外泄面（RFC 8252 §7.3 loopback redirect 同精神）。豁免的只是比对，登记行查询
 *       照旧执行；非 http(s)／解析失败不豁免（fail-closed）；</li>
 *   <li>配置：私钥缺席/空白、ttl 缺席/非正 ⇒ 拒绝。🔴 core 不写死 60 秒兜底——缺省值属部署档 yml
 *       （棒 0b-2），静默用默认值出票是被禁止的行为；</li>
 *   <li>签票：{@link SsoJwtUtil#signWithExp}（G-8＝exp 在此只算一次，票内 {@code exp} 与响应
 *       {@code expiresAt} 严格等值；🔴 不得改用 {@link SsoJwtUtil#sign} 的相对 ttl 口径后另读一次时钟）。
 *       🔴 username claim＝当前账号的 <b>ac_user.user_name（登录名）</b>，
 *       <b>不是工号</b>——票的消费侧按 {@code AcUserService.getUserByUserName} 查人（与 IdaaS 路同源同列），
 *       塞工号进去子系统必查不到。取号链：token 里的工号（{@link TokenInfo#getCustomerNumber()}，
 *       真实登录路径唯一稳定的身份载体）→ {@link AcUserService#getByCustomerNumber} → user_name；
 *       🔴 登录名取不到 ⇒ 业务拒绝，绝不签出"工号票"或空名票。</li>
 * </ol>
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Slf4j
@Service
public class SsoTicketService {

    private final TokenService tokenService;
    /**
     * 只用于把工号换成登录名（票的消费侧按 user_name 查人），core 自有表，不涉业务表。
     */
    private final AcUserService acUserService;
    /**
     * partner 闸：app_id → ac_partner（app_key）→ partner_id，core 自有表。
     */
    private final AcPartnerService acPartnerService;
    /**
     * 白名单闸：partner 名下登记行的 origin 与传入串 origin 等值（scheme+host+port），core 自有表 ac_sso_redirect。
     */
    private final AcSsoRedirectService acSsoRedirectService;
    /**
     * 出票私钥（键名正源＝{@link SsoConfigKeys#JWT_PRIVATE_KEY}）；空默认＝默认关闭出票。
     */
    private final String jwtPrivateKey;
    /**
     * 票据存活秒数（键名正源＝{@link SsoConfigKeys#TICKET_TTL_SECONDS}）；
     * 空默认 ⇒ 解析为 null ⇒ 拒绝出票（core 不设兜底数字）。
     */
    private final Long ticketTtlSeconds;

    public SsoTicketService(TokenService tokenService,
                            AcUserService acUserService,
                            AcPartnerService acPartnerService,
                            AcSsoRedirectService acSsoRedirectService,
                            @Value("${" + SsoConfigKeys.JWT_PRIVATE_KEY + ":}") String jwtPrivateKey,
                            @Value("${" + SsoConfigKeys.TICKET_TTL_SECONDS + ":}") Long ticketTtlSeconds) {
        this.tokenService = tokenService;
        this.acUserService = acUserService;
        this.acPartnerService = acPartnerService;
        this.acSsoRedirectService = acSsoRedirectService;
        this.jwtPrivateKey = jwtPrivateKey;
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    /**
     * 签发 SSO 票。
     *
     * @param appId       子系统应用标识（对应 ac_partner.app_key，必须已登记）
     * @param redirectUri 回调地址（其 origin 必须已登记在该 app_id 所属 partner 名下；出票后原串回显）
     * @param request     当前请求（登录态从 Authorization 头手抽，不依赖线程态）
     * @return 票串＋过期时刻（epoch 秒）＋回调地址回填
     */
    public SsoTicketRes issue(String appId, String redirectUri, HttpServletRequest request) {
        Validator.assertNotBlank(appId, ErrCodeSys.PA_PARAM_FORMAT,
                SsoConfigKeys.TICKET_PARAM_APP_ID);
        Validator.assertNotBlank(redirectUri, ErrCodeSys.PA_PARAM_FORMAT,
                SsoConfigKeys.TICKET_PARAM_REDIRECT_URI);

        // 1) 登录态先判：未登录不得触达 partner、白名单与任何账户查询
        TokenInfo caller = AuthTokenUtil.extractToken(request);
        boolean login = caller != null && tokenService.verifyToken(caller);
        Validator.assertTrue(login, ErrCodeSys.SYS_ERR_MSG, "未登录或会话已失效，拒绝签发SSO票据");

        // 2) partner 闸：app_id → ac_partner.app_key 取 partner_id，查不到即拒（不透出白名单查询）
        AcPartner partner = acPartnerService.getByAppKey(appId);
        Validator.assertNotNull(partner, ErrCodeSys.SYS_ERR_MSG,
                SsoConfigKeys.TICKET_PARAM_APP_ID + " 未登记（不在ac_partner），拒绝签发SSO票据");

        // 3) 白名单闸：按 partner 取全部 valid='1' 登记行，Java 侧 origin 等值比较（M-11）；
        //    解析失败⇒不命中，原串不做任何截断/加工参与比较。
        //    回环豁免腿（判据与理由见类注 2026-10-21 条）：豁免的是比对、不是取数路径——
        //    listValid 照旧入查，行为面只放宽判据，交互面与既有用例的 verify 口径兼容
        List<AcSsoRedirect> registered = acSsoRedirectService.listValid(partner.getId());
        boolean whitelisted = isLoopbackCallback(redirectUri) || (registered != null && registered.stream()
                .anyMatch(row -> originMatches(row.getRedirectUri(), redirectUri)));
        Validator.assertTrue(whitelisted, ErrCodeSys.SYS_ERR_MSG,
                SsoConfigKeys.TICKET_PARAM_REDIRECT_URI + " 未登记（不在该应用的SSO回调白名单），拒绝签发SSO票据");

        // 4) 配置闸：私钥/ttl 缺席即拒，core 不兜底
        Validator.assertNotBlank(jwtPrivateKey, ErrCodeSys.SYS_ERR_MSG,
                "SSO出票私钥未配置（" + SsoConfigKeys.JWT_PRIVATE_KEY + "），拒绝签发SSO票据");
        Validator.assertTrue(ticketTtlSeconds != null && ticketTtlSeconds > 0, ErrCodeSys.SYS_ERR_MSG,
                "SSO票据有效期未配置（" + SsoConfigKeys.TICKET_TTL_SECONDS + "），core不设兜底默认值，拒绝签发SSO票据");

        // 5) 取登录名：token 载体只有工号，票要的是消费侧查得着的 user_name
        AcUser callerUser = acUserService.getByCustomerNumber(caller.getCustomerNumber());
        String loginName = callerUser == null ? null : callerUser.getUserName();
        Validator.assertNotBlank(loginName, ErrCodeSys.SYS_ERR_MSG,
                "当前账号无登录名（ac_user.user_name），拒绝签发SSO票据");

        // 6) 签票：exp 只算一次（G-8，承诺面 41）——同一个值既签进票内、又回吐给 expiresAt，
        // 🔴 禁止再读一次 System.currentTimeMillis()：两次读数跨秒边界会让 expiresAt 与票内 exp 差 1 秒
        long expEpochSeconds = System.currentTimeMillis() / 1000L + ticketTtlSeconds;
        String idToken = SsoJwtUtil.signWithExp(jwtPrivateKey, loginName, expEpochSeconds);
        SsoTicketRes res = new SsoTicketRes();
        res.setIdToken(idToken);
        res.setExpiresAt(expEpochSeconds);
        res.setRedirectUri(redirectUri);
        log.info("[SSO出票]登录名: {}, 工号: {}, app_id: {}, partner: {}, 回调: {}, 存活秒: {}",
                loginName, caller.getCustomerNumber(), appId, partner.getId(), redirectUri, ticketTtlSeconds);
        return res;
    }

    /**
     * 白名单判据唯一真相（M15 追加棒 1，M-11）：两侧 URL 各解析出 origin 后做等值比较。
     * <ul>
     *   <li>scheme：精确等值（大小写敏感——http 与 https 本就不同源，别名也不放行）；</li>
     *   <li>host：等值，大小写不敏感（DNS 语义）；</li>
     *   <li>port：-1 按 scheme 默认口规格化（http→80、https→443；其它 scheme 保持 -1）后等值；</li>
     *   <li>任一侧解析失败（无 scheme／无 host／{@link URISyntaxException}／{@link IllegalArgumentException}）
     *       ⇒ false ⇒ 走既有"redirect_uri 未登记"拒绝文案。</li>
     * </ul>
     * 纯函数、无副作用，登记行串与传入串都只读不加工。
     */
    static boolean originMatches(String registeredUri, String candidateUri) {
        String registeredOrigin = originOf(registeredUri);
        return registeredOrigin != null && registeredOrigin.equals(originOf(candidateUri));
    }

    /**
     * 解析出 {@code scheme://host:port} 规格化串；解析不出 ⇒ null。
     * port 缺省只对 http/https 补默认口（80/443），其它 scheme 保持 -1——两侧走同一规则，
     * 比较仍自洽，只是非 http(s) 腿不做默认口等值。
     * 🔴 只取 {@code #}（hash）之前的部分即本判据语义——{@link URI} 解析天然丢弃 fragment，
     * path/query 也不参与，天然实现"只看 origin"。
     */
    private static String originOf(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return null;
        }
        try {
            URI parsed = new URI(uri.trim());
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = parsed.getPort();
            if (port == -1) {
                if ("http".equals(scheme)) {
                    port = 80;
                } else if ("https".equals(scheme)) {
                    port = 443;
                }
            }
            return scheme + "://" + host.toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 回环豁免判据（2026-10-21）：传入回调的 origin host 是本机回环 ⇒ true，豁免白名单比对。
     * <ul>
     *   <li>host＝{@code localhost}（大小写不敏感）／127.0.0.0/8（点分四段且首段 127）／{@code ::1}
     *       （IPv6 literal，{@link URI#getHost()} 取出带方括号形态，剥括号比对，另兼容全零展开写法）；</li>
     *   <li>scheme 必须 http/https——其它 scheme 一律不豁免；port 不参与（开发口 5173/5174/8083… 任意）；</li>
     *   <li>解析失败（无 scheme／无 host／{@link URISyntaxException}／{@link IllegalArgumentException}）⇒ false
     *       （fail-closed，与 {@link #originMatches} 同口径）。</li>
     * </ul>
     * 纯函数、无副作用。🔴 豁免面刻意收窄在"用户自己机器的回环地址"——非回环 host（含
     * {@code localhost.evil.com} 前缀套娃、{@code 127.0.0.1:5174.evil.com} 子域延伸、127.0.0.1 的
     * 十进制整数形态等变体）一律不命中，照走白名单比对。
     */
    static boolean isLoopbackCallback(String candidateUri) {
        if (candidateUri == null || candidateUri.trim().isEmpty()) {
            return false;
        }
        try {
            URI parsed = new URI(candidateUri.trim());
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (host == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                return false;
            }
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            if ("localhost".equals(lower)) {
                return true;
            }
            if (lower.startsWith("[") && lower.endsWith("]")) {
                String v6 = lower.substring(1, lower.length() - 1);
                return "::1".equals(v6) || "0:0:0:0:0:0:0:1".equals(v6);
            }
            return isLoopbackIPv4(lower);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 127.0.0.0/8 形态判定：点分四段、逐段 0-255 纯数字、首段 127（入参已小写）。 */
    private static boolean isLoopbackIPv4(String lowerHost) {
        if (!lowerHost.startsWith("127.")) {
            return false;
        }
        String[] parts = lowerHost.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }
}
