package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Title: OAuth2ClientService
 * Description: OAuth2 client 注册面校验（T1.2 棒 6 纯加法）——复用既有注册面两张表
 * （M-7：{@code ac_partner} 的 app_key/appSecret ＝ client_id/client_secret；
 * {@code ac_sso_redirect} ＝ redirect_uri 白名单，PartnerSeeder 已种数据），零新表。
 *
 * <p>三闸：
 * <ol>
 *   <li><b>client 闸</b>：按 {@code client_id} 查 {@code ac_partner.app_key}，查不到即拒
 *       （文案点名"client_id 未登记"，不透出后续查询）；</li>
 *   <li><b>secret 闸</b>：{@link MessageDigest#isEqual} <b>恒时比较</b>——逐字节时长一致，
 *       不给逐字符爆破的时序侧信道；</li>
 *   <li><b>redirect 闸</b>：传入串在该 partner 名下 {@code valid='1'} 登记行中<b>全串精确等值</b>
 *       （trim 后比较；🔴 与 SSO 出票面的 origin 等值判据刻意不同源——授权码回调按 RFC 6749 §3.1.2.3
 *       是全串精确匹配，前缀/origin 匹配都会放大子域绕过面）。</li>
 * </ol>
 * 全部以业务拒绝收敛（{@link Validator} ⇒ ServiceException，文案即根因），authorize/token 两个
 * 端点各自决定向外形态（authorize 对 client/redirect 无效不 302，token 转 400 OAuth2 错误 JSON）。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientService {

    private final AcPartnerService acPartnerService;
    private final AcSsoRedirectService acSsoRedirectService;

    /**
     * client 闸：client_id → ac_partner.app_key。查不到返回 null（调用方按各自形态拒，
     * 本方法不抛——authorize 面"未登记"与"登记但停用"文案要分开点名的场景留给调用方）。
     *
     * @param clientId OAuth2 client_id（对应 ac_partner.app_key）
     * @return partner 行；未登记返回 null
     */
    public AcPartner loadPartner(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }
        return acPartnerService.getByAppKey(clientId.trim());
    }

    /**
     * secret 闸：client_secret 与 {@code ac_partner.app_secret} <b>恒时比较</b>。
     *
     * @param partner      partner 行（不得为 null）
     * @param clientSecret 传入密钥（可为 null/空——空串同样走恒时比较后必然不等）
     */
    public void checkClientSecret(AcPartner partner, String clientSecret) {
        String expected = partner.getAppSecret() == null ? "" : partner.getAppSecret();
        String got = clientSecret == null ? "" : clientSecret;
        boolean same = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), got.getBytes(StandardCharsets.UTF_8));
        Validator.assertTrue(same, ErrCodeSys.SYS_ERR_MSG, "client_secret 校验失败，拒绝授权");
    }

    /**
     * redirect 闸：传入串在该 partner 名下 valid='1' 登记行中全串精确等值（trim 后比较）。
     *
     * @param partner     partner 行（不得为 null）
     * @param redirectUri 传入回调地址
     */
    public void checkRedirectUriRegistered(AcPartner partner, String redirectUri) {
        Validator.assertNotBlank(redirectUri, ErrCodeSys.PA_PARAM_FORMAT, "redirect_uri");
        String candidate = redirectUri.trim();
        List<AcSsoRedirect> registered = acSsoRedirectService.listValid(partner.getId());
        boolean whitelisted = registered != null && registered.stream()
                .anyMatch(row -> candidate.equals(row.getRedirectUri() == null
                        ? "" : row.getRedirectUri().trim()));
        Validator.assertTrue(whitelisted, ErrCodeSys.SYS_ERR_MSG,
                "redirect_uri 未登记（不在该应用的回调白名单），拒绝授权");
    }
}
