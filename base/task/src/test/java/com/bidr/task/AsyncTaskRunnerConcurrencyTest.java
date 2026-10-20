package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 机器腿（M16 修复轮）：{@code tick}（调度线程）与 {@code base-task-wake}（唤醒线程）
 * 会并发 drain 同一类型——{@link AsyncTaskRunner#ownedRunning} 是纯 SELECT，两条线程能同时
 * 取到同一批在途任务、把同一阶段体执行两遍。
 *
 * <p>🔴 这里<b>不再用 {@code Runnable::run} 当执行器</b>：真开两条线程，一条卡在阶段体里，
 * 另一条必须<b>当场返回</b>（{@code tryLock} 抢不到即走），而不是排队等锁——排队等锁虽然也不会
 * 重复执行，但会把唤醒线程堵在跑批线程后面，长阶段一多就积压。</p>
 *
 * @author sharp
 */
class AsyncTaskRunnerConcurrencyTest {

    /** 卡住第一阶段：进体计数、等放行信号 */
    @AsyncTaskType(value = "SLOW_FLOW", maxRetry = 2)
    static class SlowFlow {

        final AtomicInteger bodyRuns = new AtomicInteger();

        final CountDownLatch entered = new CountDownLatch(1);

        final CountDownLatch release = new CountDownLatch(1);

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            bodyRuns.incrementAndGet();
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StageOutcome.done();
        }
    }

    /** 另一条类型的短流程（互不阻塞腿用） */
    @AsyncTaskType(value = "FAST_FLOW", maxRetry = 2)
    static class FastFlow {

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @Test
    @DisplayName("A2：唤醒线程撞上已持锁的 tick → 当场返回，同一阶段体只执行一次")
    void concurrentDrainDoesNotDoubleExecuteStage() throws Exception {
        SlowFlow slow = new SlowFlow();
        TestStack stack = new TestStack(false, 1000, slow);
        stack.addObserver(stack.recordingObserver());
        stack.service.submit("SLOW_FLOW", "ref-slow", null, "E1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch firstDone = new CountDownLatch(1);
            pool.submit(() -> {
                try {
                    stack.runner.drain(stack.registry.require("SLOW_FLOW"));
                } finally {
                    firstDone.countDown();
                }
            });
            assertTrue(slow.entered.await(5, TimeUnit.SECONDS), "第一阶段必须已被执行（tick 侧持锁）");

            // 第二条线程（唤醒线程形态）：抢不到锁即返回，不排队、不重复执行
            CountDownLatch secondReturned = new CountDownLatch(1);
            pool.submit(() -> {
                try {
                    stack.runner.drainNow("SLOW_FLOW");
                } finally {
                    secondReturned.countDown();
                }
            });
            assertTrue(secondReturned.await(2, TimeUnit.SECONDS),
                    "唤醒线程不得排队等锁（tryLock 抢不到即走）");
            assertEquals(1, slow.bodyRuns.get(), "第一阶段体只许执行一次（不得被两条线程各推一遍）");

            slow.release.countDown();
            assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        } finally {
            slow.release.countDown();
            pool.shutdownNow();
        }

        assertEquals(1, slow.bodyRuns.get(), "收口后仍只执行一次");
        assertEquals("SUCCESS", stack.service.find("SLOW_FLOW", "ref-slow").getStatus());
        org.assertj.core.api.Assertions.assertThat(stack.observerEvents).containsExactly("SUCCESS:ref-slow");
    }

    @Test
    @DisplayName("A2：按类型加锁——一条类型卡住时另一条类型照常推进")
    void differentTypesDoNotBlockEachOther() throws Exception {
        SlowFlow slow = new SlowFlow();
        FastFlow fast = new FastFlow();
        TestStack stack = new TestStack(false, 1000, slow, fast);
        stack.service.submit("SLOW_FLOW", "ref-slow2", null, "E1");
        stack.service.submit("FAST_FLOW", "ref-fast", null, "E1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> stack.runner.drain(stack.registry.require("SLOW_FLOW")));
            assertTrue(slow.entered.await(5, TimeUnit.SECONDS));

            CountDownLatch fastDone = new CountDownLatch(1);
            pool.submit(() -> {
                try {
                    stack.runner.drainNow("FAST_FLOW");
                } finally {
                    fastDone.countDown();
                }
            });
            assertTrue(fastDone.await(2, TimeUnit.SECONDS), "不同类型不共用一把锁");

            slow.release.countDown();
        } finally {
            slow.release.countDown();
            pool.shutdownNow();
        }

        assertEquals("SUCCESS", stack.service.find("FAST_FLOW", "ref-fast").getStatus());
        assertEquals(1, slow.bodyRuns.get());
    }
}
