package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A3 所有权契约的机器腿（M16 修复轮）：
 * ① 迟到持有者的 {@code updateStage} 被拒（行仍属他人／已 FAILED）；
 * ② 两实例先后 {@code dead} 只发一次观察者通知（D12）；
 * ③ pending 退避大于租约时不被打成「实例失联」回收；
 * ④ 受理侧 {@code abort} 可终止 PENDING/RUNNING（不受 owner 谓词限制）；
 * ⑤ 受理侧 {@code sealExternal} 终态封存（A3.4）：只钉 SUCCESS/DEAD 终态行——
 *    FAILED/RUNNING 拒封（不做任何写入），已封过同值＝CAS 输家（false）。
 *
 * @author sharp
 */
class AsyncTaskOwnershipTest {

    /** 可换第二档行为的流程（失败腿用） */
    @AsyncTaskType(value = "OWN_FLOW", maxRetry = 3)
    static class OwnershipFlow {

        volatile Supplier<StageOutcome> secondOutcome = () -> StageOutcome.to("THIRD");

        @AsyncStage(value = "FIRST", order = 1)
        public StageOutcome first(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("SECOND");
        }

        @AsyncStage(value = "SECOND", order = 2)
        public StageOutcome second(SaAsyncTask task, AsyncStageScope scope) {
            return secondOutcome.get();
        }

        @AsyncStage(value = "THIRD", order = 3)
        public StageOutcome third(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    // ==================== ① 迟到持有者的写被拒 ====================

    @Test
    @DisplayName("A3-①：行已易主时迟到持有者的 updateStage 被拒（0 行、stage 不动）")
    void lateWriterRejectedWhenRowOwnedByOther() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-late", null, "E1");
        stack.runner.tick();

        SaAsyncTask running = stack.service.find("OWN_FLOW", "ref-late");
        assertEquals("SECOND", running.getStage());
        String lostOwner = running.getOwner();

        // 行已易主（真实库里是另一实例的 claim/回收写进去的 owner）
        stack.repo.handOver(handle.getId(), "other-instance");
        assertFalse(stack.repo.updateStage(handle.getId(), lostOwner, "THIRD"), "迟到持有者写不进去");
        assertEquals("SECOND", stack.service.find("OWN_FLOW", "ref-late").getStage(), "stage 不许被迟到写改掉");
        assertTrue(stack.repo.events.contains("update-stage-rejected:" + handle.getId()));
    }

    @Test
    @DisplayName("A3-①：行已 FAILED（owner 已清）时迟到持有者的 updateStage 被拒")
    void lateWriterRejectedWhenRowAlreadyFailed() {
        OwnershipFlow flow = new OwnershipFlow();
        flow.secondOutcome = () -> {
            throw new IllegalStateException("boom");
        };
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-failed", null, "E1");
        stack.runner.tick();
        String lostOwner = stack.service.find("OWN_FLOW", "ref-failed").getOwner();
        stack.runner.tick();

        SaAsyncTask failed = stack.service.find("OWN_FLOW", "ref-failed");
        assertEquals("FAILED", failed.getStatus());
        assertNull(failed.getOwner(), "失败即交回队列");
        assertFalse(stack.repo.updateStage(handle.getId(), lostOwner, "THIRD"));
        assertEquals("FAILED", stack.service.find("OWN_FLOW", "ref-failed").getStatus(), "迟到写不得把行拉回 RUNNING");
        assertEquals("SECOND", stack.service.find("OWN_FLOW", "ref-failed").getStage());
    }

    // ==================== ② 两实例先后 dead 只发一次 ====================

    @Test
    @DisplayName("A3-②：两实例先后 dead 只发一次观察者通知（D12）")
    void terminalNotificationFiresOnce() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        stack.addObserver(stack.recordingObserver());
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-d12", null, "E1");
        stack.runner.tick();

        SaAsyncTask lost = stack.service.find("OWN_FLOW", "ref-d12");
        String lostOwner = lost.getOwner();

