package com.bidr.authorization.client.login;

import com.bidr.authorization.client.oauth2.IdpPrincipal;
import com.bidr.authorization.client.oauth2.IdpTokenVerifier;
import com.bidr.authorization.client.oauth2.OAuth2Client;
import com.bidr.authorization.client.oauth2.OAuth2ExchangeException;
import com.bidr.authorization.client.oauth2.OAuth2TokenRes;
import com.bidr.authorization.client.session.SubsystemSessionService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Title: SubsystemLoginService
 * Description: 子系统「找主系统登录」的共享编排（T3.1 自 epc 的 EpcAiLoginService 上提）——
 * 登录页账密由子系统后端凭机器凭证问主系统验身换 IdP 票，随后与 SSO 票腿走同一条
 * <b>验签 → 登录后同步 SPI → 自签本地会话</b>链。
 *
 * <p>两条腿：</p>
 * <ul>
 *   <li>{@link #loginByPassword}（子系统 {@code /web/login}）：{@link OAuth2Client#exchangePassword}
 *       透传账密到主系统 open-api；业务失败文案剥技术前缀后原样透出；</li>
 *   <li>{@link #loginByIdToken}（SSO 票腿）：只接 IdP 票；票缺失＝调用方没走 SSO 登录，
 *       回墓碑文案「统一使用主系统账号登录」引导回主系统。</li>
 * </ul>
 *
 * <p>🔴 机器凭证缺失（{@link OAuth2Client} 未装配，{@code my.oauth2.client.enabled} 未开）或
 * 验票器缺失（{@link IdpTokenVerifier} 未装配，{@code my.idp.bearer.enabled} 未开）时保持原墓碑
 * 语义：抛 {@link #RETIRED_MESSAGE}，绝不静默降级成本地凭据面（子系统没有 ac_* 表）。</p>
 *
 * <p>🔴 不设 {@code IdmHolder}：client 无 idm 依赖；本地会话由
 * {@link SubsystemSessionService#login(String)} 显式收登录名，与 epc 原实现行为等价。
 * 宿主业务同步（租户/项目镜像）经 {@link SubsystemLoginSyncHook} 注入，best-effort 不阻断登录。</p>
 *
 * @author coder (棒2, 子系统登录上提 2026-11-17)
 * @since 2026-11-17
 */
@Slf4j
@RequiredArgsConstructor
public class SubsystemLoginService {

    /**
     * 墓碑文案：与 {@code RetiredCredentialController} 逐字一致（子系统无本地凭据面时唯一的业务指引）。
     */
    public static final String RETIRED_MESSAGE = "统一使用主系统账号登录";

    /** IdP OAuth2 客户端（密码透传腿；{@code my.oauth2.client.enabled} 未开时缺失） */
    private final ObjectProvider<OAuth2Client> oauth2Client;
    /** IdP access_token 验签器（{@code my.idp.bearer.enabled} 未开时缺失） */
    private final ObjectProvider<IdpTokenVerifier> idpTokenVerifier;
    /** 本地会话共享编排（镜像闸 → 租户闸 → 双票签发） */
    private final SubsystemSessionService sessionService;
    /** 登录后同步 SPI（宿主业务面，best-effort） */
    private final ObjectProvider<SubsystemLoginSyncHook> syncHook;

    /**
     * 账密登录（子系统 {@code /web/login} 腿）：凭机器凭证问主系统验身 → 验票 → 登录后同步 → 本地会话。
     *
     * @param loginId  用户标识（用户名/手机号/邮箱；主系统登录同口径）
     * @param password 密码（前端已 MD5 形态，与主系统登录同口径）
     * @return 本地会话（accessToken/refreshToken/name/customerNumber）
     */
    public LoginRes loginByPassword(String loginId, String password) {
        if (StringUtils.isBlank(loginId) || StringUtils.isBlank(password)) {
            throw new ServiceException(ErrCodeSys.PA_PARAM_FORMAT, "loginId");
        }
        OAuth2Client client = oauth2Client.getIfAvailable();
        if (client == null) {
            log.warn("[子系统登录]账密腿请求但 OAuth2Client 未装配（my.oauth2.client.enabled 未开），保持墓碑");
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, RETIRED_MESSAGE);
        }
        OAuth2TokenRes token;
        try {
            token = client.exchangePassword(loginId, password);
        } catch (OAuth2ExchangeException e) {
            log.warn("[子系统登录]主系统账密验身失败: {}", e.getMessage());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, userMessage(e.getMessage()));
        }
        return loginByIdpAccessToken(token.getAccessToken());
    }

    /**
     * SSO 票腿：只接 IdP 票（子系统账密走 {@code /web/login}，即 {@link #loginByPassword}）。
     *
     * <p>票缺失（调用方未走 SSO）⇒ 墓碑文案：子系统没有本地凭据面，必须回主系统登录。</p>
     *
     * @param idToken 主系统签发的 IdP 票串（access_token，RS256 JWT）
     * @return 本地会话
     */
    public LoginRes loginByIdToken(String idToken) {
        if (StringUtils.isBlank(idToken)) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, RETIRED_MESSAGE);
        }
        return loginByIdpAccessToken(idToken);
    }

    /**
     * IdP 票换本地会话（两条腿共用）：本地验签（fail-closed）→ 登录后同步 SPI（best-effort）→ 自签会话。
     *
     * @param accessToken IdP access_token（RS256 JWT）
     * @return 本地会话（accessToken/refreshToken/name/customerNumber）
     */
    public LoginRes loginByIdpAccessToken(String accessToken) {
        IdpTokenVerifier verifier = idpTokenVerifier.getIfAvailable();
        if (verifier == null) {
            log.warn("[子系统登录]IdP 票腿请求但 IdpTokenVerifier 未装配（my.idp.bearer.enabled 未开），保持墓碑");
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, RETIRED_MESSAGE);
        }
        IdpPrincipal principal = verifier.verify(accessToken);
        if (principal == null) {
            log.warn("[子系统登录]IdP token 本地验签失败，拒绝发会话");
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "墨枢票验签失败或已过期，请重新登录");
        }
        invokeSyncHook(principal, accessToken);
        return sessionService.login(principal.getUsername());
    }

    /**
     * 登录后同步回调（best-effort）：宿主实现异常只记 WARN，登录链继续。
     */
    private void invokeSyncHook(IdpPrincipal principal, String accessToken) {
        SubsystemLoginSyncHook hook = syncHook.getIfAvailable();
        if (hook == null) {
            return;
        }
        try {
            hook.afterIdpVerified(principal, accessToken);
        } catch (RuntimeException e) {
            // 同步属业务便利面：失败不阻断存量账号登录，新账号由登录闸在会话签发前明确拒绝
            log.warn("[子系统登录]登录后同步 hook 执行失败（best-effort，不阻断登录）: username={} err={}",
                    principal.getUsername(), e.getMessage());
        }
    }

    /**
     * 剥离 {@link OAuth2ExchangeException} 文案的技术前缀（{@code "IdP token 端点拒绝: xxx - <业务文案>"}），
     * 只留人话（与 epc 原实现逐字一致）。
     *
     * @param message 原始异常消息（可 null）
     * @return 用户可读文案
     */
    private static String userMessage(String message) {
        if (message == null) {
            return "登录失败";
        }
        int idx = message.lastIndexOf(" - ");
        return idx >= 0 ? message.substring(idx + 3) : message;
    }
}