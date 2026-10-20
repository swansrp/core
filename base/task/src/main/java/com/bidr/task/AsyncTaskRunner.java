package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 异步任务跑批（M16 棒 2）：扫描 {@code sa_async_task}，按类型把任务派给注解流程类的阶段方法。
 *
 * <p><b>执行侧唯一入口＝跑批线程</b>（纪律 §4.2.4-②）：阶段方法只由本类执行，受理侧不执行任何
 * 阶段——于是切面可无条件假定当前线程没有请求上下文，{@link TaskContextBridge} 的成对
 * 重建/恢复是安全的。每任务一轮＝重建上下文→经代理调阶段方法（切面接管记账）→未终态即续租。</p>
 *
 * <p><b>多实例安全</b>：抢占是条件 UPDATE（{@link AsyncTaskRepository#claimBatch}），一个任务
 * 同一时刻只被一个实例持有；持有期间按 tick 续租，租约过期即视为实例失联，由
 * {@link #reapExpired} 回收重排（失联不是业务失败，不吃退避；超预算则 DEAD）。
 * 每轮每类型只推固定条数：跑批要能被「下一个 tick」打断，一条坏数据不该饿死其余任务。</p>
 *
 * <p><b>唤醒信号</b>：{@link TaskWakeSignal} 收到类型即 drain 一轮（submit 后的加速器）；
 * 信号丢失由 {@code fixedDelay} 轮询兜底（fixedDelay 而非 fixedRate——避免上轮未跑完叠下一轮）。</p>
 *
 * @author sharp
 */
@Service
public class AsyncTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskRunner.class);

    /** 单轮单类型最多推进的阶段数（含续跑与抢占） */
    private static final int BATCH = 5;

    /** 补偿扫描单轮上限：一次不放太多回队列，避免坏数据把跑批喂满 */
    private static final int REAP_LIMIT = 50;

    private final TaskFlowRegistry registry;

    private final AsyncTaskRepository repository;

    private final AsyncTaskService taskService;

    private final TaskContextBridge bridge;

    /** 执行者标识（落 owner，续租与回收都按它认领）：JVM 名＋随机尾，区分同机多进程与滚动部署新旧实例 */
    private final String owner = instanceId();

    /** 按任务类型的 JVM 级互斥（tick 与唤醒双线程不得同时 drain 同一类型；不同类型互不阻塞） */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 跑批开关：本地只想手工触发时关掉（关掉后重启前入队的任务不会被自动推进）。
     *  字段带 true 初始值：无 Spring 注入的装配形态（离线测试）默认开。 */
    @Value("${base.task.runner.enabled:true}")
    private boolean enabled = true;

    /** 时钟（租约/回收的「现在」）：生产＝系统时钟；离线测试可注入跳时（退避/租约到期腿不真等） */
    Supplier<Date> clock = Date::new;

    @Autowired
    public AsyncTaskRunner(TaskFlowRegistry registry, AsyncTaskRepository repository,
                           AsyncTaskService taskService, TaskContextBridge bridge,
                           TaskWakeSignal wakeSignal) {
        this.registry = registry;
        this.repository = repository;
        this.taskService = taskService;
        this.bridge = bridge;
        wakeSignal.setListener(this::drainNow);
    }

    /**
     * 启动期校验触发点：首次拉全量流程即完成收集与 order 断号/重复校验（判据⑥＝启动期直接失败，
     * 不做运行时兜底）。放本类而不放注册表构造器：流程 Bean 是带切面的代理、代理链依赖注册表
     * 所在装配链，构造期拉代理＝循环引用（Boot 2.6 起默认拒绝）；此处各 Bean 已构造完毕。
     */
    @javax.annotation.PostConstruct
    void validateAtStartup() {
        registry.all();
    }

    /** 唤醒信号入口：只跑该类型一轮（失败由轮询兜底，异常吞成日志） */
    void drainNow(String taskType) {
        try {
            TaskFlowRegistry.FlowDef flow = registry.find(taskType);
            if (flow != null) {
                drain(flow);
            }
        } catch (Exception e) {
            log.warn("唤醒跑批异常（轮询兜底）: type={} {}", taskType, e.getMessage());
        }
    }

    /** 跑批主循环：fixedDelay，每轮全部注册类型各 drain 一次（单类型异常不拖垮其他类型） */
    @Scheduled(fixedDelayString = "${base.task.runner.interval-ms:5000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        for (TaskFlowRegistry.FlowDef flow : registry.all()) {
            try {
                drain(flow);
            } catch (Exception e) {
                log.error("任务跑批异常（本轮跳过该类型）: type={} {}", flow.getType(), e.getMessage(), e);
            }
        }
    }

    /** 一个类型一轮：先续跑本实例名下在途任务，再抢占新任务，最后回收失联任务。
     *
     * <p>🔴 <b>同一 JVM 内按类型的互斥（M16 修复轮 A2）</b>：{@code tick()}（调度线程）与
     * {@link #drainNow}（{@code base-task-wake} 线程）会并发进入同一类型；而
     * {@link #ownedRunning} 是纯 SELECT，两条线程能同时取到同一批在途任务、把同一阶段体执行
     * 两遍（绕过底盘的 external_id 幂等）。{@code tryLock} 抢不到即返回——本轮交给持锁线程，
     * 下一 tick 自然补上；不同类型用不同的锁，互不排队（不做全局串行）。</p>
     */
    void drain(TaskFlowRegistry.FlowDef flow) {
        ReentrantLock lock = locks.computeIfAbsent(flow.getType(), type -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.debug("同类型跑批已在另一线程进行，本轮跳过: type={}", flow.getType());
            return;
        }
        try {
            List<SaAsyncTask> mine = ownedRunning(flow.getType(), owner, BATCH);
            List<SaAsyncTask> fresh = claimBatch(flow.getType(), owner, BATCH - mine.size());
            for (SaAsyncTask task : mine) {
                execute(flow, task);
            }
            for (SaAsyncTask task : fresh) {
                execute(flow, task);
            }
            int reaped = reapExpired(flow);
            if (reaped > 0) {
                log.warn("补偿扫描回收失联任务: type={} count={}", flow.getType(), reaped);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 本实例名下在途任务（RUNNING、租约未过期、退避已到）：跨 tick 续跑，不参与抢占 */
    public List<SaAsyncTask> ownedRunning(String taskType, String owner, int limit) {
        return repository.ownedRunning(taskType, owner, limit);
    }

    /** 抢占待执行任务（租约时长取流程注解，缺省跟 yml；条件 UPDATE 当锁） */
    public List<SaAsyncTask> claimBatch(String taskType, String owner, int limit) {
        TaskFlowRegistry.FlowDef flow = registry.require(taskType);
        Date leaseExpire = new Date(clock.get().getTime() + flow.getLeaseMs());
        return repository.claimBatch(taskType, owner, leaseExpire, limit);
    }

    /** 续租：阶段跑完仍非终态即续（长流程跨 tick 不被判失联）；false＝已易主，本次别再写任务行 */
    public boolean renew(SaAsyncTask task, String owner) {
        return repository.renew(task.getId(), owner,
                new Date(clock.get().getTime() + registry.require(task.getTaskType()).getLeaseMs()));
    }

    /**
     * 补偿扫描：租约过期的 RUNNING 行＝执行实例失联（进程被杀/重启/部署中断）。
     * 计一次尝试并立即回到待执行（失联不是业务失败，不吃退避）；超预算则 DEAD（观察者必发）。
     *
     * @return 本轮回收行数
     */
    public int reapExpired(TaskFlowRegistry.FlowDef flow) {
        List<SaAsyncTask> expired = repository.expiredRunning(flow.getType(), clock.get(), REAP_LIMIT);
        for (SaAsyncTask task : expired) {
            String reason = "执行实例失联（租约超时，原执行者 " + task.getOwner() + "）";
            bridge.execute(task, () -> {
                if (AsyncTaskService.retryExhausted(flow, task)) {
                    taskService.dead(task, new IllegalStateException(reason));
                } else {
                    int attempt = (task.getAttempt() == null ? 0 : task.getAttempt()) + 1;
                    // 回收写按原持有者（失联实例）作谓词：行仍是 RUNNING 且 owner 未变才写得进去
                    boolean recovered = repository.updateFailed(task.getId(), task.getOwner(), reason, attempt, clock.get());
                    if (recovered) {
                        log.warn("回收失联任务: type={} task={} stage={} 第 {} 次尝试",
                                task.getTaskType(), task.getId(), task.getStage(), attempt);
                    } else {
                        log.warn("回收失联任务写回被拒（行已易主）: type={} task={}", task.getTaskType(), task.getId());
                    }
                }
            });
        }
        return expired.size();
    }

    /**
     * 推一个任务一轮：重建上下文→经代理调当前阶段方法（切面接管三态落库与异常分诊）→
     * 未终态即续租。切面之外的异常（阶段方法不在流程清单内、反射不可访问等基础设施错）只记日志：
     * 任务行不动，租约到期由补偿扫描回收。
     */
    private void execute(TaskFlowRegistry.FlowDef flow, SaAsyncTask task) {
        try {
            bridge.execute(task, () -> invokeStage(flow, task));
            SaAsyncTask latest = repository.findById(task.getId());
            if (latest != null && AsyncTaskStatus.RUNNING.name().equals(latest.getStatus())) {
                Date now = clock.get();
                if (latest.getNextRunAt() != null && latest.getNextRunAt().after(now)) {
                    // 本阶段返回 pending 退避中：租约已被 pending() 延到 max(lease, backoff)，
                    // 这里按流程租约续租会把它截短，下一跳退避未到就被当成「实例失联」回收
                    log.debug("任务退避中不续租: type={} task={} nextRunAt={}",
                            task.getTaskType(), task.getId(), latest.getNextRunAt());
                } else if (!renew(latest, owner)) {
                    log.warn("任务已易主（租约过期被回收），本次进度不再写回: task={}", task.getId());
                }
            }
        } catch (Exception e) {
            log.error("任务推进异常（任务行不动，租约到期由补偿扫描回收）: type={} task={} stage={} {}",
                    task.getTaskType(), task.getId(), task.getStage(), e.getMessage(), e);
        }
    }

    /** 经 Spring 代理调阶段方法（切面才会接管）；scope 参数由切面注入（这里传 null 占位）。
     *  反射包装异常在此解开：业务异常在切面链内已按原类型分诊，落到这里的只剩基础设施错误。 */
    private Object invokeStage(TaskFlowRegistry.FlowDef flow, SaAsyncTask task) {
        TaskFlowRegistry.StageDef stage = flow.stageOf(task.getStage());
        if (stage == null) {
            throw new IllegalStateException("任务行的阶段不在流程清单内: type=" + flow.getType()
                    + " stage=" + task.getStage());
        }
        try {
            return stage.getMethod().invoke(flow.getBean(), task, null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("阶段方法执行失败: " + stage.getMethod().getName(), cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("阶段方法不可访问: " + stage.getMethod().getName(), e);
        }
    }

    /** 实例标识：JVM 名（pid@host）＋随机尾 */
    private static String instanceId() {
        String jvm = ManagementFactory.getRuntimeMXBean().getName();
        return jvm + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
