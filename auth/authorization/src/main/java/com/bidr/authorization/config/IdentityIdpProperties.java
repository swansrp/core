package com.bidr.authorization.config;

import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Title: IdentityIdpProperties
 * Description: identity.idp.{private-key,public-key,token-ttl-seconds,code-ttl-seconds}——
 * 产品内部 IdP（OAuth2 授权码三端点 /web/oauth2/**）的签发钥匙与时效配置（T1.2 棒 6 纯加法）。
 *
 * <p>键名前缀零客户名：客户标识只出现在部署档 yml 的值里，代码与键名不出现（同
 * {@link ClientProperties} 纪律）。yml 绑定形态（🔴 必须<b>平铺键</b>，不得嵌套缩进——
 * inkhub-common 的 {@code SsoConfigSurfaceConventionTest} 负向腿把任何 {@code public-key:}
 * 缩进行按出站验票公钥误报，平铺 {@code identity.idp.public-key:} 不落其正则；同理四档 yml
 * 严禁再出现 {@code ttl-seconds: <数字>} 字面量——守卫对全档 ttl 计数恰为 4，本类 Java 默认值
 * 承载 3600/300，yml 值面只放钥匙）：</p>
 * <pre>
 * identity.idp.private-key: &lt;PKCS#8 DER 的 base64（容忍 PEM 头尾与空白）&gt;
 * identity.idp.public-key:  &lt;X.509 DER 的 base64（容忍 PEM 头尾与空白）&gt;
 * </pre>
 *
 * <p>未配置语义（任务书拍板）：启动 {@code log.warn} ＋ 三端点对签发/JWKS 一律 503（demo 形态
 * 钥匙由部署配置给，core 不硬编码钥匙）。公钥可留空——RS256 私钥（PKCS#8 CRT 形态）自带
 * 模数与公钥指数，{@link IdpJwtUtil#derivePublicKeyBase64} 现场派生（SsoJwtUtil
 * verifyAndGetUsernameByPrivateKey 同款口径）；部署方显式给了 public-key 时以其为准。
 * token/code 存活秒数有 Java 默认（3600/300），yml 可覆盖。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.idp")
public class IdentityIdpProperties {

    /** access_token 签发私钥（PKCS#8 DER 的 base64）；空默认＝IdP 端点默认关闭（503） */
    private String privateKey = "";

    /** JWKS 公布/验签公钥（X.509 DER 的 base64）；空默认＝从私钥现场派生（CRT 形态可派生） */
    private String publicKey = "";

    /** access_token 存活秒数（exp = iat + ttl）；Java 默认 3600，yml 可覆盖（core 不再兜底二次默认） */
    private long tokenTtlSeconds = 3600L;

    /** 授权码存活秒数（单次有效）；Java 默认 300＝OAuth2 授权码常规 5 分钟 */
    private long codeTtlSeconds = 300L;

    /**
     * 启动期自检：钥匙缺席只 warn 不阻断上下文——IdP 是纯加法腿（SSO 出票、普通登录链不受影响），
     * 阻断启动会把"未对接形态"误升成"整站起不来"。
     */
    @PostConstruct
    public void warnIfUnconfigured() {
        if (StringUtils.isBlank(privateKey)) {
            log.warn("[identity-idp] identity.idp.private-key 未配置——/web/oauth2 授权码与 token 签发端点"
                    + "保持关闭（调用得 503），JWKS 端点 503；不影响其余登录链");
        } else if (StringUtils.isBlank(publicKey)) {
            log.info("[identity-idp] identity.idp.public-key 未配置——JWKS/验签公钥从私钥现场派生（PKCS#8 CRT 形态）");
        }
    }

    /** 签发/JWKS 可用判据：私钥在场即视为已配置（公钥可派生） */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(privateKey);
    }

    /**
     * 验签/JWKS 用的公钥配置值：显式 public-key 优先，缺省从私钥派生（派生失败＝配置坏，返回空串
     * 由调用方按未配置收敛为 503/拒绝，不在配置层抛）。
     */
    public String resolvedPublicKey() {
        if (StringUtils.isNotBlank(publicKey)) {
            return publicKey;
        }
        if (StringUtils.isBlank(privateKey)) {
            return "";
        }
        try {
            return IdpJwtUtil.derivePublicKeyBase64(privateKey);
        } catch (Exception e) {
            log.warn("[identity-idp] 公钥从私钥派生失败，按未配置收敛: {}", e.getClass().getSimpleName());
            return "";
        }
    }
}