        // 实例 B 接手并收口 DEAD
        stack.repo.handOver(handle.getId(), "instance-B");
        SaAsyncTask asB = stack.service.find("OWN_FLOW", "ref-d12");
        stack.service.dead(asB, new IllegalStateException("B 判定不可重试"));
        assertThat(stack.observerEvents).as("DEAD 档必发一次").containsExactly("DEAD:ref-d12");

        // 实例 A 迟到收口：写被拒、不得再发一次通知
        lost.setOwner(lostOwner);
        stack.service.dead(lost, new IllegalStateException("A 迟到"));
        assertThat(stack.observerEvents).as("同一行只发一次终态通知").hasSize(1);
        assertEquals("DEAD", stack.service.find("OWN_FLOW", "ref-d12").getStatus());
    }

    // ==================== ③ pending 退避 > 租约不被回收 ====================

    /** 短租约流程：pending 退避远大于租约（配置一改就中的隐雷） */
    @AsyncTaskType(value = "SHORT_LEASE", maxRetry = 3, leaseMs = 1_000L)
    static class ShortLeaseFlow {

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("TWO");
        }

        @AsyncStage(value = "TWO", order = 2)
        public StageOutcome two(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.pending(600_000L);
        }

        @AsyncStage(value = "THREE", order = 3)
        public StageOutcome three(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @Test
    @DisplayName("A3-③：pending 退避(600s) > 租约(1s) 时不被 expiredRunning 当失联回收")
    void pendingBackoffLongerThanLeaseIsNotReaped() {
        TestStack stack = new TestStack(false, 1000, new ShortLeaseFlow());
        stack.service.submit("SHORT_LEASE", "ref-backoff", null, "E1");
        stack.runner.tick(); // ONE → TWO
        stack.runner.tick(); // TWO → pending(600s)，租约延到 max(1s, 600s)
        SaAsyncTask pending = stack.service.find("SHORT_LEASE", "ref-backoff");
        assertEquals("RUNNING", pending.getStatus());
        Date nextRunAt = pending.getNextRunAt();
        assertTrue(nextRunAt != null && nextRunAt.after(new Date()));

        // 跳过原租约（1s）但仍远早于退避与延后的租约：补偿扫描不得回收
        stack.repo.clock = () -> new Date(System.currentTimeMillis() + 10_000L);
        stack.runner.tick();
        SaAsyncTask stillPending = stack.service.find("SHORT_LEASE", "ref-backoff");
        assertEquals("RUNNING", stillPending.getStatus(), "退避未到＝在途，不许当失联回收");
        assertEquals(0, stillPending.getAttempt());
        assertEquals("TWO", stillPending.getStage());
    }

    // ==================== ④ 受理侧 abort ====================

    @Test
    @DisplayName("A3-④：受理侧 abort 终止 PENDING 与 RUNNING 行，已终态行不再改、不重复通知")
    void acceptSideAbortTerminatesPendingAndRunning() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        stack.addObserver(stack.recordingObserver());

        SaAsyncTask pending = stack.service.submit("OWN_FLOW", "ref-abort-pending", null, "E1");
        assertTrue(stack.service.abort(pending, "用户当场取消"), "PENDING 行无 owner，仍须可作废");
        SaAsyncTask abortedPending = stack.service.find("OWN_FLOW", "ref-abort-pending");
        assertEquals("DEAD", abortedPending.getStatus());
        assertEquals("用户当场取消", abortedPending.getError());
        assertNull(abortedPending.getOwner());
        assertNull(abortedPending.getNextRunAt());

        SaAsyncTask running = stack.service.submit("OWN_FLOW", "ref-abort-running", null, "E1");
        stack.runner.tick(); // 抢到并推进：RUNNING
        assertEquals("SECOND", stack.service.find("OWN_FLOW", "ref-abort-running").getStage());
        assertTrue(stack.service.abort(running, "用户当场取消"));
        assertEquals("DEAD", stack.service.find("OWN_FLOW", "ref-abort-running").getStatus());

        assertFalse(stack.service.abort(running, "再点一次"), "已终态行不再改");
        assertThat(stack.observerEvents).as("每次成功作废发一次 DEAD，不重复").hasSize(2);
    }

