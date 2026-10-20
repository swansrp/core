package com.bidr.task;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判据①②⑧＋纪律②的机器腿（§4.2.5／§4.2.4）：
 * ① 三阶段流程跑完后表内 stage/attempt/next_run_at/status 逐档对上，<b>测试代码零次手工 update</b>
 * （本类对 repo 只有读断言，一切推进经底盘与切面）；② 三态返回各一腿（to/pending/done 落库差异）；
 * ⑧ 异步注册两腿（submit 只含任务句柄、业务产物不存在；掐掉唤醒信号后轮询照样开跑）；
 * 纪律② 受理侧不执行任何阶段＋连续两阶段同线程跑、第二阶段仍读出 biz_ref。
 *
 * @author sharp
 */
class AsyncTaskAnnotationFlowTest {

    private final TestStack.ThreeStageFlow flow = new TestStack.ThreeStageFlow();

    private final TestStack stack = new TestStack(false, 1000, flow);

    // ==================== 判据①：三阶段逐档对上，零手工 update ====================

    /** 主线：FIRST→SECOND→THIRD→SUCCESS，每 tick 后按档断言 stage/status/attempt/next_run_at */
    @Test
    @DisplayName("判据①：三阶段跑完逐档对上（stage/attempt/next_run_at/status），测试零手工 update")
    void threeStageRunMatchesBookkeeping() {
        stack.addObserver(stack.recordingObserver());

        SaAsyncTask handle = stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-1", "{\"k\":1}", "E1001");
        assertEquals("FIRST", handle.getStage());
        assertEquals("PENDING", handle.getStatus());

        stack.runner.tick();
        SaAsyncTask afterFirst = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-1");
        assertEquals("SECOND", afterFirst.getStage());
        assertEquals("RUNNING", afterFirst.getStatus());
        assertEquals(0, afterFirst.getAttempt());
        assertNull(afterFirst.getNextRunAt());

        stack.runner.tick();
        SaAsyncTask afterSecond = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-1");
        assertEquals("THIRD", afterSecond.getStage());
        assertEquals("RUNNING", afterSecond.getStatus());
        assertEquals(0, afterSecond.getAttempt());

        stack.runner.tick();
        SaAsyncTask done = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-1");
        assertEquals("SUCCESS", done.getStatus());
        assertEquals("THIRD", done.getStage());
        assertEquals(0, done.getAttempt());
        assertNull(done.getNextRunAt());
        assertNull(done.getOwner());
        assertThat(flow.visited).containsExactly("FIRST:ref-1", "SECOND", "THIRD");
        assertThat(stack.observerEvents).containsExactly("SUCCESS:ref-1");
    }

    // ==================== 判据②：三态返回各一腿 ====================

