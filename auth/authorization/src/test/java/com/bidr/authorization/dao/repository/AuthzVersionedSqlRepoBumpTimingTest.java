package com.bidr.authorization.dao.repository;

import com.bidr.authorization.authz.AuthzVersionHolder;
import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Title: AuthzVersionedSqlRepoBumpTimingTest
 * Description: 两段式 bump 时序单测（对账单 #24：review 第 2 轮棒4 I-1 修复）——
 * 不起 Spring、不连库，直接驱动 {@link TransactionSynchronizationManager} 的线程态：
 * <ol>
 *   <li>无活动事务同步（autocommit 单语句形态）：单次立即 bump，零注册；</li>
 *   <li>有活动事务同步：立即 bump 一次＋注册一个 afterCommit 同步——手动触发 afterCommit
 *       再 bump 一次（模拟 commit 后的第二段）；</li>
 *   <li>success=false：零 bump 零注册；</li>
 *   <li>同一事务内多次写：多次注册（版本单调，多次 +1 无语义影响——与类 javadoc 一致）。</li>
 * </ol>
 * 判据全走 {@link AuthzVersionHolder#current()} 相对差值（bump 为静态单调计数器），
 * 不依赖绝对值，天然与其他测试隔离。
 *
 * @author main-thread (对账单 #24 修复, 2026-11-03)
 * @since 2026-11-03
 */
public class AuthzVersionedSqlRepoBumpTimingTest {

    @After
    public void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 腿1：无事务同步＝autocommit 形态——单次立即 bump、零注册（提交即完成，无窗口） */
    @Test
    public void 无事务同步_单次立即bump_零注册() {
        long before = AuthzVersionHolder.current();
        // 前置：确无活动事务同步（getSynchronizations() 在非活动态本就抛 IllegalState，
        // 故零注册的判据＝「调用不抛」＋「同步态始终非活动」——误注册在此即炸）
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(true);
        assertEquals(before + 1, AuthzVersionHolder.current());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    /**
     * 腿2：有活动事务同步——立即 bump 一次＋注册一个 afterCommit；手动触发 afterCommit
     * 再 bump 一次（即真实 commit 后容器回调的等价形态）。两段各 +1＝双 bump 落地。
     */
    @Test
    public void 有事务同步_立即bump加afterCommit补bump_两段各加一() {
        long before = AuthzVersionHolder.current();
        TransactionSynchronizationManager.initSynchronization();
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(true);

        // 段一：立即 bump 已发生
        assertEquals(before + 1, AuthzVersionHolder.current());
        // 且恰注册一个 afterCommit 同步
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());

        // 段二：模拟 commit 成功后的容器回调
        syncs.get(0).afterCommit();
        assertEquals(before + 2, AuthzVersionHolder.current());
    }

    /** 腿3：写失败（success=false）零 bump 零注册 */
    @Test
    public void 写失败_零bump零注册() {
        long before = AuthzVersionHolder.current();
        TransactionSynchronizationManager.initSynchronization();
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(false);
        assertEquals(before, AuthzVersionHolder.current());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    /** 腿4：同一事务内多次写＝多次注册，afterCommit 逐一补 bump（版本单调，无语义影响） */
    @Test
    public void 同一事务内三次写_注册三个同步_逐一补bump() {
        long before = AuthzVersionHolder.current();
        TransactionSynchronizationManager.initSynchronization();
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(true);
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(true);
        AuthzVersionedSqlRepo.bumpOnWriteCommitted(true);

        // 段一：三次立即 bump
        assertEquals(before + 3, AuthzVersionHolder.current());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(3, syncs.size());

        // 段二：commit 后容器逐一回调
        for (TransactionSynchronization sync : syncs) {
            sync.afterCommit();
        }
        assertEquals(before + 6, AuthzVersionHolder.current());
    }
}
