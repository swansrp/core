package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据⑨（§4.2.2「阶段迁移只许原地或前进、禁回跳」）：to(order 更小) 当场判错；
 * to(当前阶段) 放行（对齐现网「提交完把当前档再写一遍」的形状）。
 *
 * <p>M16 修复轮 A4：结构错（禁回跳/目标阶段不在清单内）不再向调用方抛，而是<b>就地置 DEAD</b>
 * ——重试无意义的代码错不该让补偿扫描按租约反复重跑阶段体。</p>
 *
 * @author sharp
 */
class AsyncStageRegressionTest {

    /** 第二阶段回跳第一阶段的流程 */
    @AsyncTaskType(value = "BACK_JUMP", maxRetry = 1)
    static class BackJumpFlow {

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("TWO");
        }

        @AsyncStage(value = "TWO", order = 2)
        public StageOutcome two(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("ONE");
        }

        /** 原地档形状：提交完把当前阶段再写一遍 */
        @AsyncStage(value = "THREE", order = 3)
        public StageOutcome three(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.to("THREE");
        }
    }

    private final BackJumpFlow flow = new BackJumpFlow();

    private final TestStack stack = new TestStack(false, 1000, flow);

    /** 回跳负向：to(order 更小) 判为结构契约错 → 任务 DEAD（不再交给补偿扫描重跑） */
    @Test
    @DisplayName("判据⑨-a：to(order 更小)＝结构契约错 → DEAD，不向调用方抛")
    void backwardJumpGoesDead() throws Exception {
        stack.addObserver(stack.recordingObserver());
        SaAsyncTask task = stack.service.submit("BACK_JUMP", "ref-back", null, "E1");
        // 先正常跑第一档：ONE → TWO（行变 RUNNING 且持有者已回填）
        stack.runner.tick();
        assertEquals("TWO", stack.service.find("BACK_JUMP", "ref-back").getStage());

        // 直接经代理调 TWO（其返回 to(ONE)）——切面判「禁回跳」并置 DEAD
        Object proxy = stack.registry.require("BACK_JUMP").getBean();
        Method two = BackJumpFlow.class.getDeclaredMethod("two", SaAsyncTask.class, AsyncStageScope.class);
        SaAsyncTask running = stack.service.find("BACK_JUMP", "ref-back");
        assertDoesNotThrow(() -> two.invoke(proxy, running, null), "结构错在切面内消化，不向调用方抛");

        SaAsyncTask dead = stack.service.find("BACK_JUMP", "ref-back");
        assertEquals("DEAD", dead.getStatus(), "禁回跳＝不可重试的结构错，直接 DEAD");
        assertThat(dead.getError()).contains("禁回跳");
        assertThat(stack.observerEvents).as("DEAD 档观察者必发一次").containsExactly("DEAD:ref-back");
        assertEquals("TWO", dead.getStage(), "阶段不被写坏（结构错不改 stage）");
        assertThat(task.getId()).isNotNull();
    }

    /** 原地放行：to(当前阶段) 合法（现网「提交完重写一遍当前档」的形状） */
    @Test
    @DisplayName("判据⑨-b：to(当前阶段) 放行")
    void sameStageAdvanceAllowed() throws Exception {
        stack.service.submit("BACK_JUMP", "ref-same", null, "E1");
        SaAsyncTask claimed = stack.runner.claimBatch("BACK_JUMP", "owner-same", 1).get(0);
        // 把行推到 THREE（执行侧写口带 ownership 谓词，用刚拿到的持有者）
        stack.repo.updateStage(claimed.getId(), claimed.getOwner(), "THREE");
        Object proxy = stack.registry.require("BACK_JUMP").getBean();
        Method three = BackJumpFlow.class.getDeclaredMethod("three", SaAsyncTask.class, AsyncStageScope.class);
        SaAsyncTask atThree = stack.service.find("BACK_JUMP", "ref-same");
        assertDoesNotThrow(() -> three.invoke(proxy, atThree, null));
        assertEquals("THREE", stack.service.find("BACK_JUMP", "ref-same").getStage(), "原地重写当前档合法");
    }
}
