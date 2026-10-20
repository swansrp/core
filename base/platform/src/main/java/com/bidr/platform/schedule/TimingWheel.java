package com.bidr.platform.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: TimingWheel
 * Description: 通用时间轮调度件（2026-10-20 下沉 core 归 platform＝通用平台件；首个消费方＝auth 租户级定时任务 {@code TenantJobScheduler}）：单 tick 线程步进环形槽位，
 * 到期件提交 worker 池执行；登记线程任意时刻可登记/取消，tick 线程单消费者步进。
 *
 * <p><b>环形结构（经典 rounds 溢出计数形态）</b>：槽位数 {@code ringSize}、步进间隔
 * {@code tickMillis}。登记时算出 {@code ticks = ceil((deadline-now)/tickMillis)}，
 * 落槽 {@code (cursor+ticks) % ringSize}、溢出圈数 {@code (ticks-1) / ringSize}；
 * tick 线程每步进一格，先对本格内到件 {@code rounds>0} 者减圈，减到 0 且已到期的提交
 * worker、未到期的（登记/步进竞态边界）留待下轮。远期任务（圈数很大）只在其所在槽
 * 每被扫过一轮时减一次圈，代价与圈数无关。</p>
 *
 * <p><b>线程模型</b>：{@code cursor} 仅 tick 线程读写；登记/取消走
 * {@link ConcurrentHashMap} 槽队列（{@link ConcurrentLinkedQueue}）＋ ticket 的
 * volatile 字段，登记线程无须加锁。到期件的执行体在 <b>worker 线程</b>跑
 * （tick 线程只做扫描，绝不被业务代码阻塞）；worker 抛任何 {@link Throwable}
 * 都在本类收口记日志（框架 tick 线程不许被业务异常打死）。</p>
 *
 * <p><b>测试与确定性</b>：{@code autoStart=false} 构造出"不自带线程"的轮子，
 * 配合注入 {@link Clock} 由测试直驱 {@link #advanceOnce()}（确定性断言，不睡真实时钟）；
 * 生产经 {@link #start()} 起内置 daemon 步进线程。本类不感知 cron 也不感知租户——"到期后
 * 算下一次"是消费方的事，轮子只管一次性延期件（one-shot），任何"到期跑一段代码"的场景
 * 都可复用。</p>
 *
 * <p><b>边界（有意为之，勿当缺陷修）</b>：登记早于 {@link #start()} 是合法的
 * （件先入槽、线程起来后照扫）；{@code delayMillis} 小于一个 tick 的按 1 tick 处理
 * （轮子精度＝tickMillis，不追细粒度）；同 key 重复登记不互斥——去重/重叠守卫是
 * 消费方（jobKey 维度）的职责。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TimingWheel {

    private static final Logger log = LoggerFactory.getLogger(TimingWheel.class);

    /**
     * 已登记的定时件（one-shot）：{@link #cancel()} 幂等，取消件在下一轮扫描时被静默丢弃。
     * 重复 {@link #cancel()} 与"取消后任务已在 worker 执行"的竞态都是允许形态
     * （取消承诺＝不再<b>登记面</b>触发，不追杀已提交的执行）。
     */
    public static final class Ticket {
        private final String key;
        private final Runnable task;
        private volatile long deadlineMillis;
        private volatile long rounds;
        private volatile boolean cancelled;

        private Ticket(String key, Runnable task) {
            this.key = key;
            this.task = task;
        }

        /** 取消：幂等；已提交 worker 的执行不追杀（承诺面＝登记面不再触发） */
        public void cancel() {
            this.cancelled = true;
        }

        public String key() {
            return key;
        }
    }

    private final long tickMillis;
    private final int ringSize;
    private final Clock clock;
    private final Executor worker;
    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Ticket>> slots;
    private final AtomicBoolean started = new AtomicBoolean();

    /** 仅 tick 线程读写（登记线程只读、volatile 保证可见性） */
    private volatile int cursor;
    private volatile boolean running;
    private Thread tickThread;

    /**
     * @param clock      时间源（生产 {@code Clock.systemDefaultZone()}；测试注入可拨动的假钟）
     * @param worker     到期件的执行池（tick 线程只扫描不执行）
     * @param tickMillis 步进间隔毫秒（轮子精度；小 tick 仅供测试）
     * @param ringSize   槽位数（≥1；不必 2 的幂）
     * @param autoStart  true＝构造即起内置 daemon 步进线程；false＝测试直驱
     *                   {@link #advanceOnce()}，随后仍可 {@link #start()}
     */
    public TimingWheel(Clock clock, Executor worker, long tickMillis, int ringSize, boolean autoStart) {
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis 必须 > 0，实得 " + tickMillis);
        }
        if (ringSize <= 0) {
            throw new IllegalArgumentException("ringSize 必须 > 0，实得 " + ringSize);
        }
        this.clock = clock;
        this.worker = worker;
        this.tickMillis = tickMillis;
        this.ringSize = ringSize;
        this.slots = new ConcurrentHashMap<>(ringSize);
        for (int i = 0; i < ringSize; i++) {
            slots.put(i, new ConcurrentLinkedQueue<>());
        }
        if (autoStart) {
            start();
        }
    }

    /**
     * 登记一件 one-shot 延期任务（start 前后都可登记；同 key 重复登记不互斥）。
     *
     * @param key         排障标识（进日志，不参与去重）
     * @param delayMillis 相对登记时刻的延期；小于一个 tick 的按 1 tick 处理
     * @param task        到期执行体（在 worker 线程跑，不在 tick 线程）
     * @return 可取消句柄
     */
    public Ticket schedule(String key, long delayMillis, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("定时件执行体不得为 null");
        }
        Ticket ticket = new Ticket(key, task);
        place(ticket, clock.millis() + Math.max(0, delayMillis));
        return ticket;
    }

    /** 登记落槽：ticks 向上取整，rounds＝整圈数（公式推导见类注释） */
    private void place(Ticket ticket, long deadline) {
        long now = clock.millis();
        long ticks = (deadline - now + tickMillis - 1) / tickMillis;
        if (ticks < 1) {
            ticks = 1;
        }
        ticket.deadlineMillis = deadline;
        ticket.rounds = (ticks - 1) / ringSize;
        int slot = (int) ((cursor + ticks) % ringSize);
        slots.get(slot).add(ticket);
    }

    /** 起 daemon 步进线程（幂等；autoStart=false 的测试形态随后调用亦合法） */
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        running = true;
        tickThread = new Thread(this::tickLoop, "tenant-job-wheel");
        tickThread.setDaemon(true);
        tickThread.start();
        log.info("[tenant-job] 时间轮已启动：tick={}ms ring={}", tickMillis, ringSize);
    }

    /** 停止步进线程（幂等；在途 worker 任务不中断——调用方按需自行收池） */
    public synchronized void stop() {
        running = false;
        Thread thread = this.tickThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void tickLoop() {
        while (running) {
            try {
                Thread.sleep(tickMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                advanceOnce();
            } catch (Throwable t) {
                // 扫描自身异常不得终止步进线程（框架心跳线程死了＝全部租户任务停摆）
                log.error("[tenant-job] 时间轮步进异常（继续下一 tick）", t);
            }
        }
    }

    /**
     * 步进一格并收割当前槽：rounds 减圈 → 到期件提交 worker → 未到期/竞态边界件留槽。
     * public＝直驱面：生产由 tick 线程驱动；内嵌方/测试注入假钟后可手动步进
     * （确定性断言、不睡真实时钟；与步进线程混用须自担游标互斥——线程形态下单消费者，
     * 直驱只在无线程形态做）。
     */
    public void advanceOnce() {
        cursor = (cursor + 1) % ringSize;
        ConcurrentLinkedQueue<Ticket> queue = slots.get(cursor);
        List<Ticket> keep = null;
        Ticket ticket;
        while ((ticket = queue.poll()) != null) {
            if (ticket.cancelled) {
                continue;
            }
            if (ticket.rounds > 0) {
                ticket.rounds--;
                keep = keep == null ? new ArrayList<>() : keep;
                keep.add(ticket);
                continue;
            }
            if (ticket.deadlineMillis <= clock.millis()) {
                fire(ticket);
            } else {
                // rounds==0 但时刻未到：登记线程与步进的竞态边界（登记晚于槽位到达），留到下轮
                keep = keep == null ? new ArrayList<>() : keep;
                keep.add(ticket);
            }
        }
        if (keep != null) {
            queue.addAll(keep);
        }
    }

    private void fire(Ticket ticket) {
        try {
            worker.execute(() -> {
                if (ticket.cancelled) {
                    return;
                }
                try {
                    ticket.task.run();
                } catch (Throwable t) {
                    log.error("[tenant-job] 定时件执行异常（key={}，异常已收口不回传轮子）", ticket.key(), t);
                }
            });
        } catch (Throwable t) {
            log.error("[tenant-job] 定时件提交 worker 被拒（key={}，池已关闭或饱和）", ticket.key(), t);
        }
    }
}
