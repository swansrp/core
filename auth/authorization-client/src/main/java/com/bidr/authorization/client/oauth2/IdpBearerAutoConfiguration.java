package com.bidr.authorization.client.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: IdpBearerAutoConfiguration
 * Description: IdP Bearer 验签腿装配（对照 AuthzClientAutoConfiguration 先例：
 * 默认不装配，显式开启才生效——宿主不配置零影响）。公钥缺省未配置时 bean 仍在但
 * {@link IdpBearerVerifier#enabled()} 为 false（verify 恒拒，fail-closed）。
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
@Configuration
@ConditionalOnProperty(prefix = "my.idp.bearer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdpBearerProperties.class)
public class IdpBearerAutoConfiguration {

    @Bean
    public IdpBearerVerifier idpBearerVerifier(IdpBearerProperties properties) {
        return new IdpBearerVerifier(properties);
    }
}
