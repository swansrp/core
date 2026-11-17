package com.bidr.cube;

import com.bidr.cube.admin.AdminSandboxManager;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: SandboxJanitorTest
 * Description: SandboxJanitor 行为钉桩（mock {@link CubeHttpClient}＋注入时钟，不打外网）：
 * <ul>
 *   <li><b>启动孤儿对账三重排除</b>：role=cube-admin 管理箱绝不杀；owner 缺失/不匹配
 *       （含 metadata 空 map＝CubeMaster list 剥离自定义 metadata 的真机现实）保守跳过
 *       （🔴 宁可漏杀不可误杀）；owner 匹配但 session-id 缺失保守跳过；只有
 *       owner＋session-id 双全的本系统孤儿才 delete；单条 delete 失败 warn 继续；</li>
 *   <li><b>清扫双判据</b>（时间注入）：idle 超时杀／寿命超限杀（idle 未超也杀）／未超不杀；
 *       回收后注册表逐出（leaseView 断言）；</li>
 *   <li><b>单轮异常隔离</b>：一条 killLease 抛异常不中断其余条目；</li>
 *   <li><b>SmartLifecycle 生命周期自查（静态闸门 R-2）</b>：start＝先跑启动对账（对账失败
 *       吞掉不影响启动）再起调度；stop＝isRunning 落 false＋调度线程池终止。</li>
 * </ul>
 * 租约建账复用 Provider 对账通道（{@code ensure}→{@code listSandboxIds} 命中即建账），
 * 保证 Provider/Janitor 协作面同生产装配（同一 client 实例）。
 *
 * @author Sharp
 * @since 2026/11/17
 */
class SandboxJanitorTest {

    /** 测试用 ownerId（与 properties.getOwnerId() 一致的对账匹配值） */
    private static final String OWNER = "epc-ai";

    /** 本进程实例身份（I-1 排除④判据；mock 缺省 null 会让对账判据 NPE，setUp 统一 stub） */
    private static final String MY_INSTANCE = "inst-test-mine";

    private CubeHttpClient client;
    private CubeSandboxProperties properties;

    @BeforeEach
    void setUp() {
        client = mock(CubeHttpClient.class);
        properties = new CubeSandboxProperties();
        properties.setOwnerId(OWNER);
        when(client.getInstanceId()).thenReturn(MY_INSTANCE);
    }

