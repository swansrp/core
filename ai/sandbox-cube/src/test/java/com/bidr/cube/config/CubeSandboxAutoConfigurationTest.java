package com.bidr.cube.config;

import com.bidr.cube.CubeSandboxProvider;
import com.bidr.cube.SandboxJanitor;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Title: CubeSandboxAutoConfigurationTest
 * Description: Cube 沙箱腿装配契约钉桩（ApplicationContextRunner，不起完整宿主）：
 * <ul>
 *     <li>provider=cube＋最小属性（api-url/api-key/template）⇒ 恰一 {@link AgentSandbox} Bean
 *     且 instanceof {@link CubeSandboxProvider}（骨架桩装配面）；provider 大小写变体（CUBE）
 *     同装配——钉住 {@code @ConditionalOnProperty} 的 equalsIgnoreCase 语义；</li>
 *     <li>provider 缺席/空串/openhands ⇒ 容器无 {@link AgentSandbox} Bean（无 matchIfMissing，
 *     互斥退避的另一半：OpenHands 腿照常）；</li>
 *     <li>宿主自建 {@link AgentSandbox} Bean ⇒ cube 腿让位（{@code @ConditionalOnMissingBean}）；</li>
 *     <li>配置键绑定：kebab 键位（data-base-url/host-root/allow-out/admin-timeout/timeout）
 *     与契约缺省值。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/16
 */
class CubeSandboxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CubeSandboxAutoConfiguration.class))
            .withPropertyValues(
                    "my.agent.sandbox.provider=cube",
                    "my.agent.sandbox.cube.api-url=http://127.0.0.1:8080",
                    "my.agent.sandbox.cube.api-key=sk-cube-test",
                    "my.agent.sandbox.cube.template=cube-template");

    @Test
    @DisplayName("provider=cube＋最小属性：恰一 AgentSandbox 且 instanceof CubeSandboxProvider")
    void providerCubeAssemblesSingleStubProvider() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentSandbox.class);
            assertThat(context).hasSingleBean(CubeSandboxProperties.class);
            assertThat(context.getBean(AgentSandbox.class)).isInstanceOf(CubeSandboxProvider.class);
        });
    }

    @Test
    @DisplayName("provider=CUBE（大小写不敏感）：同装配（@ConditionalOnProperty equalsIgnoreCase 语义）")
    void providerValueIsCaseInsensitive() {
        runner.withPropertyValues("my.agent.sandbox.provider=CUBE")
                .run(context -> assertThat(context).hasSingleBean(AgentSandbox.class));
    }

    @Test
    @DisplayName("provider 缺席/空串/openhands：容器无 AgentSandbox Bean（互斥另一半＝OpenHands 腿照常）")
    void providerAbsentOrOtherValueYieldsNoAgentSandbox() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CubeSandboxAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentSandbox.class);
                    assertThat(context).doesNotHaveBean(CubeSandboxProperties.class);
                });
        runner.withPropertyValues("my.agent.sandbox.provider=")
                .run(context -> assertThat(context).doesNotHaveBean(AgentSandbox.class));
        runner.withPropertyValues("my.agent.sandbox.provider=openhands")
                .run(context -> assertThat(context).doesNotHaveBean(AgentSandbox.class));
    }

    @Test
    @DisplayName("宿主自建 AgentSandbox：cube 腿让位（@ConditionalOnMissingBean 生效）")
    void hostProvidedAgentSandboxWins() {
        runner.withBean(AgentSandbox.class, () -> mock(AgentSandbox.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentSandbox.class);
                    assertThat(context.getBean(AgentSandbox.class))
                            .isNotInstanceOf(CubeSandboxProvider.class);
                });
    }

    @Test
    @DisplayName("kebab 键位绑定：data-base-url/host-root/allow-out/admin-timeout/timeout 全落位")
    void propertiesBindKebabKeys() {
        runner.withPropertyValues(
                        "my.agent.sandbox.cube.data-base-url=http://127.0.0.1:9000",
                        "my.agent.sandbox.cube.host-root=/mnt/sandbox",
                        "my.agent.sandbox.cube.allow-out=example.com,example.org",
                        "my.agent.sandbox.cube.admin-timeout=60",
                        "my.agent.sandbox.cube.timeout=30")
                .run(context -> {
                    CubeSandboxProperties props = context.getBean(CubeSandboxProperties.class);
                    assertThat(props.getApiUrl()).isEqualTo("http://127.0.0.1:8080");
                    assertThat(props.getApiKey()).isEqualTo("sk-cube-test");
                    assertThat(props.getTemplate()).isEqualTo("cube-template");
                    assertThat(props.getDataBaseUrl()).isEqualTo("http://127.0.0.1:9000");
                    assertThat(props.getHostRoot()).isEqualTo("/mnt/sandbox");
                    assertThat(props.getAllowOut()).containsExactly("example.com", "example.org");
                    assertThat(props.getAdminTimeout()).isEqualTo(60L);
                    assertThat(props.getTimeout()).isEqualTo(30);
                });
    }

    @Test
    @DisplayName("契约缺省值：hostRoot=/data/sandbox、adminTimeout=86400、timeout=1800、空缺省")
    void propertiesCarryContractDefaults() {
        runner.run(context -> {
            CubeSandboxProperties props = context.getBean(CubeSandboxProperties.class);
            assertThat(props.getDataBaseUrl()).isEmpty();
            assertThat(props.getHostRoot()).isEqualTo("/data/sandbox");
            assertThat(props.getAllowOut()).isEmpty();
            assertThat(props.getAdminTimeout()).isEqualTo(86400L);
            assertThat(props.getTimeout()).isEqualTo(1800);
        });
    }

    // ---------------------------------------------------------------- Janitor 装配（M-3 review 第1轮）

    @Test
    @DisplayName("Janitor 装配①：provider=cube＋最小属性（orphan-cleanup 缺省 true）⇒ SandboxJanitor 在场")
    void janitorAssembledByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SandboxJanitor.class);
            assertThat(context.getBean(SandboxJanitor.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("Janitor 装配②：orphan-cleanup-enabled=false ⇒ 无可用 SandboxJanitor 实例（启动对账整体关闭，provider 腿仍在）")
    void janitorAbsentWhenOrphanCleanupDisabled() {
        runner.withPropertyValues("my.agent.sandbox.cube.orphan-cleanup-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentSandbox.class);
                    // 方法体判定返回 null ⇒ Spring 注册 NullBean；断言实例级（getBeansOfType 会实例化
                    // 并过滤 NullBean，而 doesNotHaveBean 按声明返回类型预测会误报在场）
                    assertThat(context.getBeansOfType(SandboxJanitor.class)).isEmpty();
                });
    }

    @Test
    @DisplayName("Janitor 装配③：宿主自建 AgentSandbox（非 CubeSandboxProvider）⇒ 让位无 SandboxJanitor 实例（无租约可清扫）")
    void janitorAbsentWhenHostProviderWins() {
        runner.withBean(AgentSandbox.class, () -> mock(AgentSandbox.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentSandbox.class);
                    assertThat(context.getBean(AgentSandbox.class))
                            .isNotInstanceOf(CubeSandboxProvider.class);
                    assertThat(context.getBeansOfType(SandboxJanitor.class)).isEmpty();
                });
    }
}
