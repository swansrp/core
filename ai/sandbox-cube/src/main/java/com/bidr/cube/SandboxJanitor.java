package com.bidr.cube;

import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Title: SandboxJanitor
 * Description: Cube 沙箱清扫器（{@link SmartLifecycle}，容器自动驱动 start/stop）——
 * 沙箱清除三层对齐的回收兜底（对账判据 = {@code client.listSandboxEntries()} 回显的
 * metadata，销毁动作统一走 {@code CubeSandboxProvider.killLease}）：
 * <ul>
 *   <li><b>启动孤儿对账</b>（{@link #start} 内先行）：全量条目逐条<b>五重排除</b>（任一命中即跳过）——
 *       ① {@code role=cube-admin} 管理箱（绝不可杀）② {@code owner} 键缺失或不等于 {@code ownerId}
 *       （他系统/存量无 owner 箱）③ {@code session-id} 键缺失或空 ④ {@code instance-id} 等于
 *       本进程实例身份（本进程活箱——含 webserver 先就绪窗口内请求线程刚建的，
 *       I-2 自伤竞态的直接防线）⑤ 本进程租约表已含该 session-id（同进程活跃会话，
 *       双保险）；剩余＝本系统孤儿业务箱 ⇒ delete。
 *       🔴 主线程拍板：判据<b>只认 list 回显的 metadata</b>——metadata 为空 map
 *       （CubeMaster list 实测剥离自定义 metadata）⇒ 一律保守跳过＋info 记录候选
 *       （「身份不明，保守跳过」），<b>宁可漏杀不可误杀</b>（漏网由终态销毁＋清扫轮兜底）。</li>
 *   <li><b>清扫轮</b>：守护线程单线程 {@link ScheduledExecutorService}（间隔＝
 *       cleanupInterval 秒），遍历 {@code provider.leaseView()}，双判据任一命中即 killLease
 *       ＋逐出——① 空闲超时 {@code now − lastActiveAt > idleTimeout×1000} ② 寿命超限
 *       {@code now − createdAt > maxLifetime×1000}。逐条 try/catch（单条失败 warn 继续），
 *       整轮再兜一层 try/catch（warn 后等下一轮）＝单轮异常隔离。</li>
 *   <li><b>停止</b>：shutdown 调度器＋短超时 awaitTermination（超时 shutdownNow 补刀），
 *       {@link #isRunning()} 落 false。</li>
 * </ul>
 * 对账整体失败（list 抛异常）在 start 内吞掉＋warn，绝不影响启动。
 *
 * @author Sharp
 * @since 2026/11/17
 */
@Slf4j
public class SandboxJanitor implements SmartLifecycle {

    /** metadata 键：角色标记（{@code cube-admin}＝管理箱，排除①） */
    private static final String META_ROLE = "role";

    /** metadata 键：所有权身份（排除②） */
    private static final String META_OWNER = "owner";

    /** metadata 键：会话归属（排除③） */
    private static final String META_SESSION_ID = "session-id";

    /** metadata 键：进程实例身份（排除④——本进程活箱；缺失＝存量代际照样可清） */
    private static final String META_INSTANCE_ID = "instance-id";

    /** stop 后等待在跑清扫轮收尾的短超时（秒） */
    private static final int STOP_AWAIT_SECONDS = 5;

    private final CubeSandboxProvider provider;

    private final CubeHttpClient client;

    private final CubeSandboxProperties properties;

    /** 毫秒时钟（清扫双判据共用）；生产 System::currentTimeMillis，测试注入快进时钟 */
    private final LongSupplier clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ScheduledExecutorService scheduler;

    public SandboxJanitor(CubeSandboxProvider provider, CubeHttpClient client,
                          CubeSandboxProperties properties) {
        this(provider, client, properties, System::currentTimeMillis);
    }

    SandboxJanitor(CubeSandboxProvider provider, CubeHttpClient client,
                   CubeSandboxProperties properties, LongSupplier clock) {
        this.provider = provider;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 启动：先跑一次启动孤儿对账（异常吞＋warn，不影响启动），再起守护线程清扫调度
     * （固定间隔 {@code cleanupInterval} 秒；≤0 防御性 clamp 到 1——非正 period 会让
     * scheduleWithFixedDelay 抛 IAE，与 Provider 的 M-5 clamp 同口径）。
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            reconcileOrphans();
        } catch (RuntimeException e) {
            log.warn("Cube 沙箱启动孤儿对账失败（跳过，不影响启动）: {}", e.getMessage());
        }
        long intervalSeconds = Math.max(1L, properties.getCleanupInterval());
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cube-sandbox-janitor");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::cleanupExpired, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Cube 沙箱清扫器启动: interval={}s idleTimeout={}s maxLifetime={}s",
                intervalSeconds, properties.getIdleTimeout(), properties.getMaxLifetime());
    }

    /**
     * 停止：shutdown 调度器＋短超时等待收尾（在跑的那一轮让它跑完，超时 shutdownNow 补刀）；
     * 幂等（未运行时调用直接返回）。
     */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledExecutorService current = scheduler;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(STOP_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        }
        log.info("Cube 沙箱清扫器停止: 调度线程池已终止");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 调度线程池是否已完全终止（包内可见测试探针：stop 生命周期自查断言用）。
     */
    boolean schedulerTerminated() {
        ScheduledExecutorService current = scheduler;
        return current == null || current.isTerminated();
    }

    // ---------------------------------------------------------------- 清扫轮

    /**
     * 清扫轮（单轮）：遍历活跃租约快照，双判据任一命中即 {@code killLease}（内部＝注册表
     * 逐出＋delete，404 幂等）——① 空闲超时（now−lastActiveAt＞idleTimeout）② 寿命超限
     * （now−createdAt＞maxLifetime）。逐条 try/catch：单条 kill 失败 warn 后继续其余条目
     * （注册表已先逐出，下轮对账寻址兜底）；整轮再兜一层 try/catch：快照遍历自身异常
     * warn 后直接返回，等下一轮——「单轮异常隔离」语义。
     */
    void cleanupExpired() {
        try {
            long now = clock.getAsLong();
            long idleTimeoutMs = properties.getIdleTimeout() * 1000L;
            long maxLifetimeMs = properties.getMaxLifetime() * 1000L;
            for (Map.Entry<String, CubeSandboxProvider.SandboxLease> entry
                    : provider.leaseView().entrySet()) {
                String sessionId = entry.getKey();
                CubeSandboxProvider.SandboxLease lease = entry.getValue();
                boolean idleExpired = now - lease.lastActiveAtMillis() > idleTimeoutMs;
                boolean lifetimeExpired = now - lease.createdAtMillis() > maxLifetimeMs;
                if (!idleExpired && !lifetimeExpired) {
                    continue;
                }
                try {
                    provider.killLease(sessionId);
                    log.info("Cube 沙箱清扫回收: session={} sandbox={} 原因={}",
                            sessionId, lease.sandboxId(), idleExpired ? "idle超时" : "寿命超限");
                } catch (RuntimeException e) {
                    log.warn("Cube 沙箱清扫回收失败（租约已放回注册表，下轮清扫重试；进程重启对账亦可兜底）"
                            + ": session={} err={}", sessionId, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Cube 沙箱清扫轮异常（本轮放弃，等待下一轮）: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 启动孤儿对账

    /**
     * 启动孤儿对账：{@code listSandboxEntries()} 全量条目逐条<b>五重排除</b>，剩余＝本系统
     * 孤儿业务箱 ⇒ delete（单条失败 warn 继续）。
     * <p>
     * 排除序与防线（安全侧从严）：① admin 箱 ② owner 缺失/不匹配 ③ session-id 缺失/空
     * ④ {@code instance-id} 等于本进程（I-1：多实例/多环境共用平台时他进程活箱由此拦住；
     * 缺失＝存量代际照样进候选）⑤ {@code provider.leaseView()} 已含该 session-id
     * （I-2：webserver 先于本对账就绪的窗口内，请求线程刚 ensure 的活箱必须跳过——
     * 否则删掉活箱还留着 stale 租约，该会话自此 exec 404 不可自愈）。
     * <p>
     * 🔴 判据<b>只认回显的 metadata</b>：metadata 空 map（CubeMaster list 实测不回显
     * 自定义 metadata）⇒ 保守跳过＋info 记录候选（身份不明）——平台两侧
     * 现实（回显/不回显）都无害：不回显 ⇒ 全跳过零误杀，回显 ⇒ 孤儿精确回收；漏网由
     * 终态 destroy＋清扫轮兜底。list 整体异常上抛（{@link #start} 捕获吞＋warn）。
     */
    void reconcileOrphans() {
        List<CubeHttpClient.SandboxEntry> entries = client.listSandboxEntries();
        String ownerId = properties.getOwnerId();
        String myInstanceId = client.getInstanceId();
        Map<String, CubeSandboxProvider.SandboxLease> activeLeases = provider.leaseView();
        int reclaimed = 0;
        for (CubeHttpClient.SandboxEntry entry : entries) {
            try {
                Map<String, String> metadata = entry.metadata() == null ? Map.of() : entry.metadata();
                // ① 管理箱：绝不可杀
                if (CubeHttpClient.ADMIN_METADATA_ROLE.equals(metadata.get(META_ROLE))) {
                    continue;
                }
                // ② owner 缺失或不匹配（含 metadata 空 map）＝他系统/存量无 owner 箱：身份不明保守跳过
                String owner = metadata.get(META_OWNER);
                if (owner == null || !owner.equals(ownerId)) {
                    log.info("Cube 沙箱对账候选身份不明，保守跳过: sandbox={} owner={}",
                            entry.sandboxId(), owner == null ? "缺席" : owner);
                    continue;
                }
                // ③ session-id 缺失或空＝无会话归属：保守跳过
                String sessionId = metadata.get(META_SESSION_ID);
                if (sessionId == null || sessionId.isBlank()) {
                    log.info("Cube 沙箱对账候选无会话归属，保守跳过: sandbox={}", entry.sandboxId());
                    continue;
                }
                // ④ instance-id 等于本进程＝本进程活箱（I-1 他实例误杀防线；缺失＝存量代际照常可清）
                if (myInstanceId.equals(metadata.get(META_INSTANCE_ID))) {
                    continue;
                }
                // ⑤ 本进程租约表已含该会话＝活跃使用中（I-2 自伤竞态防线；与 ④ 双保险）
                if (activeLeases.containsKey(sessionId)) {
                    log.debug("Cube 沙箱对账跳过本进程活跃会话: sandbox={} session={}",
                            entry.sandboxId(), sessionId);
                    continue;
                }
                client.delete(entry.sandboxId());
                reclaimed++;
                log.info("Cube 沙箱启动对账回收孤儿: sandbox={} session={} instance={}",
                        entry.sandboxId(), sessionId,
                        metadata.getOrDefault(META_INSTANCE_ID, "存量代际"));
            } catch (RuntimeException e) {
                log.warn("Cube 沙箱启动对账单条失败（继续下一条）: sandbox={} err={}",
                        entry.sandboxId(), e.getMessage());
            }
        }
        log.info("Cube 沙箱启动孤儿对账完成: 扫描={} 回收={}", entries.size(), reclaimed);
    }
}
