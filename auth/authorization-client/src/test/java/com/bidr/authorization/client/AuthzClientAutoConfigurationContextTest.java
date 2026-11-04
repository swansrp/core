package com.bidr.authorization.client;

import com.bidr.authorization.authz.AuthzChecker;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Title: AuthzClientAutoConfigurationContextTest
 * Description: 修复轮（review 棒1 I-1/棒4 S-1）双 AuthzChecker 歧义的装配腿——
 * remoteAuthzChecker 已加 {@code @ConditionalOnMissingBean(AuthzChecker.class)}：
 * <ol>
 *   <li>容器内已有 AuthzChecker（模拟组件扫描的 LocalAuthzChecker @Component——client 禁依赖
 *       authorization server，故以同 SPI 匿名 bean 替形）时 remoteAuthzChecker 不注册，
 *       AuthAdmin 的 getIfAvailable() 单候选不抛 NoUniqueBeanDefinitionException；</li>
 *   <li>无本地 checker（远端宿主形态）时 remoteAuthzChecker 正常注册——enabled 开关语义不变。</li>
 * </ol>
 * {@link ApplicationContextRunner} 中 withBean 的注册先于 refresh（@Bean 方法上的
 * {@code @ConditionalOnMissingBean} 在 ConfigurationClassPostProcessor 求值时可见）——与生产
 * 「组件扫描先于 auto-config 装配处理」的退位语义同构。
 *
 * @author coder (修复轮1, 2026-11-03)
 * @since 2026-11-03
 */
public class AuthzClientAutoConfigurationContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("my.authz.client.enabled=true")
            .withUserConfiguration(AuthzClientAutoConfiguration.class);

    /** 腿①：本地 AuthzChecker 在场 → remoteAuthzChecker 退位（防双 bean 歧义） */
    @Test
    public void 本地AuthzChecker在场_remoteAuthzChecker退位() {
        runner.withBean("localAuthzChecker", AuthzChecker.class, () -> Mockito.mock(AuthzChecker.class))
                .run((ContextConsumer<AssertableApplicationContext>) context -> {
                    assertFalse("本地 checker 在场时 remoteAuthzChecker 必须退位（NoUniqueBeanDefinitionException 防线）",
                            context.containsBean("remoteAuthzChecker"));
                    assertEquals("容器内 AuthzChecker 候选唯一（本地判赢）", 1,
                            context.getBeansOfType(AuthzChecker.class).size());
                    assertTrue("其余两件装配不受影响", context.containsBean("authzCache"));
                });
    }

    /** 腿②：无本地 checker（远端宿主形态）→ remoteAuthzChecker 正常注册 */
    @Test
    public void 无本地checker_remoteAuthzChecker注册() {
        runner.run((ContextConsumer<AssertableApplicationContext>) context -> {
            assertTrue("远端宿主形态 remoteAuthzChecker 应注册",
                    context.containsBean("remoteAuthzChecker"));
            assertTrue("注册的候选即 RemoteAuthzChecker",
                    context.getBean("remoteAuthzChecker") instanceof RemoteAuthzChecker);
        });
    }
}
