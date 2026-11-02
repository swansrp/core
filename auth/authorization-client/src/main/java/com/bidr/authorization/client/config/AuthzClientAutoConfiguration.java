package com.bidr.authorization.client;

import com.bidr.authorization.authz.AuthzChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    /**
     * 修复轮（review 棒1 I-1/棒4 S-1）：{@code @ConditionalOnMissingBean(AuthzChecker.class)}——
     * 容器内已有任何 AuthzChecker 候选（如组件扫描的 LocalAuthzChecker）时自动退位，防双候选致
     * AuthAdmin 的 {@code ObjectProvider.getIfAvailable()} 抛 NoUniqueBeanDefinitionException。
     * 🔴 赢者取决于宿主实际扫描面（review 第 2 轮 N-I-1）：凡默认扫 {@code com.bidr} 的宿主
     * （含 epc）LocalAuthzChecker 都在场且必赢——此时开 {@code my.authz.client.enabled=true}
     * 得到的是<b>本地判定</b>而非 /authz 远端判定。T2.4b 接线时三选一定案：扫描面排除
     * authorization 包／不开 client／接受本地判定并补齐本地 schema。
     */
    @Bean
    @ConditionalOnMissingBean(AuthzChecker.class)
    public RemoteAuthzChecker remoteAuthzChecker(AuthzCheckApi authzCheckApi,
                                                 AuthzCache authzCache,
                                                 AuthzClientProperties properties) {
        return new RemoteAuthzChecker(authzCheckApi, authzCache, properties);
    }
}
