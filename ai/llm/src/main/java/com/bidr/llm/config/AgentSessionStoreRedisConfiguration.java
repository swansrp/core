package com.bidr.llm.config;

import com.bidr.llm.agent.gate.AgentTaskGate;
import com.bidr.llm.agent.gate.RedisAgentTaskGate;
import com.bidr.llm.agent.session.AgentSessionStore;
import com.bidr.llm.agent.session.RedisAgentSessionStore;
import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Title: AgentSessionStoreRedisConfiguration
 * Description: agent 会话存储 Redis 后端（生产形态：分布式控制键/互斥锁 + TTL 兜底）。
 * <p>
 * 🔴 <b>故意的 lite 配置类（无 {@code @Configuration} 注解）</b>：只允许被
 * {@link AgentSessionStoreBackendSelector} import，绝不能被 {@code @ComponentScan} 扫到——
 * 本项目 BaseApplication 默认扫 {@code com.bidr} 全域，带 {@code @Configuration}（{@code @Component}
 * 元注解）就会被直接拾取，绕过 selector 形成第二装配入口，两后端又回到
 * {@code @ConditionalOnMissingBean} 注册序竞争（pre 事故原形）。无 {@code @Configuration}
 * 注解 ⇒ 扫描器 includeFilter（按 @Component 元注解）不命中 ⇒ 只剩 selector 单入口。
 * lite 形态对本类无副作用：bean 方法互不调用（无需 CGLIB 代理），{@code @Value} 注入与
 * {@code @ConditionalOnMissingBean} 条件评估在 lite 类上照常生效。
 * bean 方法保留 {@code @ConditionalOnMissingBean}：业务自定义 {@link AgentSessionStore}/
 * {@link AgentTaskGate}（组件扫描主批次，先于 deferred imports）在场时整体让位。
 * </p>
 * <p>
 * 🔴 寿命与失联阈值是运行期参数：TTL 取 {@link LlmParam#AGENT_SESSION_TTL_SECONDS} →
 * {@code llm.agent-session.ttl-seconds} 地板 → core 默认 31536000（365 天；2026-11-18 拍板：
 * 会话回放数据不能按天丢）；失联阈值取
 * {@link LlmParam#AGENT_TASK_GATE_ORPHAN_MILLIS} → {@code llm.agent-task-gate.orphan-millis}
 * 地板 → core 默认 90000。传进实现的是 supplier 而非数值，参数页改完下一次写入/判定即生效。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/17
 */
@Slf4j
public class AgentSessionStoreRedisConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentSessionStore.class)
    public AgentSessionStore redisAgentSessionStore(
            com.bidr.platform.redis.service.RedisService redisService,
            ObjectMapper objectMapper,
            ObjectProvider<SysConfigCacheService> sysConfigProvider,
            @Value("${llm.agent-session.key-prefix:llm:agent:session:}") String keyPrefix,
            @Value("${llm.agent-session.ttl-seconds:31536000}") int yamlTtlSeconds) {
        log.info("类路径含 core/redis，装配 Redis AgentSessionStore（prefix={}，ttl 地板={}s；"
                        + "运行期以系统参数「{}」为准，留空即按地板）",
                keyPrefix, yamlTtlSeconds, LlmParam.AGENT_SESSION_TTL_SECONDS.getTitle());
        return new RedisAgentSessionStore(redisService, objectMapper, keyPrefix,
                () -> AgentSessionAutoConfiguration.positiveInt(LlmParam.AGENT_SESSION_TTL_SECONDS,
                        sysConfigProvider, yamlTtlSeconds));
    }

    @Bean
    @ConditionalOnMissingBean(AgentTaskGate.class)
    public AgentTaskGate redisAgentTaskGate(
            com.bidr.platform.redis.service.RedisService redisService,
            ObjectMapper objectMapper,
            ObjectProvider<SysConfigCacheService> sysConfigProvider,
            @Value("${llm.agent-task-gate.key-prefix:llm:agent:task-gate:}") String keyPrefix,
            @Value("${llm.agent-task-gate.orphan-millis:90000}") long yamlOrphanMillis) {
        log.info("类路径含 core/redis，装配 Redis AgentTaskGate（prefix={}，失联阈值地板={}ms；"
                        + "运行期以系统参数「{}」为准，留空即按地板）",
                keyPrefix, yamlOrphanMillis, LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS.getTitle());
        return new RedisAgentTaskGate(redisService, objectMapper, keyPrefix,
                () -> AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS,
                        sysConfigProvider, yamlOrphanMillis));
    }
}
