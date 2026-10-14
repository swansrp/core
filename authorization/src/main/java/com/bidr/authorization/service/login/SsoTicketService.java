package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.SsoConfigKeys;
import com.bidr.authorization.dao.entity.AcPartner;
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
 *   <li>白名单闸：按 {@code (partner_id, redirect_uri)} 组合、<b>全串等值</b> ∧ valid='1' 查
 *       {@link AcSsoRedirectService#existsValid}，未命中 ⇒ 拒（文案点名"redirect_uri 未登记"）。
 *       🔴 禁止退化成"只按 redirect_uri 全局查"——组合判据是 10-14 用户改拍已定案；</li>
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
     * 白名单闸：(partner_id, redirect_uri) 组合全串等值 ∧ valid='1'，core 自有表 ac_sso_redirect。
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
     * @param redirectUri 回调地址（必须已登记在该 app_id 所属 partner 名下，全串等值）
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

        // 3) 白名单闸：(partner_id, redirect_uri) 组合全串等值 ∧ valid='1'；原串整体入查，不截断不比较前缀
        boolean whitelisted = acSsoRedirectService.existsValid(partner.getId(), redirectUri);
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
}
