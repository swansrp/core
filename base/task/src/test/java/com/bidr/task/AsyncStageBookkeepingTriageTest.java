package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A4 机器腿（M16 修复轮）：切面记账的两类错误分流——
 * <ul>
 *   <li><b>结构错</b>（目标阶段不在清单内／禁回跳／未知三态）＝代码错、重试无意义 →
 *       当场置 DEAD（观察者发一次），<b>不留给补偿扫描按租约反复重跑同一阶段体</b>；</li>
 *   <li><b>基础设施错</b>（丢失所有权＝条件 UPDATE 0 行，DB 抖动同理）→ 任务行不动、只 WARN，
 *       由租约到期/下一 tick 自然收敛——不许把「进度没写回」误判成业务死。</li>
 * </ul>
 *
 * @author sharp
 */
class AsyncStageBookkeepingTriageTest {

    /** 第二阶段返回运行期可换的结果（结构错腿与丢所有权腿共用） */
    @AsyncTaskType(value = "TRIAGE_FLOW", maxRetry = 3)
    static class TriageFlow {

        final AtomicInteger secondRuns = new AtomicInteger();

        volatile java.util.function.Supplier<StageOutcome> secondOutcome =
                () -> StageOutcome.to("THIRD");

        /** 阶段体返回前的挂点（模拟「阶段执行期间行已易主」） */
        volatile Consumer<SaAsyncTask> afterBody = task -> { };

        @AsyncStage(value = "FIRST", order = 1)
        public StageOutcome first(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("SECOND");
        }

        @AsyncStage(value = "SECOND", order = 2)
        public StageOutcome second(SaAsyncTask task, AsyncStageScope scope) {
            secondRuns.incrementAndGet();
            afterBody.accept(task);
            return secondOutcome.get();
        }

        @AsyncStage(value = "THIRD", order = 3)
        public StageOutcome third(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @Test
    @DisplayName("A4：to(不存在的阶段) → 行 DEAD、阶段体只跑一次、后续 tick 不再重跑")
    void unknownTargetStageGoesDeadOnce() {
        TriageFlow flow = new TriageFlow();
        flow.secondOutcome = () -> StageOutcome.to("NO_SUCH_STAGE");
        TestStack stack = new TestStack(false, 1000, flow);
        stack.addObserver(stack.recordingObserver());
        stack.service.submit("TRIAGE_FLOW", "ref-unknown", null, "E1");

        stack.runner.tick(); // FIRST → SECOND
        assertEquals("SECOND", stack.service.find("TRIAGE_FLOW", "ref-unknown").getStage());
        stack.runner.tick(); // SECOND 返回不存在目标 → 结构错 → DEAD
        SaAsyncTask dead = stack.service.find("TRIAGE_FLOW", "ref-unknown");
        assertEquals("DEAD", dead.getStatus(), "结构错直接 DEAD（不交给补偿扫描重跑）");
        assertThat(dead.getError()).contains("目标阶段不在流程清单内");
        assertEquals("SECOND", dead.getStage(), "目标阶段不合法时 stage 不写坏");

        stack.runner.tick(); // DEAD 行不可抢占：阶段体不得再跑
        stack.runner.tick();
        assertEquals(1, flow.secondRuns.get(), "阶段体只许跑一次");
        assertThat(stack.observerEvents).as("DEAD 档只发一次").containsExactly("DEAD:ref-unknown");
    }

    @Test
    @DisplayName("A4：目标合法但 owner 已丢 → 不 DEAD、只 WARN、行不动")
    void lostOwnershipIsNotTreatedAsDead() {
        TriageFlow flow = new TriageFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        stack.addObserver(stack.recordingObserver());
        SaAsyncTask handle = stack.service.submit("TRIAGE_FLOW", "ref-lost", null, "E1");
        flow.afterBody = task -> stack.repo.handOver(handle.getId(), "instance-B");

        stack.runner.tick(); // FIRST → SECOND（第一档无挂点，正常推进）
        stack.runner.tick(); // SECOND 期间行易主 → advance 条件 UPDATE 0 行

        SaAsyncTask after = stack.service.find("TRIAGE_FLOW", "ref-lost");
        assertEquals("RUNNING", after.getStatus(), "丢所有权不是业务死，行不许被改成 DEAD");
        assertEquals("SECOND", after.getStage(), "丢失所有权的写不落库");
        assertEquals("instance-B", after.getOwner(), "owner 保持接手方");
        assertThat(stack.observerEvents).as("没有终态就不许发通知").isEmpty();
        assertThat(stack.repo.events).contains("update-stage-rejected:" + handle.getId());
    }
}
