package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判据③＋重试预算的机器腿（§4.2.2「异常即终态判据」）：可重试异常 → FAILED＋退避；
 * 预算耗尽 → DEAD；不可重试异常 → 直接 DEAD（负向腿钉「不许静默兜底」）；
 * noRetryOn 按类型补口；租约超时回收（失联不吃退避、超预算转 DEAD）。
 *
 * @author sharp
 */
class AsyncTaskRetryTriageTest {

    /** 临时故障：retryable=true（交给预算） */
    static class TransientFault extends RuntimeException implements RetryAware {
        TransientFault() {
            super("temporary");
        }

        @Override
        public boolean isRetryable() {
            return true;
        }
    }

    /** 重跑结果不变的失败：retryable=false（直接 DEAD） */
    static class PermanentFault extends RuntimeException implements RetryAware {
        PermanentFault() {
            super("permanent");
        }

        @Override
        public boolean isRetryable() {
            return false;
        }
    }

    /** 可引爆的单阶段流程（同一阶段反复炸直到成功），noRetryOn 钉 IllegalArgumentException */
    @AsyncTaskType(value = "TRIAGE", maxRetry = 2)
    static class TriageFlow {

        volatile Supplier<RuntimeException> bomb = () -> null;

        int runs = 0;

        @AsyncStage(value = "WORK", order = 1, noRetryOn = IllegalArgumentException.class)
        public StageOutcome work(SaAsyncTask task, AsyncStageScope scope) {
            runs++;
            RuntimeException e = bomb.get();
            if (e != null) {
                throw e;
            }
            return StageOutcome.done();
        }
    }

    private final TriageFlow flow = new TriageFlow();

    private final TestStack stack = new TestStack(false, 1000, flow);

    /** 可重试异常 → FAILED＋退避（attempt+1、租约交回），观察者不发 FAILED 档 */
    @Test
    @DisplayName("判据③-a：RetryAware=true → FAILED＋退避，FAILED 不发观察者")
    void retryableFailureBacksOff() {
        stack.addObserver(stack.recordingObserver());
        flow.bomb = TransientFault::new;
        stack.service.submit("TRIAGE", "ref-tr", null, "E1");

        stack.runner.tick();
        SaAsyncTask failed = stack.service.find("TRIAGE", "ref-tr");
        assertEquals("FAILED", failed.getStatus());
        assertEquals(1, failed.getAttempt());
        assertNotNull(failed.getNextRunAt(), "可重试失败必须带退避时间");
        assertTrue(failed.getNextRunAt().after(new Date()));
        assertNull(failed.getOwner(), "失败即交回队列：不等租约自然过期");
        assertThat(stack.observerEvents).as("预算内的 FAILED 不发观察者（防同一任务通知 N 次）").isEmpty();

        // 退避过后重试成功 → SUCCESS
        long jump = failed.getNextRunAt().getTime() - System.currentTimeMillis() + 1_000L;
        stack.repo.clock = () -> new Date(System.currentTimeMillis() + jump);
        flow.bomb = () -> null;
        stack.runner.tick();
        assertEquals("SUCCESS", stack.service.find("TRIAGE", "ref-tr").getStatus());
        assertThat(stack.observerEvents).as("SUCCESS 档必发").containsExactly("SUCCESS:ref-tr");
    }

    /** 未实现 RetryAware 的异常＝默认可重试（🔴 不许静默兜底：静默会把「暂时没页」固化成「永远没页」） */
    @Test
    @DisplayName("判据③-b：裸异常默认可重试（FAILED＋退避，不静默兜底）")
    void plainExceptionDefaultsToRetryable() {
        flow.bomb = () -> new NullPointerException("npe");
        stack.service.submit("TRIAGE", "ref-plain", null, "E1");
        stack.runner.tick();
        SaAsyncTask failed = stack.service.find("TRIAGE", "ref-plain");
        assertEquals("FAILED", failed.getStatus());
        assertEquals(1, failed.getAttempt());
        assertNotNull(failed.getNextRunAt());
    }

