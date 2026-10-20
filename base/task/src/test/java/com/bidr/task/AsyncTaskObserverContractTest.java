package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据④⑦的机器腿（§4.2.2 observer 终态契约）：
 * ④ observer 抛异常不改任务终态（逐件 try-catch，后面的观察者照常收到）；
 * ⑦ DEAD ⇒ observer 收到（业务失败态由这一档接手）；FAILED（预算内）⇒ observer 不收到
 * （钉住「同一任务通知 N 次」的反例）。
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
}
