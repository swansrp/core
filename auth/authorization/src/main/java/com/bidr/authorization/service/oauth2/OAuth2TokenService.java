package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: OAuth2TokenService
 * Description: 授权码换 token 编排（T1.2 棒 6 纯加法）——RFC 6749 §4.1.3 判定序：
 * grant_type 形态 → client 三闸（复用 {@link OAuth2ClientService}，secret 恒时比较）→
 * code 消费（单次有效＋过期失效，{@link OAuth2AuthorizationCodeService}）→
 * code 绑定面回验（client_id/redirect_uri 与签发时同串）→ RS256 JWT 签发。
 *
 * <p>签发 claim 形态（任务书定稿）：{@code {iss:"inkhub", sub:<工号>, uid:<user_id>,
 * username:<登录名>, name:<展示名>, tenant_ids:[绑定租户 id], app_id:<client_id>,
 * iat, exp}}，exp＝iat + identity.idp.token-ttl-seconds（默认 3600）。响应标准形态
 * {@code {access_token, token_type:"Bearer", expires_in}}；refresh_token 本棒不做
 * （demo 无需，任务书无要求——留 P2 记录）。</p>
 *
 * <p>错误全部抛 {@link OAuth2TokenException}（RFC 6749 §5.2 错误码），controller 转标准
 * 400 JSON——不走框架业务异常全局形状，消费方按标准 OAuth2 形态解析。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    /** grant_type：本端点只认授权码腿（refresh/implicit 等不在本棒范围） */
    public static final String GRANT_AUTHORIZATION_CODE = "authorization_code";

    /** iss 固定值（本产品内部 IdP 的签发方标识，任务书定稿） */
    public static final String ISSUER = "inkhub";

    private final OAuth2ClientService clientService;
    private final OAuth2AuthorizationCodeService codeService;
    private final IdentityIdpProperties idpProperties;

    /**
     * grant_type=authorization_code 全编排：校验全过 ⇒ 签发并返回标准响应体；
     * 任一步失败 ⇒ {@link OAuth2TokenException}（标准错误码＋根因文案）。
     *
     * @param grantType    form 参数 grant_type（必须 authorization_code）
     * @param code         form 参数 code
     * @param clientId     form 参数 client_id
     * @param clientSecret form 参数 client_secret
     * @param redirectUri  form 参数 redirect_uri（必须与签发时同串）
     * @return 标准响应体 {access_token, token_type:"Bearer", expires_in}
     */
    public Map<String, Object> grantByAuthorizationCode(String grantType, String code,
                                                        String clientId, String clientSecret,
                                                        String redirectUri) {
        if (!GRANT_AUTHORIZATION_CODE.equals(grantType)) {
            throw new OAuth2TokenException("unsupported_grant_type",
                    "grant_type 仅支持 authorization_code");
        }
        if (StringUtils.isBlank(code)) {
            throw new OAuth2TokenException("invalid_request", "code 缺失");
        }
        if (StringUtils.isBlank(clientId)) {
            throw new OAuth2TokenException("invalid_request", "client_id 缺失");
        }

        // 1) client 闸：登记＋secret 恒时比较（错误码 invalid_client，RFC 6749 §5.2）
        AcPartner partner = clientService.loadPartner(clientId);
        if (partner == null) {
            throw new OAuth2TokenException("invalid_client", "client_id 未登记，拒绝授权");
        }
        try {
            clientService.checkClientSecret(partner, clientSecret);
        } catch (Exception e) {
            throw new OAuth2TokenException("invalid_client", "client_secret 校验失败，拒绝授权");
        }

        // 2) code 消费（单次有效＋过期失效；错误码 invalid_grant）
        OAuth2AuthorizationCode context = codeService.consume(code);
        if (context == null) {
            throw new OAuth2TokenException("invalid_grant", "授权码无效、已过期或已使用");
        }

        // 3) code 绑定面回验：client_id 与 redirect_uri 必须与签发时同串（RFC 6749 §4.1.3）
        if (!clientId.trim().equals(context.getClientId())) {
            throw new OAuth2TokenException("invalid_grant", "授权码与 client_id 不匹配");
        }
        if (redirectUri == null || !redirectUri.trim().equals(context.getRedirectUri())) {
            throw new OAuth2TokenException("invalid_grant", "redirect_uri 与授权码签发时不一致");
        }

        // 4) redirect 白名单再闸（code 绑定串已闸过一次；直连 client 闸漏过"登记但停用"的形态）
        try {
            clientService.checkRedirectUriRegistered(partner, context.getRedirectUri());
        } catch (Exception e) {
            throw new OAuth2TokenException("invalid_grant", "redirect_uri 未登记（回调白名单已变动）");
        }

        return buildTokenResponse(context);
    }

    /**
     * 签发 RS256 JWT access_token ＋ 标准响应体。exp 只算一次（iat/exp 同源读一次时钟，
     * SsoJwtUtil G-8 同款纪律——expires_in 与票内 exp 严格对齐）。
     */
    private Map<String, Object> buildTokenResponse(OAuth2AuthorizationCode context) {
        String publicKey = idpProperties.resolvedPublicKey();
        if (!idpProperties.isConfigured() || StringUtils.isBlank(publicKey)) {
            throw new OAuth2TokenException("server_error",
                    "identity.idp 签发钥匙未配置，token 端点不可用");
        }
        long now = System.currentTimeMillis() / 1000L;
        long ttl = idpProperties.getTokenTtlSeconds();
        Map<String, Object> claims = new LinkedHashMap<>(12);
        claims.put(IdpJwtUtil.CLAIM_ISS, ISSUER);
        claims.put(IdpJwtUtil.CLAIM_SUB, context.getCustomerNumber());
        claims.put(IdpJwtUtil.CLAIM_UID, context.getUserId());
        claims.put(IdpJwtUtil.CLAIM_USERNAME, context.getUserName());
        claims.put(IdpJwtUtil.CLAIM_NAME, context.getName());
        claims.put(IdpJwtUtil.CLAIM_TENANT_IDS,
                context.getTenantIds() == null ? new ArrayList<Long>(0) : context.getTenantIds());
        claims.put(IdpJwtUtil.CLAIM_APP_ID, context.getClientId());
        claims.put(IdpJwtUtil.CLAIM_IAT, now);
        claims.put(IdpJwtUtil.CLAIM_EXP, now + ttl);
        String accessToken = IdpJwtUtil.sign(idpProperties.getPrivateKey(),
                IdpJwtUtil.computeKid(publicKey), claims);

        Map<String, Object> res = new LinkedHashMap<>(6);
        res.put("access_token", accessToken);
        res.put("token_type", "Bearer");
        res.put("expires_in", ttl);
        if (StringUtils.isNotBlank(context.getScope())) {
            res.put("scope", context.getScope());
        }
        log.info("[IdP出票]client={}, user={}, ttl={}s", context.getClientId(),
                context.getCustomerNumber(), ttl);
        return res;
    }
}
