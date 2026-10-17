package com.bidr.agent.runtime.config;

import com.bidr.agent.runtime.client.AgentRuntimeRestClient;
import com.bidr.agent.runtime.client.AgentRuntimeSseClient;
import com.bidr.agent.runtime.provider.AgentSystemProvider;
import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.agent.runtime.provider.openhands.OpenHandsProvider;
import com.bidr.agent.runtime.service.AgentChatSessionService;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: AgentRuntimeAutoConfigurationTest
 * Description: 装配行为验证——重点是**组件扫描与自动装配同包并存时不得双注册**
 * （本模块的配置类位于组件扫描根 com.bidr 之下，与 core/llm 同款形态）：
 * <ul>
 * <li>扫描 + 自动装配并存 → 单例 Bean；</li>
 * <li>默认（不配置）→ <b>不装配</b>：框架模块须消费项目显式开启；</li>
 * <li>{@code my.agent.runtime.enabled=false} → 不装配；</li>
 * <li>配置键按前缀正确绑定。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/22
 */
class AgentRuntimeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class))
            .withUserConfiguration(ScannedConfig.class)
            // 框架模块默认不装配（下沉 core 时翻的默认值），故基线用例须显式开启
            .withPropertyValues("my.agent.runtime.enabled=true")
            // 会话服务依赖本模块自带的 DAO（需要数据源）：此处以 mock 顶替，聚焦装配行为本身
            .withBean("agentChatSessionService", AgentChatSessionService.class, () -> Mockito.mock(AgentChatSessionService.class));

    /**
     * 模拟业务应用：组件扫描根与本模块配置类同包（BaseApplication 扫描 com.bidr）。
     * 只扫本模块的 config 包——DAO/Schema 属表代码，扫描它们需要数据源与事务管理器，与本用例目标无关。
     */
    @Configuration
    @ComponentScan("com.bidr.agent.runtime.config")
    static class ScannedConfig {
    }

    @Test
    @DisplayName("扫描与自动装配并存：配置与 Provider 均单例")
    void assemblesSingleBeanWithComponentScan() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentRuntimeProperties.class);
            assertThat(context).hasSingleBean(AgentRuntimeConfigProvider.class);
        });
    }

    @Test
    @DisplayName("enabled=false：整体不装配")
    void disabledBySwitch() {
        runner.withPropertyValues("my.agent.runtime.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AgentRuntimeConfigProvider.class));
    }

    @Test
    @DisplayName("不配置 enabled：框架默认即不装配（消费项目须显式开启）")
    void disabledByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class))
                .withUserConfiguration(ScannedConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentRuntimeConfigProvider.class);
                    assertThat(context).doesNotHaveBean(AgentRuntimeProperties.class);
                });
    }

    @Test
    @DisplayName("配置按键前缀绑定到 Provider")
    void bindsPropertiesByPrefix() {
        runner.withPropertyValues(
                        "my.agent.runtime.base-url=http://param-host:8100",
                        "my.agent.runtime.api-key=sk-rt-integration-test",
                        "my.agent.runtime.api-prefix=/open/v2")
                .run(context -> {
                    AgentRuntimeConfigProvider provider = context.getBean(AgentRuntimeConfigProvider.class);
                    assertThat(provider.getBaseUrl()).isEqualTo("http://param-host:8100");
                    assertThat(provider.getApiPrefix()).isEqualTo("/open/v2");
                    assertThat(provider.isConfigured()).isTrue();
                });
    }

    @Test
    @DisplayName("缺省上游 = agent-system（含其 REST/SSE 客户端）")
    void defaultsToAgentSystemProvider() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentRuntimeProvider.class);
            assertThat(context.getBean(AgentRuntimeProvider.class))
                    .isInstanceOf(AgentSystemProvider.class);
            assertThat(context).hasSingleBean(AgentRuntimeRestClient.class);
            assertThat(context).hasSingleBean(AgentRuntimeSseClient.class);
            assertThat(context).doesNotHaveBean(OpenHandsClient.class);
        });
    }

    @Test
    @DisplayName("provider=openhands：换实现且不再装配 agent-system 的客户端")
    void switchesToOpenHandsProvider() {
        runner.withPropertyValues("my.agent.runtime.provider=openhands")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentRuntimeProvider.class);
                    AgentRuntimeProvider provider = context.getBean(AgentRuntimeProvider.class);
                    assertThat(provider).isInstanceOf(OpenHandsProvider.class);
                    assertThat(provider.name()).isEqualTo("openhands");
                    assertThat(provider.supportsPresignedUpload()).isFalse();
                    assertThat(context).hasSingleBean(OpenHandsClient.class);
                    assertThat(context).doesNotHaveBean(AgentRuntimeRestClient.class);
                    assertThat(context).doesNotHaveBean(AgentRuntimeSseClient.class);
                });
    }
}