package com.bidr.authorization.client.oauth2;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: IdpBearerAutoConfiguration
 * Description: IdP Bearer 验签腿装配（对照 AuthzClientAutoConfiguration 先例：
 * default 不装配，显式开启才生效——宿主不配置零影响）。
 *
 * <p>T3.1 棒0 起同时装配 {@link IdpTokenVerifier}（IdP access_token 验签腿）：同一开关、同一取钥器。
 * 2026-11-04 起取钥走 {@link JwksKeyProvider}（{@code server-base-url} 动态拉 JWKS，
 * {@code public-key-base64} 静态兜底）。</p>
 *
 * <p>🔴 <b>fail-fast</b>（2026-11-04 加）：{@code enabled=true} 但**既无 server-base-url 也无静态公钥**
 * 时启动直接失败——此前的行为是 bean 装配成功、{@code verify} 恒拒，表现为"登录莫名其妙 401 且
 * 日志指向不明"。现在把配置缺失挡在启动期，错误信息直接给出该配什么。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03；JWKS 化 + fail-fast 2026-11-04)
 * @since 2026-11-03
 */
@Configuration
@ConditionalOnProperty(prefix = "my.idp.bearer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdpBearerProperties.class)
public class IdpBearerAutoConfiguration {

    @Bean
    public IdpBearerVerifier idpBearerVerifier(IdpBearerProperties properties) {
        assertKeySourceConfigured(properties);
        return new IdpBearerVerifier(properties);
    }

    /**
     * IdP access_token 验签器（T3.1 棒0 补装）：与 Bearer 腿同一开关、同一取钥器。
     */
    @Bean
    public IdpTokenVerifier idpTokenVerifier(IdpBearerProperties properties) {
        assertKeySourceConfigured(properties);
        return new IdpTokenVerifier(properties);
    }

    /**
     * 配置完整性闸：开启验签腿就必须有"钥来源"。
     *
     * <p>两种合法形态：①{@code server-base-url}（推荐——接入方零密钥，公钥运行时从墨枢
     * {@code /web/oauth2/jwks.json} 取，换钥自动跟上）②显式 {@code public-key-base64}
     * （离线/排查时手工搬运）。自 2026-11-04 起 starter **不再内置任何默认公钥**——
     * 墨枢按部署生成密钥对，写死的"标准值"对非标准部署只会造成静默验签失败。</p>
     */
    static void assertKeySourceConfigured(IdpBearerProperties properties) {
        if (properties != null
                && StringUtils.isBlank(properties.getServerBaseUrl())
                && StringUtils.isBlank(properties.getPublicKeyBase64())) {
            throw new IllegalStateException(
                    "my.idp.bearer.enabled=true 但未配置钥来源。请二选一："
                            + "①【推荐】my.idp.bearer.server-base-url=<墨枢根地址>（如 http://10.3.6.60/inkhub）"
                            + "——公钥运行时从 /web/oauth2/jwks.json 拉取，接入方无需任何密钥；"
                            + "②离线/排查场景 my.idp.bearer.public-key-base64=<X.509 公钥 base64>（手工搬运）。");
        }
    }
}