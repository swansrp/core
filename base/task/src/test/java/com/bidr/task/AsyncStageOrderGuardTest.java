package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据⑥（§4.2.5：阶段 order 断号/重复时启动期直接失败，不做运行时兜底）＋
 * 注解 order 连续性守卫的静态闸门腿（输出全部注册流程的阶段序＝必须 1..N 连续）。
 *
 * @author sharp
 */
class AsyncStageOrderGuardTest {

    @AsyncTaskType(value = "GAP_FLOW", maxRetry = 1)
    static class OrderGapFlow {
        @AsyncStage(value = "A", order = 1)
        public StageOutcome a(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }

        @AsyncStage(value = "C", order = 3)
        public StageOutcome c(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @AsyncTaskType(value = "DUP_FLOW", maxRetry = 1)
    static class OrderDuplicateFlow {
        @AsyncStage(value = "A", order = 1)
        public StageOutcome a(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }

        @AsyncStage(value = "B", order = 1)
        public StageOutcome b(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @AsyncTaskType(value = "NO_STAGE_FLOW", maxRetry = 1)
    static class NoStageFlow {
        public StageOutcome notAStage(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    @AsyncTaskType(value = "BAD_SIGNATURE_FLOW", maxRetry = 1)
    static class BadSignatureFlow {
        @AsyncStage(value = "A", order = 1)
        public boolean a(SaAsyncTask task, AsyncStageScope scope) {
            return true;
        }
    }

    @AsyncTaskType(value = "THREE_STAGE", maxRetry = 1)
    static class DuplicateTypeFlow {
        @AsyncStage(value = "A", order = 1)
        public StageOutcome a(SaAsyncTask task, AsyncStageScope scope) {
            return StageOutcome.done();
        }
    }

    /** 断号：orders=[1,3] → 构造注册表即抛（启动期失败，机器判据） */
    @Test
    @DisplayName("判据⑥-a：order 断号 → 启动期直接失败")
    void orderGapFailsAtStartup() {
        assertThatThrownBy(() -> new TaskFlowRegistry(Collections.singleton(new OrderGapFlow()), 900_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("从 1 连续")
                .hasMessageContaining("orders=[1, 3]");
    }

    /** 重复：orders=[1,1] → 启动期抛 */
    @Test
    @DisplayName("判据⑥-b：order 重复 → 启动期直接失败")
    void orderDuplicateFailsAtStartup() {
        assertThatThrownBy(() -> new TaskFlowRegistry(Collections.singleton(new OrderDuplicateFlow()), 900_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("从 1 连续");
    }

    /** 无阶段方法 / 签名不符 / 类型重复注册：同样启动期抛 */
    @Test
    @DisplayName("判据⑥-c：无阶段方法/签名不符/类型重复 → 启动期直接失败")
    void structuralMistakesFailAtStartup() {
        assertThatThrownBy(() -> new TaskFlowRegistry(Collections.singleton(new NoStageFlow()), 900_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有任何 @AsyncStage");
        assertThatThrownBy(() -> new TaskFlowRegistry(Collections.singleton(new BadSignatureFlow()), 900_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("签名");
        assertThatThrownBy(() -> new TaskFlowRegistry(
                java.util.Arrays.asList(new TestStack.ThreeStageFlow(), new DuplicateTypeFlow()), 900_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册");
    }

    /** 静态闸门腿：注册表内全部流程的阶段 order 必须 1..N 连续、标识唯一（守卫输出） */
    @Test
    @DisplayName("order 连续性守卫：全部注册流程 orders=1..N、阶段标识唯一")
    void allRegisteredFlowsHaveContiguousOrders() {
        TaskFlowRegistry registry = new TaskFlowRegistry(Collections.singleton(new TestStack.ThreeStageFlow()), 900_000L);
        registry.all().forEach(flow -> {
            List<Integer> orders = flow.getStages().stream()
                    .map(TaskFlowRegistry.StageDef::getOrder).collect(Collectors.toList());
            for (int i = 0; i < orders.size(); i++) {
                assertEquals(i + 1, orders.get(i), "type=" + flow.getType() + " orders 必须从 1 连续");
            }
            assertThat(flow.getStages().stream().map(TaskFlowRegistry.StageDef::getValue).distinct())
                    .as("type=" + flow.getType() + " 阶段标识唯一")
                    .hasSize(flow.getStages().size());
        });
    }
}
