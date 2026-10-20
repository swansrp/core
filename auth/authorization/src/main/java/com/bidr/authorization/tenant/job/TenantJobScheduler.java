package com.bidr.authorization.tenant.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bidr.kernel.cache.lock.CacheLockProvider;
import com.bidr.platform.schedule.TimingWheel;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: TenantJobScheduler
 * Description: 租户级定时任务的框架调度器（2026-10-20 下沉 core）：启动期收集容器内全部
 * {@link TenantScopedJob}（"定点收集"），逐个解析 cron 后挂上 {@link TimingWheel}
 * （"定点跑"）——每个触发点经 {@link TenantJobExecutor} 对启用租户逐个执行，
 * 单租户失败记 ERROR 跳过继续，任务级重叠（上轮未完又到点）整轮跳过。
 *
 * <p><b>生命周期</b>：{@link SmartLifecycle}——容器刷新完成后 {@link #start()}、关闭时
 * {@link #stop()}。start 做三件 fail-fast 校验：① cron 全部可解析（坏表达式＝装配事故，
 * 宁可不启动，不收一个永远不跑/跑错时刻的任务）；② 有任务但容器无
 * {@link ActiveTenantSource} ＝ 装配事故抛出（永远空转的任务不收）；③ 无任务＝空转待命
 * （合法形态：启用闸开了但该壳还没落任务，记 INFO 不起轮子线程）。</p>
 *
 * <p><b>线程模型</b>：时间轮步进线程（daemon，轮子自管）＋ 本类自有 worker 池
 * （2 线程 daemon——定时任务吞吐诉求极低，2 足够；池参数要调是消费方扩容的信号）。
 * stop 时轮子停步、worker {@code shutdownNow}——在途租户动作不等待（定时任务半途截断
 * 由"幂等可重跑"承接，这是任务实现方的义务）。</p>
 *
 * <p><b>重叠守卫</b>：按 {@code jobKey()} 记在飞位——触发点到达但上一轮未跑完时本轮
 * 整轮跳过（记 WARN，下一触发点照常），防止慢轮次在 worker 池里无限堆积。
 * {@code runForAllTenants} 的取数/执行全部在 worker 线程，轮子 tick 线程只做登记面。</p>
 *
 * <p><b>分布式锁（多实例部署的正闸）</b>：每个触发点先抢
 * {@link CacheLockProvider} 锁（键＝{@code tenant-job:{jobKey}:{触发点epoch秒}}，
 * 非阻塞、租期 {@code my.tenant.job-wheel.lock-lease-seconds} 缺省 600s）——redis 面
 * 在场＝全实例互斥（谁抢到谁跑），抢不到/锁面不可用＝本轮跳过（<b>宁可漏跑一轮，
 * 不重复跑</b>）；无锁面 bean 回落 JVM 本地锁＋启动 WARN（单实例安全）。锁键含触发点
 * 时刻 ⇒ 键粒度＝单轮，崩溃实例的残留锁不牵连后续轮次（租期内那轮除外，TTL 兜底）。</p>
 *
 * <p><b>装配纪律</b>：仅在 {@code my.tenant.job-wheel.enabled=true} 时由
 * {@link TenantJobSchedulingConfiguration} 注册——缺省不装配，未启用项目零影响。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TenantJobScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TenantJobScheduler.class);

    /** 时间轮参数：秒级步进＋64 槽（cron 最细粒度＝秒；64 槽对远期任务的减圈代价可忽略） */
    static final long TICK_MILLIS = 1000L;
    static final int RING_SIZE = 64;
    /** worker 池大小：定时任务吞吐诉求极低；防慢任务占满（重叠守卫兜底堆积） */
    static final int WORKER_THREADS = 2;
    /** 分布式锁缺省租期：单轮执行的上界假设——超时说明任务该拆了，而不是调大租期 */
    static final long DEFAULT_LOCK_LEASE_SECONDS = 600L;

    private final List<TenantScopedJob> jobs;
    private final ActiveTenantSource source;
    private final Clock clock;
    /** 分布式锁面（多实例部署防同一触发点重复执行）：redis 面＝分布式，回落面＝JVM 本地 */
    private final CacheLockProvider lockProvider;
    private final long lockLeaseSeconds;
    private final TimingWheel wheel;
    private final ExecutorService worker;
    private final Map<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();
    private final Map<String, CronExpression> expressions = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    /** false＝测试形态：start 不起轮子线程，由测试注入假钟直驱 {@link #wheelForTest()} */
    private final boolean autoStartWheel;

    public TenantJobScheduler(List<TenantScopedJob> jobs, ActiveTenantSource source, Clock clock,
                              CacheLockProvider lockProvider) {
        this(jobs, source, clock, lockProvider, DEFAULT_LOCK_LEASE_SECONDS, true);
    }

    TenantJobScheduler(List<TenantScopedJob> jobs, ActiveTenantSource source, Clock clock,
                       CacheLockProvider lockProvider, long lockLeaseSeconds, boolean autoStartWheel) {
        this.jobs = jobs == null ? Collections.<TenantScopedJob>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(jobs));
        this.source = source;
        this.clock = clock;
        // 锁面缺省＝JVM 本地（单实例语义）；启用分布式部署必须让 redis 面的 CacheLockProvider 进容器
        this.lockProvider = lockProvider == null ? CacheLockProvider.LOCAL : lockProvider;
        this.lockLeaseSeconds = lockLeaseSeconds > 0 ? lockLeaseSeconds : DEFAULT_LOCK_LEASE_SECONDS;
        this.autoStartWheel = autoStartWheel;
        if (lockProvider == null) {
            log.warn("[tenant-job] 容器无 CacheLockProvider bean，触发点锁回落 JVM 本地（单实例安全；"
                    + "多实例部署请让 redis 面的 CacheLockProvider 进容器，否则同一触发点会各实例重复执行）");
        }
        this.worker = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread thread = new Thread(r, "tenant-job-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.wheel = new TimingWheel(clock, worker, TICK_MILLIS, RING_SIZE, false);
        // fail-fast 校验：cron 全部可解析；有任务必须给得出租户枚举口（装配事故不进运行期）
        for (TenantScopedJob job : this.jobs) {
            if (!job.enabled()) {
                continue;
            }
            try {
                expressions.put(job.jobKey(), CronExpression.parse(job.cron()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("[tenant-job] 任务 " + job.jobKey()
                        + " 的 cron 不可解析（须 6 段 Spring cron：秒 分 时 日 月 周）：\""
                        + job.cron() + "\"——装配事故 fail-fast，请修正后重启", e);
            }
        }
        if (!expressions.isEmpty() && source == null) {
            throw new IllegalStateException("[tenant-job] 容器存在租户级任务 " + expressions.keySet()
                    + " 但未装配 ActiveTenantSource bean——任务无法枚举租户，装配事故 fail-fast"
                    + "（业务侧应提供启用租户的取数实现，或关掉 my.tenant.job-wheel.enabled）");
        }
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        running = true;
        if (expressions.isEmpty()) {
            log.info("[tenant-job] 调度器启动：无租户级任务（空转待命，不起轮子线程）");
            return;
        }
        log.info("[tenant-job] 调度器启动：收集到 {} 个租户级任务 {}", expressions.size(), expressions.keySet());
        if (autoStartWheel) {
            wheel.start();
        }
        for (Map.Entry<String, CronExpression> entry : expressions.entrySet()) {
            scheduleNext(jobOf(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public void stop() {
        running = false;
        wheel.stop();
        worker.shutdownNow();
        log.info("[tenant-job] 调度器已停止（在途租户动作不等待，幂等可重跑由任务实现方承接）");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 计算下一次触发点并登记上轮（无下一次＝cron 永不再命中，记 WARN 后自然退役） */
    private void scheduleNext(TenantScopedJob job, CronExpression expression) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = expression.next(now);
        if (next == null) {
            log.warn("[tenant-job] 任务 {} 的 cron \"{}\" 不再有任何触发点，不登记", job.jobKey(), job.cron());
            return;
        }
        long delay = Math.max(0, Duration.between(now, next).toMillis());
        long fireEpochSecond = next.toEpochSecond();
        wheel.schedule(job.jobKey(), delay, () -> fire(job, expression, fireEpochSecond));
    }

    /**
     * 触发点到达：分布式锁 → 重叠守卫 → 逐租户执行（单租户失败跳过继续）→ 登记下一次。
     *
     * <p><b>锁语义（多实例部署的正闸）</b>：锁键按<b>触发点</b>粒度
     * {@code tenant-job:{jobKey}:{触发点epoch秒}}——同轮在多实例间互斥（谁抢到谁跑），
     * 不同轮互不牵连（崩溃实例的残留锁只影响其租期内那一轮，TTL 到期自然消解）。
     * 非阻塞抢锁（waitTime=0）：抢不到＝别的实例正在跑本轮，整轮跳过（INFO 留痕）。
     * 锁面不可用（redis 面返回 null）与"抢不到"同待遇——<b>宁可漏跑一轮，不重复跑</b>；
     * JVM 本地回落面下单实例部署无感。</p>
     */
    private void fire(TenantScopedJob job, CronExpression expression, long fireEpochSecond) {
        if (!running) {
            return;
        }
        String lockKey = "tenant-job:" + job.jobKey() + ":" + fireEpochSecond;
        Object lockToken = lockProvider.tryLock(lockKey, 0L, lockLeaseSeconds * 1000L);
        if (lockToken == null) {
            log.info("[tenant-job] 任务 {} 触发点 {} 的执行锁被其它实例持有（或锁面不可用），本轮跳过",
                    job.jobKey(), fireEpochSecond);
            scheduleNext(job, expression);
            return;
        }
        AtomicBoolean flag = inFlight.computeIfAbsent(job.jobKey(), k -> new AtomicBoolean());
        try {
            if (!flag.compareAndSet(false, true)) {
                log.warn("[tenant-job] 任务 {} 上一轮尚未结束，本轮整轮跳过（下一触发点照常）", job.jobKey());
                return;
            }
            TenantJobExecutor.runForAllTenants(source, tenantId -> {
                try {
                    job.execute(tenantId);
                } catch (Exception e) {
                    log.error("[tenant-job] 租户级任务执行失败，跳过继续下一租户：tenantId={} job={}",
                            tenantId, job.jobKey(), e);
                }
            });
        } finally {
            flag.set(false);
            lockProvider.unlock(lockToken);
            if (running) {
                scheduleNext(job, expression);
            }
        }
    }

    /** 按 jobKey 找回任务（登记面与触发面解耦后的回查；找不到＝任务被并发摘除，跳过） */
    private TenantScopedJob jobOf(String jobKey) {
        for (TenantScopedJob job : jobs) {
            if (job.jobKey().equals(jobKey)) {
                return job;
            }
        }
        throw new IllegalStateException("[tenant-job] jobKey " + jobKey + " 找不回任务实例（启动清单被改动）");
    }

    /** 测试直驱口：不起步进线程的形态下，由测试拨假钟后手动步进收割（包私有） */
    TimingWheel wheelForTest() {
        return wheel;
    }
}
