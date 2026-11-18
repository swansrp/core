package com.bidr.llm.config;

import com.bidr.llm.agent.session.AgentSessionStore;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.bidr.llm.agent.session.RedisAgentSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: AgentSessionStoreAssemblyTest
 * Description: {@link AgentSessionAutoConfiguration} 会话存储后端二选一装配回归测试。
 * <p>
 * 🔴 复现 pre 生产事故：类路径明明有 core/redis（RedisService 在 BOOT-INF/lib），完整应用却装配了
 * 内存 {@link InMemoryAgentSessionStore}（日志「类路径无 core/redis」）⇒ 引擎B 会话存进程内存，
 * 重启/重发即清，历史会话回放全灭。原结构（外层内存 bean + 内嵌 Redis bean 靠
 * {@code @ConditionalOnMissingBean} 竞争）的胜负取决于 bean 定义注册顺序、随装配通道漂移；
 * 修复后改由 {@link AgentSessionStoreBackendSelector} 在 parse 期按类路径二选一 import——
 * 本测试钉住两个形态在隔离容器下都装配正确，装配顺序缺陷当场红掉。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentSessionStoreAssemblyTest {

    /** 类路径有 RedisService＋bean 在场 ⇒ 必须装配 Redis 会话存储（内存兜底退场） */
    @Test
    public void redisStoreWinsWhenRedisServicePresent() {
        runnerWithRedis().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentSessionStore.class);
            assertThat(context.getBean(AgentSessionStore.class))
                    .as("RedisService 类在类路径且 bean 在场时必须装配 RedisAgentSessionStore"
                            + "（内存分支赢了＝后端选择退化，pre 引擎B 会话重启即清的根因）")
                    .isInstanceOf(RedisAgentSessionStore.class);
        });
    }

    /** 类路径无 core/redis（按包过滤模拟）⇒ 必须回落内存形态，且不许有 Redis 类型残留 */
    @Test
    public void inMemoryStoreWinsWhenRedisServiceAbsent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentSessionAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("com.bidr.platform.redis"))
                .withBean(ObjectMapper.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentSessionStore.class);
                    assertThat(context.getBean(AgentSessionStore.class))
                            .as("类路径无 core/redis 时必须装配内存兜底（selector 应只放行内存形态）")
                            .isInstanceOf(InMemoryAgentSessionStore.class);
                });
    }

    private ApplicationContextRunner runnerWithRedis() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentSessionAutoConfiguration.class))
                .withBean(ObjectMapper.class)
                .withBean("stubRedisService", com.bidr.platform.redis.service.RedisService.class,
                        () -> Mockito.mock(com.bidr.platform.redis.service.RedisService.class));
    }
}