    /** 经 Provider 对账通道给活跃注册表建租约（listSandboxIds 命中 ⇒ createdAt=lastActiveAt=建账时点） */
    private CubeSandboxProvider leasedProvider(String sessionId, String sandboxId, long baseMillis) {
        when(client.listSandboxIds("session-id", sessionId)).thenReturn(List.of(sandboxId));
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), properties);
        provider.ensure(sessionId, List.of());
        assertTrue(provider.leaseView().containsKey(sessionId), "前置：租约建账成功");
        assertTrue(provider.leaseView().get(sessionId).createdAtMillis() >= baseMillis,
                "前置：建账时点不早于测试基准时钟");
        return provider;
    }

    private SandboxJanitor janitor(CubeSandboxProvider provider, AtomicLong now) {
        return new SandboxJanitor(provider, client, properties, now::get);
    }

    /** list 全量条目便捷构造（state 对对账判定无影响，固定 running） */
    private static CubeHttpClient.SandboxEntry entry(String sandboxId, Map<String, String> metadata) {
        return new CubeHttpClient.SandboxEntry(sandboxId, metadata, "running");
    }

    // ---------------------------------------------------------------- 启动孤儿对账（三重排除）

    @Test
    @DisplayName("对账排除①：role=cube-admin 管理箱 ⇒ 绝不可杀（零 delete）")
    void reconcileExcludesAdminBox() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-admin", Map.of("role", "cube-admin", "owner", OWNER))));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client, never()).delete(anyString());
    }

    @Test
    @DisplayName("对账排除②：owner 不匹配／缺失／metadata 空 map ⇒ 身份不明保守跳过（零 delete——宁可漏杀不可误杀）")
    void reconcileSkipsUnidentifiableOwner() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-foreign", Map.of("owner", "other-system", "session-id", "s-1")),
                entry("sbx-ownerless", Map.of("session-id", "s-2")),
                entry("sbx-nometadata", Map.of())));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client, never()).delete(anyString());
    }

    @Test
    @DisplayName("对账排除③：owner 匹配但 session-id 缺失 ⇒ 保守跳过（零 delete）")
    void reconcileSkipsOwnerMatchedBoxWithoutSessionId() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-nosession", Map.of("owner", OWNER))));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client, never()).delete(anyString());
    }

    @Test
    @DisplayName("本系统孤儿业务箱（owner＋session-id 双全）⇒ delete 精确回收，同场管理箱不动")
    void reconcileDeletesOurOrphan() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-admin", Map.of("role", "cube-admin", "owner", OWNER)),
                entry("sbx-orphan", Map.of("owner", OWNER, "session-id", "s-2"))));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client).delete("sbx-orphan");
        verify(client, never()).delete("sbx-admin");
    }

    @Test
    @DisplayName("对账排除⑤：本进程租约表已含该 session-id（instance-id 异值/缺失）⇒ 活跃使用中保守跳过（I-2 自伤竞态防线）")
    void reconcileSkipsSessionAlreadyLeasedByThisProcess() {
        long base = System.currentTimeMillis();
        // 本进程活跃会话 s-active 已建租约；平台 list 回显它的 instance-id 为异值（如 list 回显滞后/他路径）
        CubeSandboxProvider provider = leasedProvider("s-active", "sbx-active", base);
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-active", Map.of("owner", OWNER, "session-id", "s-active",
                        "instance-id", "inst-other"))));

        janitor(provider, new AtomicLong()).reconcileOrphans();

        verify(client, never()).delete(anyString());
        assertTrue(provider.leaseView().containsKey("s-active"), "活跃租约必须原样保留");
    }

    @Test
    @DisplayName("对账排除④：instance-id 等于本进程 ⇒ 本进程活箱跳过（I-1 自伤/多实例防线）")
    void reconcileSkipsOwnInstanceBox() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-mine", Map.of("owner", OWNER, "session-id", "s-mine",
                        "instance-id", MY_INSTANCE))));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client, never()).delete(anyString());
    }

    @Test
    @DisplayName("对账回收他实例遗留：owner 匹配＋instance-id 异值（上进程代际）⇒ delete 回收")
    void reconcileReclaimsPreviousGenerationOrphan() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-prev", Map.of("owner", OWNER, "session-id", "s-prev",
                        "instance-id", "inst-previous-process"))));

        janitor(new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans();

        verify(client).delete("sbx-prev");
    }

    @Test
    @DisplayName("对账单条失败隔离：首条 delete 抛异常 ⇒ warn 继续回收第二条（不中断、不上抛）")
    void reconcileContinuesAfterSingleDeleteFailure() {
        when(client.listSandboxEntries()).thenReturn(List.of(
                entry("sbx-a", Map.of("owner", OWNER, "session-id", "s-a")),
                entry("sbx-b", Map.of("owner", OWNER, "session-id", "s-b"))));
        doThrow(new ServiceException("delete 沙箱失败 HTTP 500: boom")).when(client).delete("sbx-a");

        assertDoesNotThrow(() -> janitor(
                new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong()).reconcileOrphans());

        verify(client).delete("sbx-a");
        verify(client).delete("sbx-b");
    }

    // ---------------------------------------------------------------- 清扫轮（双判据＋隔离）

    @Test
    @DisplayName("清扫判据①：idle 超时（now−lastActiveAt＞idleTimeout）⇒ killLease 回收＋注册表逐出")
    void cleanupKillsIdleExpiredLease() {
        long base = System.currentTimeMillis();
        CubeSandboxProvider provider = leasedProvider("s-idle", "sbx-idle", base);
        // 快进 10s：idleTimeout=600s（缺省）已超（留足建账时延余量，毫秒级抖动不翻盘）
        AtomicLong now = new AtomicLong(base + 10_000 + 600 * 1000);

        janitor(provider, now).cleanupExpired();

        verify(client).delete("sbx-idle");
        assertFalse(provider.leaseView().containsKey("s-idle"), "回收后注册表必须逐出");
    }

    @Test
    @DisplayName("清扫判据②：寿命超限（now−createdAt＞maxLifetime，idle 判据未超）⇒ 仍回收")
    void cleanupKillsLifetimeExpiredLease() {
        long base = System.currentTimeMillis();
        properties.setMaxLifetime(1);      // 寿命封顶 1s：快进 2s 即超限（余量≥建账时延）
        properties.setIdleTimeout(600);    // idle 判据远未触发
        CubeSandboxProvider provider = leasedProvider("s-life", "sbx-life", base);
        AtomicLong now = new AtomicLong(base + 2_000);

        janitor(provider, now).cleanupExpired();

        verify(client).delete("sbx-life");
        assertFalse(provider.leaseView().containsKey("s-life"));
    }

    @Test
    @DisplayName("双判据都未超（刚建账＋快进 5s）⇒ 不杀（零 delete，租约保留）")
    void cleanupKeepsFreshLease() {
        long base = System.currentTimeMillis();
        CubeSandboxProvider provider = leasedProvider("s-fresh", "sbx-fresh", base);
        AtomicLong now = new AtomicLong(base + 5_000);   // idle 600s／lifetime 21600s 均未超

        janitor(provider, now).cleanupExpired();

        verify(client, never()).delete(anyString());
        assertTrue(provider.leaseView().containsKey("s-fresh"), "未过期租约必须保留");
    }

    @Test
    @DisplayName("清扫单条失败隔离：一条 killLease 抛异常 ⇒ 不中断其余（第二条照常回收）")
    void cleanupIsolatesSingleKillFailure() {
        long base = System.currentTimeMillis();
        properties.setIdleTimeout(0);   // 判据立即命中（now−t＞0 恒真），只借道建账时间基准
        CubeSandboxProvider provider = leasedProvider("s-a", "sbx-a", base);
        when(client.listSandboxIds("session-id", "s-b")).thenReturn(List.of("sbx-b"));
        provider.ensure("s-b", List.of());
        doThrow(new ServiceException("delete 沙箱失败 HTTP 500: boom")).when(client).delete("sbx-a");
        AtomicLong now = new AtomicLong(base + 60_000);

        assertDoesNotThrow(() -> janitor(provider, now).cleanupExpired());

        verify(client).delete("sbx-a");
        verify(client).delete("sbx-b");
        assertFalse(provider.leaseView().containsKey("s-b"), "异常条目之后的回收必须照常执行");
    }

    // ---------------------------------------------------------------- SmartLifecycle 生命周期（静态闸门 R-2）

    @Test
    @DisplayName("生命周期自查：start＝先跑启动对账（listSandboxEntries 恰一次）再起调度；对账失败吞掉不影响启动；stop＝isRunning 落 false＋调度线程池终止")
    void lifecycleStartRunsReconcileAndStopTerminatesScheduler() {
        when(client.listSandboxEntries())
                .thenThrow(new ServiceException("list 沙箱失败 HTTP 500: boom"));   // 对账失败场景
        properties.setCleanupInterval(3600);   // 测试窗口内不触发清扫轮
        SandboxJanitor janitor = janitor(
                new CubeSandboxProvider(client, mock(AdminSandboxManager.class), properties),
                new AtomicLong());

        assertDoesNotThrow(janitor::start, "对账失败必须被 start 吞掉（warn，不影响启动）");
        assertTrue(janitor.isRunning());
        verify(client).listSandboxEntries();   // 启动对账在 start 内先行恰一次

        janitor.stop();
        assertFalse(janitor.isRunning(), "stop 后 isRunning 必须落 false");
        assertTrue(janitor.schedulerTerminated(), "stop 后调度线程池必须终止（R-2 静态闸门）");
    }
}
