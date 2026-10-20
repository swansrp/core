package com.bidr.authorization.tenant.job;

import com.bidr.kernel.cache.lock.CacheLockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Title: TenantJobSchedulingConfiguration
 * Description: 租户级定时任务的装配口（2026-10-20 下沉 core）：容器里收集全部
 * {@link TenantScopedJob} 业务 bean ＋ {@link ActiveTenantSource} 租户枚举口，
 * 组装 {@link TenantJobScheduler}（cron → 时间轮 → 逐租户执行）。
 *
 * <p><b>装配纪律（与 {@code my.tenant.line-enabled} 同款安全口径）</b>：
 * {@code @ConditionalOnProperty(prefix="my.tenant", name="job-wheel.enabled",
 * havingValue="true")}——<b>默认不装配</b>，已上线项目零影响（无线程、无轮子、无收集）；
 * 启用方 yml 显式 {@code my.tenant.job-wheel.enabled: true}。启用但无任务的壳＝空转待命
 * （合法形态，调度器不起轮子线程）。</p>
 *
 * <p><b>分布式锁（多实例部署的正闸）</b>：锁面＝容器里的 {@link CacheLockProvider}——
 * redis 模块在场（{@code HybridCacheConfig} 自动装配）且 Redisson 可用时＝
 * <b>Redisson 分布式锁</b>，同一触发点全实例互斥（键含触发点时刻，键粒度＝单轮，租期
 * {@code my.tenant.job-wheel.lock-lease-seconds} 缺省 600s）；无 redisson 时该装配回落
 * JVM 本地锁＝单实例语义，调度器启动 WARN 提醒多实例部署补锁面。</p>
 *
 * <p><b>多壳口径</b>：本装配随 core 进每个 boot 壳的组件扫描面（{@code com.bidr}）——
 * 任务收集面＝<b>本壳 classpath 上</b>的实现 bean，"只该跑一份"的任务必须只放在
 * 单一壳的装配面（放公共模块＝各壳各跑一遍，D-5 拓扑的既有纪律）。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
@Configuration
@ConditionalOnProperty(prefix = "my.tenant", name = "job-wheel.enabled", havingValue = "true")
public class TenantJobSchedulingConfiguration {

    /**
     * 调度器 bean：SmartLifecycle（容器刷新后 start、关闭时 stop）。
     * cron 解析/租户枚举口的装配校验在构造器 fail-fast——坏配置宁可不启动。
     */
    /**
     * 调度器 bean：SmartLifecycle（容器刷新后 start、关闭时 stop）。
     * cron 解析/租户枚举口的装配校验在构造器 fail-fast——坏配置宁可不启动。
     *
     * @param lockProvider   分布式锁面（redis 模块在场时＝Redisson 分布式实现，多实例互斥；
     *                       缺省回落 JVM 本地锁——单实例安全，多实例部署须让锁面进容器，调度器启动 WARN 提醒）
     * @param lockLeaseSeconds 触发点锁租期秒（须大于单轮最慢执行时长；超时＝任务该拆了）
     */
    @Bean
    public TenantJobScheduler tenantJobScheduler(ObjectProvider<TenantScopedJob> jobsProvider,
                                                 ObjectProvider<ActiveTenantSource> sourceProvider,
                                                 ObjectProvider<CacheLockProvider> lockProvider,
                                                 @Value("${my.tenant.job-wheel.lock-lease-seconds:600}") long lockLeaseSeconds) {
        List<TenantScopedJob> jobs = StreamSupport.stream(jobsProvider.spliterator(), false)
                .collect(Collectors.toList());
        return new TenantJobScheduler(jobs, sourceProvider.getIfAvailable(), Clock.systemDefaultZone(),
                lockProvider.getIfAvailable(), lockLeaseSeconds, true);
    }
}
