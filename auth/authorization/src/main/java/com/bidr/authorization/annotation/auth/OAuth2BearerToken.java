package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: OAuth2BearerToken
 * Description: open-api 的 Bearer 鉴权腿（T1.2 棒 6 纯加法）——{@link AuthRole} 形态挂在
 * {@code @Auth(OAuth2BearerToken.class)} 端点上，{@code AuthInterceptor.validateAuth} 既有
 * 分派机制零改动消费（bean 名必须 {@code "oAuth2BearerToken"}＝
 * {@code StringUtils.uncapitalize(简名)} 的查找形态；🔴 不能用 @Component 默认名——
 * {@code Introspector.decapitalize} 对 "OA" 双大写开头不降首字母，默认名
 * {@code "OAuth2BearerToken"} 与拦截器查找名不一致会 NoSuchBeanDefinition）。
 *
 * <p><b>双轨语义</b>（X-App-Key 轨并存待拍板项，任务书备注已记）：请求头
 * {@code Authorization: Bearer <jwt>} <b>在场才走本轨</b>——头缺席即静默放行
 * （端点内的 X-App-Key 校验照旧裁决，与现有行为完全兼容）；头<b>在场但票无效</b>则
 * 业务拒绝（绝不静默回落 X-App-Key 轨——fail-closed，调用方显式声明了要走 JWT 轨）。</p>
 *
 * <p><b>校验序</b>：抽头 → 验签（JWKS 同钥＝identity.idp 公钥，公钥缺省从私钥派生）→
 * exp 自判（{@link IdpJwtUtil#verify} 内含 30 秒偏移容忍）→ 构造
 * {@link AccountInfo}（claims 映射：uid→userId、sub→customerNumber、username→userName、
 * name→name、tenant_ids→tenantIds）置 {@link AccountContext}＋{@link TenantContext}
 * （{@code AuthLogin.buildContext} 同款接线——数据面租户闸依赖 TenantContext 就位；
 * 当前租户键不置＝未选态，与老 token 口径一致；角色面留空＝Bearer 票不携带角色，
 * 管理闸端点不适用本轨）。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Component("oAuth2BearerToken")
@RequiredArgsConstructor
public class OAuth2BearerToken implements AuthRole {

    /** RFC 6750：Authorization 头 scheme 前缀（大小写不敏感） */
    private static final String BEARER_PREFIX = "bearer ";

    private final IdentityIdpProperties idpProperties;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        String header = request.getHeader("Authorization");
        if (StringUtils.isBlank(header)) {
            // 双轨：无 Bearer 头＝走端点既有 X-App-Key 轨，本轨静默放行
            return;
        }
        String token = header.trim();
        if (token.length() <= BEARER_PREFIX.length()
                || !token.toLowerCase(java.util.Locale.ROOT).startsWith(BEARER_PREFIX)) {
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                    "Authorization 头形态非法（须为 Bearer <access_token>）");
        }
        String jwt = token.substring(BEARER_PREFIX.length()).trim();

        String publicKey = idpProperties.resolvedPublicKey();
        if (!idpProperties.isConfigured() || StringUtils.isBlank(publicKey)) {
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                    "identity.idp 验签公钥未配置，Bearer 轨不可用");
        }
        Map<String, Object> claims = IdpJwtUtil.verify(jwt, publicKey);
        if (claims == null) {
            // 验签失败/过期/脏串统一拒（验票面只 WARN 不回显细节，IdpJwtUtil 已打点）
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                    "access_token 无效或已过期，拒绝访问");
        }
        AccountContext.set(buildAccount(claims));
        TenantContext.set(parseTenantIds(claims.get(IdpJwtUtil.CLAIM_TENANT_IDS)));
    }

    /** claims ⇒ AccountInfo（AuthLogin.buildContext 形态的 JWT 版；角色面留空） */
    private AccountInfo buildAccount(Map<String, Object> claims) {
        AccountInfo account = new AccountInfo();
        account.setUserId(toLong(claims.get(IdpJwtUtil.CLAIM_UID)));
        account.setCustomerNumber(asString(claims.get(IdpJwtUtil.CLAIM_SUB)));
        account.setUserName(asString(claims.get(IdpJwtUtil.CLAIM_USERNAME)));
        account.setName(asString(claims.get(IdpJwtUtil.CLAIM_NAME)));
        return account;
    }

    /** tenant_ids claim ⇒ List&lt;Long&gt;（数字/字符串元素兼容，脏元素跳过——AuthLogin.parseTenantIds 同口径） */
    private static List<Long> parseTenantIds(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<Long> tenantIds = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            Long id = toLong(item);
            if (id != null) {
                tenantIds.add(id);
            }
        }
        return tenantIds;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
