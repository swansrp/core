package com.bidr.llm.config;

import com.bidr.llm.agent.external.ExternalRuntimeTaskService;
import com.bidr.llm.agent.gate.AgentTaskGate;
import com.bidr.llm.agent.gate.InMemoryAgentTaskGate;
import com.bidr.llm.agent.gate.RedisAgentTaskGate;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.session.AgentSessionStore;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.bidr.llm.agent.session.RedisAgentSessionStore;
import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p>
 * 🔴 <b>寿命与失联阈值是运行期参数，不是构造期常量</b>：会话 TTL 取
 * {@link LlmParam#AGENT_SESSION_TTL_SECONDS} → {@code llm.agent-session.ttl-seconds} 地板 → core 默认 86400；
 * 失联阈值取 {@link LlmParam#AGENT_TASK_GATE_ORPHAN_MILLIS} → {@code llm.agent-task-gate.orphan-millis} 地板
 * → core 默认 90000。两侧出厂都<b>留空</b>＝"本键未启用、按 yml 来"，所以升级 core 不会顶掉任何项目
 * 显式写在 yml 里的值（共享件默认行为不许改）。传进实现的是 supplier 而非数值：参数页改完
 * 下一次写入/下一次判定即生效，不用重启、不用发版。内存兜底形态同样按次取值——
 * 两形态生效语义必须一致，否则 dev 侧改了没反应、只在生产生效。
 * </p>
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
                ObjectProvider<SysConfigCacheService> sysConfigProvider,
                @Value("${llm.agent-session.key-prefix:llm:agent:session:}") String keyPrefix,
                @Value("${llm.agent-session.ttl-seconds:86400}") int yamlTtlSeconds) {
            log.info("装配 Redis AgentSessionStore（prefix={}，ttl 地板={}s；运行期以系统参数「{}」为准，留空即按地板）",
                    keyPrefix, yamlTtlSeconds, LlmParam.AGENT_SESSION_TTL_SECONDS.getTitle());
            return new RedisAgentSessionStore(redisService, objectMapper, keyPrefix,
                    () -> positiveInt(LlmParam.AGENT_SESSION_TTL_SECONDS, sysConfigProvider, yamlTtlSeconds));
        }

        @Bean
        @ConditionalOnMissingBean(AgentTaskGate.class)
        public AgentTaskGate redisAgentTaskGate(
                com.bidr.platform.redis.service.RedisService redisService,
                ObjectMapper objectMapper,
                ObjectProvider<SysConfigCacheService> sysConfigProvider,
                @Value("${llm.agent-task-gate.key-prefix:llm:agent:task-gate:}") String keyPrefix,
                @Value("${llm.agent-task-gate.orphan-millis:90000}") long yamlOrphanMillis) {
            log.info("装配 Redis AgentTaskGate（prefix={}，失联阈值地板={}ms；运行期以系统参数「{}」为准，留空即按地板）",
                    keyPrefix, yamlOrphanMillis, LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS.getTitle());
            return new RedisAgentTaskGate(redisService, objectMapper, keyPrefix,
                    () -> positiveLong(LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS, sysConfigProvider, yamlOrphanMillis));
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
            ObjectProvider<SysConfigCacheService> sysConfigProvider,
            @Value("${llm.agent-task-gate.orphan-millis:90000}") long yamlOrphanMillis) {
        log.info("类路径无 core/redis，装配内存 AgentTaskGate（单实例 fallback，互斥不跨实例；失联阈值同样按系统参数取值）");
        return new InMemoryAgentTaskGate(
                () -> positiveLong(LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS, sysConfigProvider, yamlOrphanMillis));
    }

    /**
     * 外部 agent runtime 的任务派发服务（形态 B 接线）：provider 缺席时仍可装配，
     * 工具面据 {@code available()} 明确回绝而非静默假成功——故不因未接外部 runtime 而阻断启动。
     */
    @Bean
    @ConditionalOnMissingBean(ExternalRuntimeTaskService.class)
    public ExternalRuntimeTaskService externalRuntimeTaskService(
            AgentSessionStore store, ObjectProvider<AgentRuntimeProvider> providerHolder) {
        return new ExternalRuntimeTaskService(store, providerHolder::getIfAvailable);
    }

    /**
     * 系统参数正数优先、其余一律回落 yml 地板（{@code fallback}）。
     * <p>每次调用现读，不缓存副本——与 {@code DbAwareModelConfigProvider} 的超时/密钥同通道，
     * 管理页改完 {@code ParamService.refresh()} 之后下一次取值就是新值。
     * "没有可用库值"的形态都必须安静回落而不是抛：① platform 未接入（本模块对它是 optional 依赖，
     * 连 bean 解析本身也可能抛）；② 出厂留空；③ 填了非数字/非正/超出 int 可表示范围（见
     * {@link #positiveInt}）。</p>
     */
    static long positiveLong(LlmParam param, ObjectProvider<SysConfigCacheService> sysConfigProvider, long fallback) {
        SysConfigCacheService service;
        try {
            service = sysConfigProvider == null ? null : sysConfigProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("解析系统参数服务（{} 的取值通道）失败，回落应用配置地板 {}：{}",
                    param.name(), fallback, e.getMessage());
            return fallback;
        }
        if (service == null) {
            return fallback;
        }
        String raw;
        try {
            raw = service.getSysConfigValue(param);
        } catch (Exception e) {
            log.warn("读取系统参数 {} 失败，回落应用配置地板：{}", param.name(), e.getMessage());
            return fallback;
        }
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                log.warn("系统参数 {} 填了非正数 {}，回落应用配置地板 {}（该键语义上必须是正数）",
                        param.name(), parsed, fallback);
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("系统参数 {} 值无法解析成整数「{}」，回落应用配置地板 {}", param.name(), raw, fallback);
            return fallback;
        }
    }

    /**
     * {@link #positiveLong} 的 int 形态（Redis TTL 入参是秒数的 int）。
     * 越界不是理论风险：把"一年"按毫秒口径填进"秒"格＝31536000000，已超出 int 可表示范围 ⇒ 回落地板；
     * 但"一天"按毫秒填 86400000、"一年"按秒填 31536000 都仍在 int 域内，这一层拦不住（如实边界，代价记在参数说明里）。
     * <p>🔴 真超出 int 可表示范围时<b>回落地板</b>，不收口到上界：越界几乎必然是单位填错（把毫秒当秒填），
     * 收口到 {@link Integer#MAX_VALUE}＝约 68 年的永生键，会把误操作静默放大成最危险的形态；
     * 三级链的其余非法形态一律回落，越界不例外（回落值本身恒在域内，{@code setex} 不会再拿到越界值）。</p>
     */
    static int positiveInt(LlmParam param, ObjectProvider<SysConfigCacheService> sysConfigProvider, int fallback) {
        long value = positiveLong(param, sysConfigProvider, fallback);
        if (value > Integer.MAX_VALUE) {
            log.warn("系统参数 {} 值 {} 超出可表示范围，回落应用配置地板 {}（不收口上界，越界多半是单位填错）",
                    param.name(), value, fallback);
            return fallback;
        }
        return (int) value;
    }
}
