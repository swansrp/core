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
 *       app-key: xxx
 *       app-secret: yyy
 *       fail-mode: CLOSED
 *       version-refresh-ms: 5000
 * </pre>
 * T2.3b 装配三件：缓存 Bean ＋ {@link HttpAuthzCheckApi}（实现 {@link AuthzCheckApi}，
 * /authz 三端点 HTTP 面）＋ {@link RemoteAuthzChecker}（实现 api
 * {@code AuthzChecker}，AuthAdmin 端点 admin 门槛经 ObjectProvider 消费）。
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

    @Bean
    public HttpAuthzCheckApi httpAuthzCheckApi(AuthzClientProperties properties) {
        return new HttpAuthzCheckApi(properties);
    }

    @Bean
    public RemoteAuthzChecker remoteAuthzChecker(AuthzCheckApi authzCheckApi,
                                                 AuthzCache authzCache,
                                                 AuthzClientProperties properties) {
        return new RemoteAuthzChecker(authzCheckApi, authzCache, properties);
    }
}
