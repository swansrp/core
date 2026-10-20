package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 受理侧清理口 {@link AsyncTaskService#discard(String, String)} 的契约腿（M16 修复轮 A9）：
 * 删业务对象时顺手收口它的任务行，判据三条——
 * <ol>
 *   <li>按 {@code (task_type, biz_ref)} 精确物理删：目标键的行确实<b>不存在</b>了；</li>
 *   <li><b>别的键不受影响</b>（{@code material:} 这类按内容寻址的键可能被多处共享，
 *       删文件不许顺手清掉别人的在途解析）；</li>
 *   <li><b>幂等</b>：行本就不存在（从没提交过、或重复删）返回 0 且不抛；空键同档不抛。</li>
 * </ol>
 *
 * @author sharp
 */
class AsyncTaskDiscardTest {

    @Test
    @DisplayName("A9：discard 物理删指定键、别的键不受影响、重复删幂等")
    void discardRemovesOnlyTheGivenKeyAndIsIdempotent() {
        TestStack stack = new TestStack(false, 1000, new TestStack.ThreeStageFlow());
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "parse:1:2", null, "E1");
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "material:1:9", null, "E1");

        assertEquals(1, stack.service.discard(TestStack.ThreeStageFlow.TYPE, "parse:1:2"),
                "目标键恰好删一行");
        assertNull(stack.service.find(TestStack.ThreeStageFlow.TYPE, "parse:1:2"), "行必须真的不存在");
        SaAsyncTask untouched = stack.service.find(TestStack.ThreeStageFlow.TYPE, "material:1:9");
        assertNotNull(untouched, "按内容寻址的 material: 键不许被文件删除连带清掉");

        assertEquals(0, stack.service.discard(TestStack.ThreeStageFlow.TYPE, "parse:1:2"),
                "幂等：行不存在返回 0 不抛");
        assertEquals(0, stack.service.discard("", "parse:1:2"), "空类型不抛");
        assertEquals(0, stack.service.discard(TestStack.ThreeStageFlow.TYPE, null), "空键不抛");
        assertNotNull(stack.service.find(TestStack.ThreeStageFlow.TYPE, "material:1:9"));
    }

    @Test
    @DisplayName("A9：在途行被删后，跑批写口因行不存在而放弃（不复活僵尸行）")
    void inFlightRowDeletionMakesLateWritesNoOps() {
        TestStack stack = new TestStack(false, 1000, new TestStack.ThreeStageFlow());
        stack.service.submit(TestStack.ThreeStageFlow.TYPE, "parse:7:8", null, "E1");
        stack.runner.tick();
        SaAsyncTask running = stack.service.find(TestStack.ThreeStageFlow.TYPE, "parse:7:8");
        assertEquals("SECOND", running.getStage());

        // 业务对象被删：行物理删掉；在途实例再写阶段推进也只会更新 0 行（core 只记 WARN）
        stack.service.discard(TestStack.ThreeStageFlow.TYPE, "parse:7:8");
        assertFalse(stack.repo.updateStage(running.getId(), running.getOwner(), "THIRD"),
                "行不存在＝写口更新 0 行（丢失所有权只 WARN，不是复活）");
        assertNull(stack.service.find(TestStack.ThreeStageFlow.TYPE, "parse:7:8"), "不得凭空复活");
    }
}
