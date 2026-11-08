package com.bidr.authorization.client.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: OAuth2ClientAutoConfiguration
 * Description: IdP OAuth2 授权码客户端装配（T3.1 棒0 新增；对照 IdpBearerAutoConfiguration 先例：
 * 默认不装配，显式开启才生效——宿主不配置零影响）。
 *
 * <p>装 {@link OAuth2Client}＋{@link PartnerCredentialProvider} 两件（棒4 起同条件追加后者：
 * 开放口机器凭据腿与 OAuth2 客户端共用同一组 ac_partner 三件套，开关一体）；验票腿
 * {@link IdpTokenVerifier} 由 {@link IdpBearerAutoConfiguration} 按 {@code my.idp.bearer.enabled}
 * 装配（两条腿配置键不同、开关独立）。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@Configuration
@ConditionalOnProperty(prefix = "my.oauth2.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OAuth2ClientAutoConfiguration {

    @Bean
    public OAuth2Client oauth2Client(OAuth2ClientProperties properties) {
        return new OAuth2Client(properties);
    }

    /** 开放口机器凭据腿（方案B 棒4）：场景客户端按 {@code OpenApiAuthProvider} 接口注入消费 */
    @Bean
    public PartnerCredentialProvider partnerCredentialProvider(OAuth2ClientProperties properties) {
        return new PartnerCredentialProvider(properties);
    }
}
