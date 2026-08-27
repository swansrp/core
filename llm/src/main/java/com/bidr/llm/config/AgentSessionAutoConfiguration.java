package com.bidr.llm.config;

import com.bidr.llm.agent.gate.AgentTaskGate;
import com.bidr.llm.agent.gate.InMemoryAgentTaskGate;
import com.bidr.llm.agent.gate.RedisAgentTaskGate;
import com.bidr.llm.agent.session.AgentSessionStore;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.bidr.llm.agent.session.RedisAgentSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: AgentSessionAutoConfiguration
 * Description: agent 会话存储与任务闸门默认装配（开箱即用，业务可覆盖）：
 * 类路径有 core/redis 时装配 Redis 实现（生产：分布式控制键/互斥锁 + TTL 兜底），
 * 否则回落内存实现（单实例 fallback）。Redis 分支放内嵌配置类做类加载隔离——
 * 外层配置类无 RedisService 类型引用，无 redis 依赖的应用装载外层不触发 NoClassDefFoundError
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
@AutoConfiguration
public class AgentSessionAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.bidr.platform.redis.service.RedisService")
    public static class RedisStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(AgentSessionStore.class)
        public AgentSessionStore redisAgentSessionStore(
                com.bidr.platform.redis.service.RedisService redisService,
                ObjectMapper objectMapper,
                @Value("${llm.agent-session.key-prefix:llm:agent:session:}") String keyPrefix,
                @Value("${llm.agent-session.ttl-seconds:86400}") int ttlSeconds) {
            log.info("装配 Redis AgentSessionStore（prefix={}，ttl={}s）", keyPrefix, ttlSeconds);
            return new RedisAgentSessionStore(redisService, objectMapper, keyPrefix, ttlSeconds);
        }

        @Bean
        @ConditionalOnMissingBean(AgentTaskGate.class)
        public AgentTaskGate redisAgentTaskGate(
                com.bidr.platform.redis.service.RedisService redisService,
                ObjectMapper objectMapper,
                @Value("${llm.agent-task-gate.key-prefix:llm:agent:task-gate:}") String keyPrefix,
                @Value("${llm.agent-task-gate.orphan-millis:90000}") long orphanMillis) {
            log.info("装配 Redis AgentTaskGate（prefix={}，失联阈值 {}ms）", keyPrefix, orphanMillis);
            return new RedisAgentTaskGate(redisService, objectMapper, keyPrefix, orphanMillis);
        }
    }

    @Bean
    @ConditionalOnMissingBean(AgentSessionStore.class)
    public AgentSessionStore inMemoryAgentSessionStore() {
        log.info("类路径无 core/redis，装配内存 AgentSessionStore（单实例 fallback，控制键不跨实例）");
        return new InMemoryAgentSessionStore();
    }

    @Bean
    @ConditionalOnMissingBean(AgentTaskGate.class)
    public AgentTaskGate inMemoryAgentTaskGate(
            @Value("${llm.agent-task-gate.orphan-millis:90000}") long orphanMillis) {
        log.info("类路径无 core/redis，装配内存 AgentTaskGate（单实例 fallback，互斥不跨实例）");
        return new InMemoryAgentTaskGate(orphanMillis);
    }
}