    /** to：推进与落库由切面做（阶段体只返回，无任何手工写） */
    @Test
    @DisplayName("判据②-a：to(下一阶段)＝stage 前进、RUNNING 不变")
    void advanceOutcomeMovesStage() {
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-to", null, "E1");
        stack.runner.tick();
        assertEquals("SECOND", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-to").getStage());
    }

    /** pending：原地不动＋退避时间落库；退避未到时下一 tick 不重跑（「没做完」≠「推进一步」） */
    @Test
    @DisplayName("判据②-b：pending(退避ms)＝stage 原地、next_run_at 后移、未到期不重跑")
    void pendingOutcomeStaysAndBacksOff() {
        flow.secondOutcome = () -> StageOutcome.pending(120_000L);
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-pending", null, "E1");

        stack.runner.tick();
        assertEquals("SECOND", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending").getStage());

        stack.runner.tick();
        SaAsyncTask pending = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending");
        assertEquals("SECOND", pending.getStage(), "pending＝原地不动");
        assertEquals("RUNNING", pending.getStatus());
        assertNotNull(pending.getNextRunAt());
        assertTrue(pending.getNextRunAt().after(new Date()), "退避时间必须晚于当前");

        int visitedAfterPending = flow.visited.size();
        stack.runner.tick();
        assertEquals(visitedAfterPending, flow.visited.size(), "退避未到，本实例名下也不得重跑");
        assertEquals("SECOND", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending").getStage());

        // 时钟跳过退避：下一 tick 由 ownedRunning 再取到、把 SECOND 走完（换回推进语义）
        long jumpMs = pending.getNextRunAt().getTime() - System.currentTimeMillis() + 1_000L;
        stack.repo.clock = () -> new Date(System.currentTimeMillis() + jumpMs);
        flow.secondOutcome = () -> StageOutcome.to("THIRD");
        stack.runner.tick();
        assertEquals("THIRD", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending").getStage());
    }

    /** 提交即唤醒正向腿：唤醒栈（同步执行器）里 submit 后不等 tick，任务已被 runner 推进 */
    @Test
    @DisplayName("唤醒正向腿：submit 落库即唤醒，runner 不等下一 tick 就开跑")
    void wakeSignalRunsImmediatelyAfterSubmit() {
        TestStack.ThreeStageFlow wflow = new TestStack.ThreeStageFlow();
        TestStack woke = new TestStack(true, 1000, wflow);
        woke.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-woke", null, "E1");
        assertEquals("SECOND", woke.service.find(TestStack.ThreeStageFlow.TYPE, "ref-woke").getStage(),
                "提交即唤醒：首轮阶段已被唤醒线程跑掉（不等 fixedDelay）");
    }

    /** done：整任务完成，切面收口 SUCCESS＋通知观察者（判据②-c） */
    @Test
    @DisplayName("判据②-c：done()＝SUCCESS＋末阶段＋清调度列＋观察者 SUCCESS 档")
    void doneOutcomeSucceedsAndNotifies() {
        stack.addObserver(stack.recordingObserver());
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-done", null, "E1");
        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();
        SaAsyncTask done = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-done");
        assertEquals("SUCCESS", done.getStatus());
        assertThat(stack.observerEvents).containsExactly("SUCCESS:ref-done");
    }

    // ==================== 判据⑧：异步注册两腿 ====================

    /** submit 返回任务句柄、不含业务产物：受理侧不执行任何阶段（纪律②腿②），表内 stage 停在首档 */
    @Test
    @DisplayName("判据⑧-a：submit 只落库不执行——返回句柄、阶段零调用、stage 仍在首档")
    void submitRegistersWithoutExecuting() {
        SaAsyncTask handle = stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-submit", "{\"k\":1}", "E1");
        assertNotNull(handle.getId());
        assertEquals("FIRST", handle.getStage());
        assertEquals("PENDING", handle.getStatus());
        assertThat(flow.visited).isEmpty();
        SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-submit");
        assertEquals("PENDING", row.getStatus());
        assertEquals("FIRST", row.getStage());
        assertEquals(0, row.getAttempt());
        assertNull(row.getOwner(), "受理不抢占：owner 为空，等跑批来抢");
    }

    /** 掐掉唤醒信号：submit 后不开跑，下一 tick 轮询照样把任务跑起来（信号只是加速器） */
    @Test
    @DisplayName("判据⑧-b：唤醒信号掐掉后由 fixedDelay 轮询兜住")
    void pollingFallbackCoversLostWakeSignal() {
        TestStack noWake = new TestStack(false, 1000, new TestStack.ThreeStageFlow());
        noWake.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-nowake", null, "E1");
        SaAsyncTask before = noWake.service.find(TestStack.ThreeStageFlow.TYPE, "ref-nowake");
        assertEquals("PENDING", before.getStatus());

        noWake.runner.tick();
        assertEquals("SECOND", noWake.service.find(TestStack.ThreeStageFlow.TYPE, "ref-nowake").getStage(),
                "信号丢失不影响正确性：下一 tick 照样开跑");
    }

    /** 幂等键：同 (task_type, biz_ref) 再 submit＝同一行（不新建、不改状态） */
    @Test
    @DisplayName("幂等键：同键再 submit 返回同一行，不新建")
    void sameBizKeySubmitsSameRow() {
        SaAsyncTask first = stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-idem", null, "E1");
        stack.runner.tick();
        SaAsyncTask second = stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-idem", null, "E1");
        assertEquals(first.getId(), second.getId());
        assertEquals("SECOND", second.getStage(), "再 submit 不重置在途任务的阶段");
        assertEquals(1, stack.repo.events.stream().filter(e -> e.startsWith("insert:")).count());
    }

    /** requeue：受理侧人工重试入队＝回首阶段＋PENDING＋attempt 清零（真要重跑的正源，不在阶段体内） */
    @Test
    @DisplayName("requeue：回首阶段＋PENDING＋attempt 清零，跑批接着跑完")
    void requeueResetsAndReruns() {
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-requeue", null, "E1");
        stack.runner.tick();
        assertEquals("SECOND", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue").getStage());

        stack.service.requeue(TestStack.ThreeStageFlow.TYPE, "ref-requeue");
        SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue");
        assertEquals("FIRST", row.getStage());
        assertEquals("PENDING", row.getStatus());
        assertEquals(0, row.getAttempt());

        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();
        assertEquals("SUCCESS", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue").getStatus());
    }

    // ==================== 纪律②：连续两阶段同线程，第二阶段仍读出 biz_ref ====================

    /** biz_ref 跨阶段可读（受理侧写入一次、跑批线程逐阶段还原） */
    @Test
    @DisplayName("纪律②：连续两阶段同线程跑，第二阶段仍读出 biz_ref")
    void bizRefReadableAcrossConsecutiveStages() {
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-ctx", null, "E1");
        stack.runner.tick();
        stack.runner.tick();
        assertEquals("ref-ctx", flow.bizRefSeenInSecondStage, "第二阶段在跑批线程上仍读出 biz_ref");
    }

    /** 未注册类型提交即拒（未标注 @AsyncTaskType 的类零影响——纪律⑤的受理面） */
    @Test
    @DisplayName("未注册类型 submit 直接拒绝")
    void unregisteredTypeRejected() {
        assertThatThrownBy(() -> stack.service.submit("UNREGISTERED", "ref-x", null, "E1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册");
    }

    /** 进度推导：首阶段 0%、中段按 order、SUCCESS 恒 100（无任务行 0% 空态不抛） */
    @Test
    @DisplayName("progress：按 order 推导＋SUCCESS 恒 100＋空态不抛")
    void progressDerivation() {
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-prog", null, "E1");
        assertEquals(0, stack.service.progress(TestStack.ThreeStageFlow.TYPE, "ref-prog").getProgress());

        stack.runner.tick();
        assertEquals(50, stack.service.progress(TestStack.ThreeStageFlow.TYPE, "ref-prog").getProgress());

        stack.runner.tick();
        assertEquals(100, stack.service.progress(TestStack.ThreeStageFlow.TYPE, "ref-prog").getProgress());

        assertEquals(0, stack.service.progress(TestStack.ThreeStageFlow.TYPE, "ref-none").getProgress());
        assertNull(stack.service.progress(TestStack.ThreeStageFlow.TYPE, "ref-none").getStatus());
    }

    /** 提交闸门负向腿：排队数超阈不提交（NoticeException 拒收） */
    @Test
    @DisplayName("提交闸门：排队数超阈拒收")
    void gateRejectsWhenQueueAheadExceedsLimit() {
        TestStack gated = new TestStack(false, 2, new TestStack.ThreeStageFlow());
        gated.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-q1", null, "E1");
        gated.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-q2", null, "E1");
        assertThatThrownBy(() -> gated.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-q3", null, "E1"))
                .isInstanceOf(NoticeException.class)
                .hasMessageContaining("排队过多");
    }

    /** 提交闸门「取不到排队数放行」腿：count 抛异常不误杀受理 */
    @Test
    @DisplayName("提交闸门：取不到排队数（查询失败）放行")
    void gateAllowsWhenCountFails() {
        stack.repo.countFailure = new IllegalStateException("db down");
        SaAsyncTask handle = stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-gate-ok", null, "E1");
        assertNotNull(handle.getId());
    }
}
