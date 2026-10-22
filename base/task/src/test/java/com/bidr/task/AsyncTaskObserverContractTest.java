package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据④⑦的机器腿（§4.2.2 observer 终态契约）：
 * ④ observer 抛异常不改任务终态（逐件 try-catch，后面的观察者照常收到）；
 * ⑦ DEAD ⇒ observer 收到（业务失败态由这一档接手）；FAILED（预算内）⇒ observer 不收到
 * （钉住「同一任务通知 N 次」的反例）。
 *
 * <p>阶段迁移通知契约（onStage hook）四腿：真迁移必发且先于终态、原地 advance 不发、
 * pending 退避不发、onStage 抛异常不影响任务记账（与终态档同一纪律）。</p>
 *
 * @author sharp
 */
class AsyncTaskObserverContractTest {

    private final TestStack.ThreeStageFlow flow = new TestStack.ThreeStageFlow();

    private final TestStack stack = new TestStack(false, 1000, flow);

    /** observer 抛异常：任务终态不变、后续观察者照常收到（多贡献链逐件 try-catch） */
    @Test
    @DisplayName("判据④：观察者抛异常不改任务终态，其余观察者照常收到")
    void observerThrowDoesNotChangeTerminal() {
        AtomicInteger boomCount = new AtomicInteger();
        stack.addObserver((task, status) -> {
            boomCount.incrementAndGet();
            throw new IllegalStateException("observer down");
        });
        stack.addObserver(stack.recordingObserver());

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-throw", null, "E1");
        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();

        SaAsyncTask done = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-throw");
        assertEquals("SUCCESS", done.getStatus(), "观察者异常不改任务终态");
        assertEquals(1, boomCount.get());
        assertThat(stack.observerEvents).as("坏观察者之后的观察者照常收到")
                .containsExactly("SUCCESS:ref-throw");
    }

