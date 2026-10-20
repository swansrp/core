package com.bidr.authorization.tenant.job;

import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.cache.lock.CacheLockProvider;
import org.junit.After;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: TenantJobSchedulerTest
 * Description: 调度器离线单测（纯 JUnit4，2026-10-20 下沉 core）——假钟＋直驱时间轮的
 * 确定性断言（worker 是真实双线程池，fire 异步到达，轮询等待轨迹到位）。
 *
 * <p>直驱语义＝<b>先拨钟到下一触发点再步进</b>：scheduleNext 以
 * {@code ZonedDateTime.now(clock)} 计算延迟（登记在步进之前的槽里）——若步进先于拨钟，
 * 件会落进"rounds=0 但未到期"的留槽腿、要等 cursor 绕一整圈才再扫到（轮子的竞态边界
 * 语义），测试统一规避之。cron 统一 {@code "0/1 * * * * *"}（每秒），首个触发点＝
 * 当前整秒＋1s。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TenantJobSchedulerTest {

    /** 可拨动的假钟（起点固定，测试间互不污染） */
    private static final class MutableClock extends Clock {
        private long millis = ZonedDateTime.of(2026, 10, 20, 10, 0, 30, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        /** 拨到下一个整秒边界（cron "0/1 * * * * *" 的下一触发点） */
        void toNextSecond() {
            long remainder = millis % 1000L;
            millis += (1000L - remainder);
        }

        void advanceSeconds(int seconds) {
            millis += seconds * 1000L;
        }
    }

    /** 记录执行轨迹的桩任务：cron＝每秒 */
    private static class StubJob implements TenantScopedJob {
        private final String key;
        private final List<Long> executedTenants;
        private final List<Long> contextInExecute;

        StubJob(String key, List<Long> executedTenants, List<Long> contextInExecute) {
            this.key = key;
            this.executedTenants = executedTenants;
            this.contextInExecute = contextInExecute;
        }

        @Override
        public String jobKey() {
            return key;
        }

        @Override
        public String cron() {
            return "0/1 * * * * *";
        }

        @Override
        public void execute(Long tenantId) {
            executedTenants.add(tenantId);
            contextInExecute.add(TenantContext.currentTenantId());
        }
    }

    private final MutableClock clock = new MutableClock();

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    @Test
    public void firesPerTenantWithTenantContext() throws Exception {
        List<Long> executed = new CopyOnWriteArrayList<>();
        List<Long> contextInExecute = new CopyOnWriteArrayList<>();
        StubJob job = new StubJob("demo", executed, contextInExecute);
        TenantJobScheduler scheduler = new TenantJobScheduler(
                java.util.Collections.singletonList(job), () -> Arrays.asList(1L, 2L), clock, new StubLockProvider(), 600L, false);

        clock.toNextSecond();
        scheduler.start();
        assertTrue(scheduler.isRunning());

        // 登记面以 now=整秒算出下一触发点＝+1s；先拨钟到触发点再步进
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        awaitSize(executed, 2);
        assertEquals(Arrays.asList(1L, 2L), executed);
        assertEquals(Arrays.asList(1L, 2L), contextInExecute);

        // 第二触发点：下一轮登记在上一轮 finally 里——awaitSize 只保证 execute 全返，
        // 给 finally 一小段落定余量再拨钟步进
        Thread.sleep(100);
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        awaitSize(executed, 4);
        assertEquals(Arrays.asList(1L, 2L, 1L, 2L), executed);
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    public void singleTenantFailureDoesNotStopOthers() throws Exception {
        List<Long> executed = new CopyOnWriteArrayList<>();
        List<Long> context = new CopyOnWriteArrayList<>();
        StubJob job = new StubJob("flaky", executed, context) {
            @Override
            public void execute(Long tenantId) {
                if (Long.valueOf(1L).equals(tenantId)) {
                    throw new IllegalStateException("该租户数据坏了");
                }
                super.execute(tenantId);
            }
        };
        TenantJobScheduler scheduler = new TenantJobScheduler(
                java.util.Collections.singletonList(job), () -> Arrays.asList(1L, 2L), clock, new StubLockProvider(), 600L, false);
        clock.toNextSecond();
        scheduler.start();

        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        awaitSize(executed, 1);
        assertEquals(java.util.Collections.singletonList(2L), executed);
        scheduler.stop();
    }

    @Test
    public void misconfigurationFailsFast() {
        StubJob badCron = new StubJob("bad", new CopyOnWriteArrayList<Long>(), new CopyOnWriteArrayList<Long>()) {
            @Override
            public String cron() {
                return "not-a-cron";
            }
        };
        try {
            new TenantJobScheduler(java.util.Collections.<TenantScopedJob>singletonList(badCron),
                    () -> java.util.Collections.singletonList(1L), clock, new StubLockProvider(), 600L, false);
            fail("cron 不可解析必须 fail-fast");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("cron 不可解析"));
        }

        StubJob good = new StubJob("good", new CopyOnWriteArrayList<Long>(), new CopyOnWriteArrayList<Long>());
        try {
            new TenantJobScheduler(java.util.Collections.<TenantScopedJob>singletonList(good), null, clock, new StubLockProvider(), 600L, false);
            fail("有任务无 ActiveTenantSource 必须 fail-fast");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("ActiveTenantSource"));
        }
    }

    @Test
    public void idleWhenNoJobs_andDisabledJobNotScheduled() throws Exception {
        TenantJobScheduler idle = new TenantJobScheduler(
                java.util.Collections.<TenantScopedJob>emptyList(), () -> java.util.Collections.singletonList(1L),
                clock,
                new StubLockProvider(), 600L, false);
        idle.start();
        assertTrue(idle.isRunning());
        idle.stop();

        List<Long> executed = new CopyOnWriteArrayList<>();
        List<Long> context = new CopyOnWriteArrayList<>();
        StubJob disabled = new StubJob("off", executed, context) {
            @Override
            public boolean enabled() {
                return false;
            }
        };
        TenantJobScheduler scheduler = new TenantJobScheduler(
                java.util.Collections.singletonList(disabled), () -> Arrays.asList(1L, 2L), clock, new StubLockProvider(), 600L, false);
        clock.toNextSecond();
        scheduler.start();
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        clock.advanceSeconds(2);
        scheduler.wheelForTest().advanceOnce();
        scheduler.wheelForTest().advanceOnce();
        assertTrue(executed.isEmpty());
        scheduler.stop();
    }

    @Test
    public void stopSilencesFires() throws Exception {
        List<Long> executed = new CopyOnWriteArrayList<>();
        List<Long> context = new CopyOnWriteArrayList<>();
        StubJob job = new StubJob("q", executed, context);
        TenantJobScheduler scheduler = new TenantJobScheduler(
                java.util.Collections.singletonList(job), () -> java.util.Collections.singletonList(1L),
                clock,
                new StubLockProvider(), 600L, false);
        clock.toNextSecond();
        scheduler.start();
        scheduler.stop();
        // stop 后登记件仍在轮上（轮子照扫），但 fire 的 running 闸静默之
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        Thread.sleep(200);
        assertTrue(executed.isEmpty());
    }

    /** 锁桩：可切换"全拒"形态模拟别的实例持有；记录 unlock 轨迹 */
    private static final class StubLockProvider implements CacheLockProvider {
        final Set<Object> held = Collections.synchronizedSet(new HashSet<Object>());
        final List<String> unlocked = new ArrayList<>();
        volatile boolean refuseAll;

        @Override
        public Object tryLock(String lockKey, long waitTime, long leaseTime) {
            if (refuseAll || !held.add(lockKey)) {
                return null;
            }
            return lockKey;
        }

        @Override
        public void unlock(Object lockToken) {
            held.remove(lockToken);
            unlocked.add(String.valueOf(lockToken));
        }
    }

    @Test
    public void distributedLockGuardsTriggerPoint() throws Exception {
        List<Long> executed = new CopyOnWriteArrayList<>();
        List<Long> context = new CopyOnWriteArrayList<>();
        StubJob job = new StubJob("locked", executed, context);
        StubLockProvider lockProvider = new StubLockProvider();
        TenantJobScheduler scheduler = new TenantJobScheduler(
                java.util.Collections.<TenantScopedJob>singletonList(job),
                () -> java.util.Collections.singletonList(1L), clock, lockProvider, 600L, false);

        // 触发点 1：锁被其它实例持有 ⇒ 本轮整轮跳过（不执行），但下一轮照常登记
        clock.toNextSecond();
        scheduler.start();
        lockProvider.refuseAll = true;
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        Thread.sleep(200);
        // 锁被持有时本轮不得执行
        assertTrue(executed.isEmpty());

        // 触发点 2：锁可得 ⇒ 正常执行；本轮锁在轮末释放
        lockProvider.refuseAll = false;
        clock.advanceSeconds(1);
        scheduler.wheelForTest().advanceOnce();
        awaitSize(executed, 1);
        // 锁恢复后该轮正常执行
        assertEquals(java.util.Collections.singletonList(1L), executed);
        // 成功轮次须释放触发点锁（键含任务标识与触发点时刻）
        assertTrue(lockProvider.unlocked.size() >= 1 && lockProvider.unlocked.get(0).contains("tenant-job:locked:"));
        scheduler.stop();
    }

    private static void awaitSize(List<?> list, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (list.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(expected, list.size());
    }
}
