package com.bidr.agent.runtime.config;

import com.bidr.agent.runtime.client.AgentRuntimeRestClient;
import com.bidr.agent.runtime.client.AgentRuntimeSseClient;
import com.bidr.agent.runtime.dao.repository.ChatSessionService;
import com.bidr.agent.runtime.provider.AgentSystemProvider;
import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.agent.runtime.provider.openhands.OpenHandsProvider;
import com.bidr.agent.runtime.relay.AgentStreamRelay;
import com.bidr.agent.runtime.service.AgentChatSessionService;
import com.bidr.agent.runtime.spi.AgentRequestContext;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.agent.runtime.spi.DefaultAgentRequestContext;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Title: AgentRuntimeAutoConfiguration
 * Description: Agent 接入的默认装配（开箱即用，业务可覆盖）——以 Spring Boot 自动配置形式注册
 * （见 {@code META-INF/spring/...AutoConfiguration.imports}），在用户 Bean 之后处理，配合
 * {@link ConditionalOnMissingBean} 保证「业务自定义即覆盖默认」的顺序可靠。
 * <p>
 * 分层：
 * <ul>
 * <li>配置出口 {@link AgentRuntimeConfigProvider}：系统参数（{@code AGENT_RUNTIME_*}）优先、
 * 应用配置 {@code my.agent.runtime.*} 回落；</li>
 * <li><b>上游实现按 {@code my.agent.runtime.provider} 选</b>：各 provider 的 Bean 方法各自带
 * {@code @ConditionalOnProperty}，二者都实现 {@link AgentRuntimeProvider}，
 * 对上只暴露「我们的 DTO + §5.7 形状帧」；</li>
 * <li>{@link AgentStreamRelay} / {@link AgentChatSessionService} / relay 控制器都不感知上游种类；</li>
 * <li>业务扩展点缺省 {@link DefaultAgentRequestContext}（零项目语义）。</li>
 * </ul>
 * relay 控制器（{@code relay} 包）按框架惯例以 {@code @RestController} 由组件扫描装配——与 core/llm 控制器同款。
 * <p>
 * ⚠️ <b>刻意不用内嵌 @Configuration 分组</b>：扫描根是 {@code com.bidr}，内嵌配置类会被当作独立配置类
 * 扫进来、绕过外层 {@code enabled} 条件（实测装配失败过）。条件一律挂在 Bean 方法上。
 * <p>
 * 关闭装配：{@code my.agent.runtime.enabled=false}——<b>作为框架模块后默认即关闭</b>（原项目内模块默认开），
 * 消费项目须显式置 true 才装配端点与建表；controller 与 Schema 挂同一条件，
 * 防"加了依赖未配置"时因缺 bean 启动失败。<b>provider 切换需重启</b>（历史与 id 语义不同）。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "my.agent.runtime", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class AgentRuntimeAutoConfiguration {

    /**
     * agent-system 条件：provider 未配或配成 agent-system 时生效（缺省实现）
     */
    private static final String PROVIDER_AGENT_SYSTEM = "agent-system";

    /**
     * openhands 条件：显式配置 {@code my.agent.runtime.provider=openhands} 才生效
     */
    private static final String PROVIDER_OPENHANDS = "openhands";

    @Bean
    @ConditionalOnMissingBean
    public AgentRuntimeConfigProvider agentRuntimeConfigProvider(
            AgentRuntimeProperties properties,
            ObjectProvider<SysConfigCacheService> sysConfigProvider) {
        return new AgentRuntimeConfigProvider(properties, sysConfigProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRequestContext agentRequestContext() {
        return new DefaultAgentRequestContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentStreamRelay agentStreamRelay(AgentRuntimeConfigProvider config) {
        return new AgentStreamRelay(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentChatSessionService agentChatSessionService(ChatSessionService repository,
                                                           AgentRuntimeProvider provider,
                                                           AgentRequestContext context,
                                                           AgentRuntimeConfigProvider config) {
        return new AgentChatSessionService(repository, provider, context, config);
    }

    // ==================== provider: agent-system ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "my.agent.runtime", name = "provider",
            havingValue = PROVIDER_AGENT_SYSTEM, matchIfMissing = true)
    public AgentRuntimeRestClient agentRuntimeRestClient(AgentRuntimeConfigProvider config) {
        return new AgentRuntimeRestClient(config);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "my.agent.runtime", name = "provider",
            havingValue = PROVIDER_AGENT_SYSTEM, matchIfMissing = true)
    public AgentRuntimeSseClient agentRuntimeSseClient(AgentRuntimeConfigProvider config) {
        return new AgentRuntimeSseClient(config);
    }

    @Bean
    @ConditionalOnMissingBean(AgentRuntimeProvider.class)
    @ConditionalOnProperty(prefix = "my.agent.runtime", name = "provider",
            havingValue = PROVIDER_AGENT_SYSTEM, matchIfMissing = true)
    public AgentRuntimeProvider agentSystemRuntimeProvider(AgentRuntimeRestClient rest,
                                                           AgentRuntimeSseClient sseClient,
                                                           AgentRuntimeConfigProvider config) {
        logWiring(AgentSystemProvider.NAME, config);
        return new AgentSystemProvider(rest, sseClient);
    }

    // ==================== provider: openhands ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "my.agent.runtime", name = "provider", havingValue = PROVIDER_OPENHANDS)
    public OpenHandsClient openHandsClient(AgentRuntimeConfigProvider config) {
        return new OpenHandsClient(config);
    }

    @Bean
    @ConditionalOnMissingBean(AgentRuntimeProvider.class)
    @ConditionalOnProperty(prefix = "my.agent.runtime", name = "provider", havingValue = PROVIDER_OPENHANDS)
    public AgentRuntimeProvider openHandsRuntimeProvider(AgentRuntimeConfigProvider config,
                                                        AgentRuntimeProperties properties,
                                                        OpenHandsClient client) {
        logWiring(OpenHandsProvider.NAME, config);
        return new OpenHandsProvider(config, properties, client);
    }

    /**
     * 启动即打一行"接的是哪个上游"：切换靠配置、又不做热切，出问题时先要能一眼确认生效的是哪套
     * （密钥只打掩码）
     */
    private void logWiring(String providerName, AgentRuntimeConfigProvider config) {
        log.info("Agent 运行时上游 = {}（base-url={}，api-key={}，已接线={}）", providerName,
                config.getBaseUrl(), config.maskedApiKey(), config.isConfigured());
    }
}