    /** DEAD 档必发：业务失败态落库/通知由这一档接手（原先写在执行侧 catch 里的东西搬到这里） */
    @Test
    @DisplayName("判据⑦-a：任务到 DEAD ⇒ 观察者收到且只收到一次")
    void deadNotifiesObserverOnce() {
        flow.secondOutcome = () -> {
            throw new IllegalStateException("stage two broken permanently-ish");
        };
        // maxRetry=2：三次失败耗尽预算 → DEAD
        stack.addObserver(stack.recordingObserver());
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-dead", null, "E1");
        // 首轮 FIRST 不失败：共 4 轮＝FIRST＋三次 SECOND 失败（预算 2 耗尽后置 DEAD）
        for (int i = 0; i < 4; i++) {
            stack.runner.tick();
            SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-dead");
            if ("FAILED".equals(row.getStatus()) && row.getNextRunAt() != null) {
                final long jump = row.getNextRunAt().getTime() - System.currentTimeMillis() + 1_000L;
                stack.repo.clock = () -> new Date(System.currentTimeMillis() + jump);
            }
        }
        assertEquals("DEAD", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-dead").getStatus());
        assertThat(stack.observerEvents).as("DEAD 档必发、只发一次").containsExactly("DEAD:ref-dead");
    }

    /** FAILED 档不发：预算内每轮失败都进 FAILED，挂上去＝同一任务通知 N 次 */
    @Test
    @DisplayName("判据⑦-b：预算内的 FAILED ⇒ 观察者不收到")
    void failedWithinBudgetNotNotified() {
        flow.secondOutcome = () -> {
            throw new IllegalStateException("transient-ish");
        };
        stack.addObserver(stack.recordingObserver());
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-failed", null, "E1");
        stack.runner.tick();
        stack.runner.tick(); // 首轮 FIRST 推进、二轮 SECOND 失败
        assertEquals("FAILED", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-failed").getStatus());
        assertThat(stack.observerEvents).as("FAILED（预算内）不发观察者").isEmpty();
    }

    // ==================== 阶段迁移通知契约（onStage hook） ====================

    /** 真迁移必发：from/to 正确、先于终态通知，观察者看到的行已带新 stage 与 RUNNING 态 */
    @Test
    @DisplayName("判据⑧-a：真迁移发 onStage（from/to 正确）且都在 onTerminal(SUCCESS) 之前")
    void realMigrationNotifiesStageBeforeTerminal() {
        List<String> events = new ArrayList<>();
        stack.addObserver(new AsyncTaskObserver() {
            @Override
            public void onTerminal(SaAsyncTask task, AsyncTaskStatus status) {
                events.add("TERMINAL:" + status.name() + ":" + task.getBizRef());
            }

            @Override
            public void onStage(SaAsyncTask task, String fromStage, String toStage) {
                assertEquals(toStage, task.getStage(), "通知时任务行已带新 stage");
                assertEquals("RUNNING", task.getStatus(), "通知时任务行已是 RUNNING 态");
                events.add("STAGE:" + fromStage + "->" + toStage + ":" + task.getBizRef());
            }
        });

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-stage", null, "E1");
        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();

        SaAsyncTask done = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-stage");
        assertEquals("SUCCESS", done.getStatus());
        assertThat(events).as("两条真迁移都在终态通知之前，from/to 逐字正确").containsExactly(
                "STAGE:FIRST->SECOND:ref-stage",
                "STAGE:SECOND->THIRD:ref-stage",
                "TERMINAL:SUCCESS:ref-stage");
    }

    /** 原地 advance（to(当前阶段)）不发：外部腿轮询的常态形状，发了＝每 tick 轰一次观察者 */
    @Test
    @DisplayName("判据⑧-b：原地 advance 不发 onStage，任务照常继续推进收口")
    void inPlaceAdvanceDoesNotNotifyStage() {
        flow.secondOutcome = () -> StageOutcome.to("SECOND"); // SECOND 原地：把当前档再写一遍
        stack.addObserver(stack.stageRecordingObserver());

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-inplace", null, "E1");
        stack.runner.tick(); // FIRST→SECOND：真迁移，唯一一条
        stack.runner.tick(); // SECOND→SECOND：原地
        stack.runner.tick(); // SECOND→SECOND：原地再来一轮

        SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-inplace");
        assertEquals("SECOND", row.getStage(), "原地推进 stage 不动");
        assertEquals("RUNNING", row.getStatus());
        assertThat(stack.stageEvents).as("原地 advance 不发 onStage")
                .containsExactly("FIRST->SECOND:ref-inplace");

        // 放开成前进：原地轮不影响后续推进与收口
        flow.secondOutcome = () -> StageOutcome.to("THIRD");
        stack.runner.tick(); // SECOND→THIRD
        stack.runner.tick(); // THIRD done
        assertEquals("SUCCESS", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-inplace").getStatus(),
                "原地轮不影响后续推进与收口");
        assertThat(stack.stageEvents).containsExactly(
                "FIRST->SECOND:ref-inplace", "SECOND->THIRD:ref-inplace");
    }

    /** pending 退避不发：stage 原地不动只是本轮暂缓，不是迁移 */
    @Test
    @DisplayName("判据⑧-c：pending 退避不发 onStage，退避后照常续跑")
    void pendingBackoffDoesNotNotifyStage() {
        flow.secondOutcome = () -> StageOutcome.pending(60_000L);
        stack.addObserver(stack.stageRecordingObserver());

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-pending", null, "E1");
        stack.runner.tick(); // FIRST→SECOND
        stack.runner.tick(); // SECOND pending 退避

        SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending");
        assertEquals("RUNNING", row.getStatus());
        assertThat(stack.stageEvents).as("退避轮不发 onStage")
                .containsExactly("FIRST->SECOND:ref-pending");

        // 跳过退避窗口（与 DEAD 腿同一把跳时尺），放开后照常续跑收口
        final long jump = row.getNextRunAt().getTime() - System.currentTimeMillis() + 1_000L;
        stack.repo.clock = () -> new Date(System.currentTimeMillis() + jump);
        flow.secondOutcome = () -> StageOutcome.to("THIRD");
        stack.runner.tick(); // SECOND→THIRD
        stack.runner.tick(); // THIRD done
        assertEquals("SUCCESS", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-pending").getStatus());
        assertThat(stack.stageEvents).containsExactly(
                "FIRST->SECOND:ref-pending", "SECOND->THIRD:ref-pending");
    }

    /** onStage 抛异常：坏观察者之后的照常收到，任务记账不受影响（照常跑完到 SUCCESS） */
    @Test
    @DisplayName("判据⑧-d：onStage 抛异常不影响任务记账，其余观察者照常收到")
    void stageObserverThrowDoesNotChangeBookkeeping() {
        AtomicInteger stageBoom = new AtomicInteger();
        stack.addObserver(new AsyncTaskObserver() {
            @Override
            public void onTerminal(SaAsyncTask task, AsyncTaskStatus status) {
                // 终态档保持沉默：本腿只验证 onStage 的异常隔离
            }

            @Override
            public void onStage(SaAsyncTask task, String fromStage, String toStage) {
                stageBoom.incrementAndGet();
                throw new IllegalStateException("stage observer down");
            }
        });
        stack.addObserver(stack.stageRecordingObserver());

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-stage-throw", null, "E1");
        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();

        SaAsyncTask done = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-stage-throw");
        assertEquals("SUCCESS", done.getStatus(), "onStage 异常不改任务记账");
        assertEquals(2, stageBoom.get(), "两条真迁移都触到了坏观察者");
        assertThat(stack.stageEvents).as("坏观察者之后的观察者照常收到")
                .containsExactly("FIRST->SECOND:ref-stage-throw", "SECOND->THIRD:ref-stage-throw");
    }

    /** 受理侧 requeue／abort 不发 onStage：重排回首阶段与作废走直写／终态口，不经理账口 */
    @Test
    @DisplayName("判据⑧-e：受理侧 requeue 回首阶段与 abort 作废都不发 onStage")
    void requeueAndAbortDoNotNotifyStage() {
        stack.addObserver(stack.stageRecordingObserver());

        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-requeue", null, "E1");
        stack.runner.tick(); // FIRST→SECOND：真迁移，唯一一条

        stack.service.requeue(TestStack.ThreeStageFlow.TYPE, "ref-requeue");
        SaAsyncTask row = stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue");
        assertEquals("FIRST", row.getStage(), "requeue 回首阶段");
        assertEquals("PENDING", row.getStatus());
        assertThat(stack.stageEvents).as("受理侧 requeue 不发 onStage")
                .containsExactly("FIRST->SECOND:ref-requeue");

        stack.service.abort(row, "用户作废");
        assertEquals("DEAD", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue").getStatus(),
                "abort 置 DEAD（终态走 onTerminal，不产生阶段事件）");
        assertThat(stack.stageEvents).as("受理侧 abort 也不发 onStage")
                .containsExactly("FIRST->SECOND:ref-requeue");

        // 重排路径闭环：另一任务 requeue 后照常按迁移节奏发事件
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "ref-requeue-2", null, "E1");
        stack.runner.tick();
        stack.service.requeue(TestStack.ThreeStageFlow.TYPE, "ref-requeue-2");
        stack.runner.tick();
        stack.runner.tick();
        stack.runner.tick();
        assertEquals("SUCCESS", stack.service.find(TestStack.ThreeStageFlow.TYPE, "ref-requeue-2").getStatus(),
                "requeue 后照常推进收口");
        assertThat(stack.stageEvents).containsExactly(
                "FIRST->SECOND:ref-requeue",
                // requeue 后行回到 FIRST：再度 FIRST→SECOND 是真迁移，照发（requeue 自身不发）
                "FIRST->SECOND:ref-requeue-2",
                "FIRST->SECOND:ref-requeue-2",
                "SECOND->THIRD:ref-requeue-2");
    }
}
