package com.bidr.authorization.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: AuthzClientAutoConfiguration
 * Description: client starter 装配入口（对照 agent-runtime 先例：默认不装配，显式开启才生效）。
 * <p>
 * 子系统接入三行 yml：
 * <pre>
 * my:
 *   authz:
 *     client:
 *       enabled: true
 *       server-base-url: http://hub:8080
 *       fail-mode: CLOSED
 * </pre>
 * T2.3b 在此注册 HttpAuthzCheckApi（实现 {@link AuthzCheckApi}）与缓存 Bean。
 *
 * @author Sharp
 * @since 2026-10-31
 */
@Configuration
@ConditionalOnProperty(prefix = "my.authz.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuthzClientProperties.class)
public class AuthzClientAutoConfiguration {

    @Bean
    public AuthzCache authzCache() {
        return new AuthzCache();
    }
}
