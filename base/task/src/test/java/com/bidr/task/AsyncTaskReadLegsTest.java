package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 棒 4 纯加法读口的判据腿（内存库驱动同一套 service 门面）：
 * 批量幂等键读（免 N+1）、本人最近行（create_by 硬绑）、排队位次（与提交闸门同一份判据）。
 *
 * @author sharp
 */
class AsyncTaskReadLegsTest {

    private final TestStack stack = new TestStack(false, 1000, new TestStack.ThreeStageFlow());

    private SaAsyncTask submit(String bizRef, String operator) {
        return stack.service.submit(TestStack.ThreeStageFlow.TYPE, bizRef, null, operator);
    }

    @DisplayName("findByBizRefs：同键只留最新行、无任务的键不出现、空入参＝空表")
    @Test
    void findByBizRefsKeepsLatestPerKey() {
        submit("ref-1", "u1");
        submit("ref-2", "u1");
        submit("ref-3", "u2");

        Map<String, SaAsyncTask> found = stack.service.findByBizRefs(
                TestStack.ThreeStageFlow.TYPE, Arrays.asList("ref-1", "ref-2", "ref-x"));

        assertEquals(2, found.size());
        assertFalse(found.containsKey("ref-x"), "无任务的键不出现");
        assertEquals("PENDING", found.get("ref-1").getStatus());

        assertTrue(stack.service.findByBizRefs(TestStack.ThreeStageFlow.TYPE, null).isEmpty());
        assertTrue(stack.service.findByBizRefs(TestStack.ThreeStageFlow.TYPE,
                Collections.<String>emptyList()).isEmpty());
    }

    @DisplayName("listRecentByCreator：create_by 硬绑、新→旧、limit 截断")
    @Test
    void listRecentByCreatorFiltersAndOrders() {
        submit("ref-1", "u1");
        submit("ref-2", "u2");
        submit("ref-3", "u1");

        List<SaAsyncTask> mine = stack.service.listRecentByCreator("u1", 10);

        assertEquals(2, mine.size(), "只看本人行");
        assertEquals("ref-3", mine.get(0).getBizRef(), "新→旧");
        assertEquals("ref-1", mine.get(1).getBizRef());
        assertEquals(1, stack.service.listRecentByCreator("u1", 1).size(), "limit 截断");
        assertTrue(stack.service.listRecentByCreator("nobody", 10).isEmpty());
        assertTrue(stack.service.listRecentByCreator(" ", 10).isEmpty(), "空主创人＝空表不抛");
    }

    @DisplayName("queuePosition：同类型排队行按 id 升序报位次；非排队态＝0")
    @Test
    void queuePositionCountsClaimableAhead() {
        SaAsyncTask first = submit("ref-1", "u1");
        submit("ref-2", "u1");
        SaAsyncTask third = submit("ref-3", "u1");

        assertEquals(1, stack.service.queuePosition(first));
        assertEquals(3, stack.service.queuePosition(third), "含自己：前面 2 件＋自己＝第 3 位");

        // 抢占成 RUNNING 后不再占排队位次
        stack.runner.claimBatch(TestStack.ThreeStageFlow.TYPE, "owner-a", 5);
        assertEquals(0, stack.service.queuePosition(first), "RUNNING 态＝0");
        assertEquals(0, stack.service.queuePosition(null));
    }

    /**
     * B5 批量读口契约腿：{@code claimableIds} 全量升序；批量换算位次（{@code positionIn}）
     * 与逐行 COUNT（{@code queuePosition(id,type,now)}）逐行等价——含"被抢占行掉出列表后
     * 位次归零、其余行前移"的形态。
     */
    @DisplayName("claimableIds（B5 批量读口）：全量升序；批量位次与逐行 COUNT 等价")
    @Test
    void claimableIdsAscendingAndBatchPositionEqualsPerRowCount() {
        SaAsyncTask first = submit("ref-1", "u1");
        SaAsyncTask second = submit("ref-2", "u1");
        SaAsyncTask third = submit("ref-3", "u1");

        List<Long> ids = stack.service.claimableIds(TestStack.ThreeStageFlow.TYPE);
        assertEquals(Arrays.asList(first.getId(), second.getId(), third.getId()), ids,
                "同类型 claimable 全量、id 升序");

        // 批量换算与逐行 COUNT 同一口径（含自己在内数前面的行）
        for (SaAsyncTask task : Arrays.asList(first, second, third)) {
            assertEquals(stack.repo.queuePosition(task.getId(), TestStack.ThreeStageFlow.TYPE, stack.repo.clock()),
                    AsyncTaskService.positionIn(ids, task.getId()),
                    "批量位次必须与逐行 COUNT 等价: ref=" + task.getBizRef());
            assertEquals(stack.repo.queuePosition(task.getId(), TestStack.ThreeStageFlow.TYPE, stack.repo.clock()),
                    stack.service.queuePosition(task),
                    "改走批量读口后的 queuePosition 与逐行 COUNT 等价: ref=" + task.getBizRef());
        }

        // 只抢占一行：该行掉出列表（位次 0）、其余行位次前移——两条路仍逐行相等
        stack.runner.claimBatch(TestStack.ThreeStageFlow.TYPE, "owner-a", 1);
        List<Long> after = stack.service.claimableIds(TestStack.ThreeStageFlow.TYPE);
        assertEquals(Arrays.asList(second.getId(), third.getId()), after, "被抢占行掉出 claimable 列表");
        for (SaAsyncTask task : Arrays.asList(first, second, third)) {
            assertEquals(stack.repo.queuePosition(task.getId(), TestStack.ThreeStageFlow.TYPE, stack.repo.clock()),
                    stack.service.queuePosition(task),
                    "抢占后批量位次仍与逐行 COUNT 等价: ref=" + task.getBizRef());
        }
        assertEquals(0, stack.service.queuePosition(first), "被抢占行位次＝0");
        assertEquals(1, stack.service.queuePosition(second), "前面只剩 1 件 claimable（含自己）＝第 1 位");
    }
}