    // ==================== ⑤ 受理侧终态封存 sealExternal（A3.4） ====================

    @Test
    @DisplayName("A3.4-⑤a：SUCCESS 行首封 true，重复封同值 false（CAS 已封过＝输家）")
    void sealExternalSealsSuccessRowOnce() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-seal-success", null, "E1");
        stack.runner.tick(); // FIRST → SECOND
        stack.runner.tick(); // SECOND → THIRD
        stack.runner.tick(); // THIRD → done
        assertEquals("SUCCESS", stack.service.find("OWN_FLOW", "ref-seal-success").getStatus());

        assertTrue(stack.service.sealExternal(handle, "CLEANED"), "终态行首封＝true");
        assertEquals("CLEANED", stack.service.find("OWN_FLOW", "ref-seal-success").getExternalStatus());
        assertTrue(stack.repo.events.contains("seal-external-hit:" + handle.getId()));

        assertFalse(stack.service.sealExternal(handle, "CLEANED"), "已封过同值＝false（CAS）");
        assertTrue(stack.repo.events.contains("seal-external-miss:" + handle.getId()));
        assertEquals("CLEANED", stack.service.find("OWN_FLOW", "ref-seal-success").getExternalStatus(), "拒封不得改写");
    }

    @Test
    @DisplayName("A3.4-⑤b：DEAD 行封一次 true（受理侧补落对账标记的主场景）")
    void sealExternalSealsDeadRowOnce() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-seal-dead", null, "E1");
        assertTrue(stack.service.abort(handle, "作废"), "先落 DEAD（abort 不受 owner 限）");
        assertEquals("DEAD", stack.service.find("OWN_FLOW", "ref-seal-dead").getStatus());

        assertTrue(stack.service.sealExternal(handle, "CLEANED"), "DEAD 行可封");
        assertEquals("CLEANED", stack.service.find("OWN_FLOW", "ref-seal-dead").getExternalStatus());
        assertFalse(stack.service.sealExternal(handle, "CLEANED"), "再封＝false");
    }

    @Test
    @DisplayName("A3.4-⑤c：FAILED 行拒封（不在终态集——requeue 会清 external_status，封了也会被洗掉）")
    void sealExternalRejectsFailedRow() {
        OwnershipFlow flow = new OwnershipFlow();
        flow.secondOutcome = () -> {
            throw new IllegalStateException("boom");
        };
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-seal-failed", null, "E1");
        stack.runner.tick(); // FIRST → SECOND
        stack.runner.tick(); // SECOND 抛异常 → FAILED
        SaAsyncTask failed = stack.service.find("OWN_FLOW", "ref-seal-failed");
        assertEquals("FAILED", failed.getStatus());

        assertFalse(stack.service.sealExternal(handle, "CLEANED"), "FAILED 不在终态集");
        assertTrue(stack.repo.events.contains("seal-external-rejected:" + handle.getId()));
        assertNull(stack.service.find("OWN_FLOW", "ref-seal-failed").getExternalStatus(), "拒封不得落标记");
    }

    @Test
    @DisplayName("A3.4-⑤d：RUNNING 有主行拒封（封存只作用于已终态的行）")
    void sealExternalRejectsOwnedRunningRow() {
        OwnershipFlow flow = new OwnershipFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask handle = stack.service.submit("OWN_FLOW", "ref-seal-running", null, "E1");
        stack.runner.tick(); // 抢到并推进：RUNNING 有主
        SaAsyncTask running = stack.service.find("OWN_FLOW", "ref-seal-running");
        assertEquals("RUNNING", running.getStatus());
        assertTrue(running.getOwner() != null, "前提：行确有主");

        assertFalse(stack.service.sealExternal(handle, "CLEANED"), "RUNNING 行不许封");
        assertTrue(stack.repo.events.contains("seal-external-rejected:" + handle.getId()));
        assertNull(stack.service.find("OWN_FLOW", "ref-seal-running").getExternalStatus(), "拒封不得落标记");
    }
}
