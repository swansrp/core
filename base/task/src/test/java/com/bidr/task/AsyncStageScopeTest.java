package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段作用域三个写口的写穿腿（§4.2.1 手写面的注解化去处）：
 * snapshot＝外部观测留痕、claimExternal＝条件 UPDATE 幂等锁（没抢到什么都不写）、
 * externalId＝外部任务 id 写入。另钉 bridge 的操作人重建与恢复卫生（存旧→设新→恢复）。
 *
 * @author sharp
 */
class AsyncStageScopeTest {

    /** 用到作用域的流程 */
    @AsyncTaskType(value = "SCOPE_FLOW", maxRetry = 1)
    static class ScopeFlow {

        final List<String> seen = new ArrayList<>();

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            seen.add("ONE");
            scope.externalId("engine-task-9");
            scope.snapshot("CONVERT_FALLBACK", "{\"spans\":2}");
            return StageOutcome.to("TWO");
        }

        @AsyncStage(value = "TWO", order = 2)
        public StageOutcome two(SaAsyncTask task, AsyncStageScope scope) {
            seen.add("TWO");
            // 乐观认领：先到先得，第二次（notStatus 已被写成 DONE）必须落空
            scope.claimExternal("DONE", "{\"cleaned\":true}", "DONE");
            return StageOutcome.done();
        }
    }

    @Test
    @DisplayName("snapshot/externalId/claimExternal 写穿通用列；幂等锁没抢到什么都不写")
    void scopeWritesThroughToGenericColumns() {
        ScopeFlow flow = new ScopeFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        stack.service.submit("SCOPE_FLOW", "ref-scope", null, "E1");

        stack.runner.tick();
        SaAsyncTask afterOne = stack.service.find("SCOPE_FLOW", "ref-scope");
        assertEquals("engine-task-9", afterOne.getExternalId());
        assertEquals("CONVERT_FALLBACK", afterOne.getExternalStatus());
        assertEquals("{\"spans\":2}", afterOne.getExternalSnapshot());

        // 没抢到的形状：notStatus 等于当前 external_status（仍是 RUNNING/自持）→ 条件不满足、不写
        assertFalse(stack.service.claimExternal(afterOne, "DONE_AGAIN", null, "CONVERT_FALLBACK"));
        assertThat(stack.repo.events).endsWith("claim-external-miss:" + afterOne.getId());
        assertEquals("CONVERT_FALLBACK", stack.service.find("SCOPE_FLOW", "ref-scope").getExternalStatus(),
                "没抢到就什么都不写");

        // 第二阶段的 claimExternal(notStatus=DONE)：external_status 当前是 CONVERT_FALLBACK
        // （不等于 DONE）→ 抢到，写成 DONE
        stack.runner.tick();
        SaAsyncTask afterTwo = stack.service.find("SCOPE_FLOW", "ref-scope");
        assertTrue(stack.repo.events.stream().anyMatch(e -> e.equals("claim-external-hit:" + afterTwo.getId())));
        assertEquals("DONE", afterTwo.getExternalStatus());
        assertEquals("SUCCESS", afterTwo.getStatus());

        // 终态行不再接受执行侧写口（A3 owner＋RUNNING 谓词）：写被拒、列不变
        assertFalse(stack.service.claimExternal(afterTwo, "DONE_AGAIN", null, "DONE"));
        assertThat(stack.repo.events).endsWith("claim-external-rejected:" + afterTwo.getId());
        assertEquals("DONE", stack.service.find("SCOPE_FLOW", "ref-scope").getExternalStatus());
    }

    /** bridge 卫生腿：跑批线程无上下文→按 create_by 重建操作人；链结束恢复为无上下文（不裸清别人） */
    @Test
    @DisplayName("bridge：从 create_by 重建操作人、链后恢复；已有上下文不被覆盖")
    void bridgeRebuildsOperatorAndRestores() {
        com.bidr.authorization.bo.account.AccountInfo existing =
                new com.bidr.authorization.bo.account.AccountInfo();
        existing.setCustomerNumber("REQUEST_USER");
        com.bidr.authorization.holder.AccountContext.set(existing);

        SaAsyncTask task = new SaAsyncTask();
        task.setTaskType("SCOPE_FLOW");
        task.setBizRef("ref-bridge");
        task.setCreateBy("E2002");

        TaskContextBridge bridge = new TaskContextBridge();
        List<String> operators = new ArrayList<>();
        bridge.execute(task, () -> {
            operators.add(com.bidr.authorization.holder.AccountContext.getOperator());
            return null;
        });

        assertEquals(java.util.Collections.singletonList("E2002"), operators,
                "链内按 create_by 重建出操作人（审计列/租户参照的原料）");
        assertEquals("REQUEST_USER",
                com.bidr.authorization.holder.AccountContext.getOperator(),
                "链后恢复原值（请求线程的登录态不被静默清掉）");
        com.bidr.authorization.holder.AccountContext.remove();

        // 跑批线程（无上下文）形态：链内重建、链后回到无上下文
        List<String> inChain = new ArrayList<>();
        bridge.execute(task, () -> {
            inChain.add(com.bidr.authorization.holder.AccountContext.getOperator());
            return null;
        });
        assertEquals(java.util.Collections.singletonList("E2002"), inChain);
        assertEquals("", com.bidr.authorization.holder.AccountContext.getOperator(),
                "链后回到无上下文（跑批线程卫生）");
    }
}
