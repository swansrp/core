package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A6 机器腿（M16 修复轮）：{@code submit} 的 find-then-insert 之间必有竞态（内容寻址键如
 * {@code material:{业务维度}:{blobId}} 必撞）——唯一键 {@code uk_type_biz} 是最终裁决者，
 * 撞键时<b>重读返回既有行</b>，不向调用方抛 500、也不新建第二行。
 *
 * @author sharp
 */
class AsyncTaskSubmitRaceTest {

    /** 模拟「另一实例在我 insert 之前抢先落库」：内存库先写赢家行，再抛唯一键冲突 */
    static class RacingInsertRepository extends InMemoryAsyncTaskRepository {

        private final boolean winnerExists;

        private boolean armed = true;

        RacingInsertRepository(boolean winnerExists) {
            this.winnerExists = winnerExists;
        }

        @Override
        public SaAsyncTask insert(SaAsyncTask task) {
            if (armed) {
                armed = false;
                if (winnerExists) {
                    SaAsyncTask winner = new SaAsyncTask();
                    winner.setTaskType(task.getTaskType());
                    winner.setBizRef(task.getBizRef());
                    winner.setStage(task.getStage());
                    winner.setStatus(AsyncTaskStatus.PENDING.name());
                    winner.setAttempt(0);
                    winner.setCreateBy(task.getCreateBy());
                    super.insert(winner); // 另一实例先落库
                }
                throw new DuplicateKeyException("Duplicate entry for key 'uk_type_biz'");
            }
            return super.insert(task);
        }
    }

    @Test
    @DisplayName("A6：并发撞唯一键 → 重读返回既有行（不抛、不新建第二行）")
    void duplicateKeyReturnsExistingRow() {
        RacingInsertRepository repo = new RacingInsertRepository(true);
        TestStack stack = new TestStack(false, 1000, repo, new TestStack.ThreeStageFlow());

        SaAsyncTask handle = stack.service.submit("THREE_STAGE", "ref-race", null, "E1");
        SaAsyncTask existed = stack.service.find("THREE_STAGE", "ref-race");
        assertEquals(existed.getId(), handle.getId(), "返回的是赢家行（不是本次新建的行）");
        assertEquals(1L, handle.getId(), "赢家先落库＝id 1");
        assertThat(repo.events).as("只落一行（本次 insert 被吃掉）").containsExactly("insert:1");
    }

    @Test
    @DisplayName("A6：撞键但重读不到行 → 原样上抛（不吞异常）")
    void duplicateKeyWithoutExistingRowIsRethrown() {
        RacingInsertRepository repo = new RacingInsertRepository(false);
        TestStack stack = new TestStack(false, 1000, repo, new TestStack.ThreeStageFlow());

        assertThrows(DuplicateKeyException.class,
                () -> stack.service.submit("THREE_STAGE", "ref-race2", null, "E1"),
                "重读不到行＝不是幂等竞态，异常必须上抛");
    }
}
