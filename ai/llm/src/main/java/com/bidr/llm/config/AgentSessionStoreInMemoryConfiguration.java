package com.bidr.llm.config;

import com.bidr.llm.agent.gate.AgentTaskGate;
import com.bidr.llm.agent.gate.InMemoryAgentTaskGate;
import com.bidr.llm.agent.session.AgentSessionStore;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Title: AgentSessionStoreInMemoryConfiguration
 * Description: agent 会话存储内存后端（单实例 fallback：控制键不跨实例、互斥不跨实例）。
 * <p>
 * 🔴 <b>故意的 lite 配置类（无 {@code @Configuration} 注解）</b>，原因同
 * {@link AgentSessionStoreRedisConfiguration}：只允许被 {@link AgentSessionStoreBackendSelector}
 * 在类路径<b>无</b> core/redis 时 import；带 {@code @Configuration}（{@code @Component} 元注解）
 * 会被 {@code com.bidr} 全域扫描直接拾取形成第二装配入口，重回注册序竞争。
 * 走到本类必然类路径无 RedisService，日志文案与事实一致（原结构的「类路径无 core/redis」
 * 在注册序翻转时说谎，见 selector 注释）。bean 方法保留 {@code @ConditionalOnMissingBean}：
 * 业务自定义 {@link AgentSessionStore}/{@link AgentTaskGate} 在场时整体让位。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/17
 */
@Slf4j
public class AgentSessionStoreInMemoryConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentSessionStore.class)
    public AgentSessionStore inMemoryAgentSessionStore() {
        log.info("类路径无 core/redis，装配内存 AgentSessionStore（单实例 fallback，控制键不跨实例）");
        return new InMemoryAgentSessionStore();
    }

    @Bean
    @ConditionalOnMissingBean(AgentTaskGate.class)
    public AgentTaskGate inMemoryAgentTaskGate(
            ObjectProvider<SysConfigCacheService> sysConfigProvider,
            @Value("${llm.agent-task-gate.orphan-millis:90000}") long yamlOrphanMillis) {
        log.info("类路径无 core/redis，装配内存 AgentTaskGate（单实例 fallback，互斥不跨实例；失联阈值同样按系统参数取值）");
        return new InMemoryAgentTaskGate(
                () -> AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS,
                        sysConfigProvider, yamlOrphanMillis));
    }
}