    /** 预算耗尽 → DEAD：maxRetry=2，第 3 次失败不再退避 */
    @Test
    @DisplayName("判据③-c：预算耗尽 → DEAD（不再自动重试）")
    void exhaustedBudgetGoesDead() {
        stack.addObserver(stack.recordingObserver());
        flow.bomb = TransientFault::new;
        stack.service.submit("TRIAGE", "ref-exhaust", null, "E1");

        stack.runner.tick();
        assertEquals("FAILED", stack.service.find("TRIAGE", "ref-exhaust").getStatus());
        assertEquals(1, stack.service.find("TRIAGE", "ref-exhaust").getAttempt());
        jumpPastBackoff("ref-exhaust");
        stack.runner.tick();
        assertEquals("FAILED", stack.service.find("TRIAGE", "ref-exhaust").getStatus());
        assertEquals(2, stack.service.find("TRIAGE", "ref-exhaust").getAttempt());

        jumpPastBackoff("ref-exhaust");
        stack.runner.tick();
        SaAsyncTask dead = stack.service.find("TRIAGE", "ref-exhaust");
        assertEquals("DEAD", dead.getStatus());
        assertEquals(3, dead.getAttempt(), "第 3 次失败＝预算(maxRetry=2)耗尽后的一次记账");
        assertNull(dead.getNextRunAt(), "DEAD 必须真清空退避时间（不让人误以为还会再跑）");
        assertThat(stack.observerEvents).as("DEAD 档必发观察者").containsExactly("DEAD:ref-exhaust");
    }

    /** 不可重试异常 → 直接 DEAD（不吃预算、不退避） */
    @Test
    @DisplayName("判据③-d：RetryAware=false → 直接 DEAD 带原因")
    void nonRetryableGoesDeadImmediately() {
        flow.bomb = PermanentFault::new;
        stack.service.submit("TRIAGE", "ref-perm", null, "E1");
        stack.runner.tick();
        SaAsyncTask dead = stack.service.find("TRIAGE", "ref-perm");
        assertEquals("DEAD", dead.getStatus());
        assertEquals("permanent", dead.getError());
        assertNull(dead.getNextRunAt());
    }

    /** noRetryOn 按类型补口：命中即 DEAD（不用改异常族） */
    @Test
    @DisplayName("noRetryOn：命中类型直接 DEAD")
    void noRetryOnTypeGoesDead() {
        flow.bomb = () -> new IllegalArgumentException("bad input");
        stack.service.submit("TRIAGE", "ref-noretry", null, "E1");
        stack.runner.tick();
        assertEquals("DEAD", stack.service.find("TRIAGE", "ref-noretry").getStatus());
    }

    /** 租约超时回收（实例失联）：计一次尝试并立即回队（失联不是业务失败，不吃退避）；超预算则 DEAD */
    @Test
    @DisplayName("回收：失联回队不吃退避；预算尽转 DEAD＋观察者必发")
    void reapsExpiredLease() {
        stack.addObserver(stack.recordingObserver());
        flow.bomb = TransientFault::new;
        stack.service.submit("TRIAGE", "ref-reap", null, "E1");
        stack.runner.tick();
        assertEquals("FAILED", stack.service.find("TRIAGE", "ref-reap").getStatus());

        // 跑到 RUNNING 后失联：先把退避跳过后抢到、再用长租约内的时间冻结模拟中途失联
        jumpPastBackoff("ref-reap");
        flow.bomb = () -> null;
        stack.runner.tick();
        assertEquals("SUCCESS", stack.service.find("TRIAGE", "ref-reap").getStatus());

        // 失联腿另起一行：让阶段保持 RUNNING（pending 长退避），再把时钟跳过租约
        TestStack.ThreeStageFlow slow = new TestStack.ThreeStageFlow();
        slow.secondOutcome = () -> StageOutcome.pending(600_000L);
        TestStack reapStack = new TestStack(false, 1000, slow);
        reapStack.addObserver(reapStack.recordingObserver());
        reapStack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-lost", null, "E1");
        reapStack.runner.tick();
        reapStack.runner.tick();
        assertEquals("RUNNING", reapStack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-lost").getStatus());

        long jump = 1_000_000L; // 超过租约（900s）与退避（600s）
        reapStack.repo.clock = () -> new Date(System.currentTimeMillis() + jump);
        reapStack.runner.tick();
        SaAsyncTask reaped = reapStack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-lost");
        assertEquals("FAILED", reaped.getStatus());
        assertEquals(1, reaped.getAttempt());
        assertEquals("ref-lost", reaped.getBizRef());
        assertTrue(reaped.getNextRunAt() != null && !reaped.getNextRunAt().after(new Date(System.currentTimeMillis() + jump)),
                "失联回队立即重跑（不吃 60s 退避）");
    }

    private void jumpPastBackoff(String bizRef) {
        SaAsyncTask row = stack.service.find("TRIAGE", bizRef);
        long jump = row.getNextRunAt().getTime() - System.currentTimeMillis() + 1_000L;
        stack.repo.clock = () -> new Date(System.currentTimeMillis() + jump);
    }
}
