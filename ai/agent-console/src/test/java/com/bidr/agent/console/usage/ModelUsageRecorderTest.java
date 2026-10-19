package com.bidr.agent.console.usage;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dao.repository.ModelUsageService;
import com.bidr.llm.usage.ModelCallContext;
import com.bidr.llm.usage.ModelUsageFacts;
import com.bidr.llm.usage.ModelUsageReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: ModelUsageRecorderTest
 * Description: 用量落库腿契约测试（M14 棒5）。钉五条：① 事实→行的映射形态（🔴 NULL 语义不许被补成 0、
 * 空白栏落 NULL 不落空串、两个时刻列各归各位）；② 一次事实一行、异步入库（上报线程不等 INSERT）；
 * ③ 仓储炸只丢那一行、线程不死；④ 队列满按<b>丢数</b>处理并计数可见，绝不无界堆积；
 * ⑤ 停机时把在途事实排干，不丢最后几行。全程 mock 仓储，不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class ModelUsageRecorderTest {

    private static final String FP = "db7dd228b1b8babc";

    private ModelUsageRecorder recorder;

    @AfterEach
    void shutdown() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    private static ModelUsageFacts fullFacts() {
        ModelUsageFacts facts = ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SSE, "AGENT", "qwen-max",
                FP, new ModelCallContext("s-1", "inkhub-check", "bidrAdmin"));
        facts.setPromptTokens(12);
        facts.setCompletionTokens(30);
        facts.setReasoningTokens(7);
        facts.setTotalTokens(42);
        facts.setToolCount(1);
        facts.setFinishReason("tool_calls");
        facts.setCostMs(1234L);
        return facts;
    }

    // ---------------- 映射形态 ----------------

    @Test
    @DisplayName("事实→行：载体落 call_kind，发生时刻落 call_at，valid 置 1")
    void mapsEveryCarriedColumn() {
        ModelUsageFacts facts = fullFacts();
        ModelUsage row = ModelUsageRecorder.toRow(facts);

        assertEquals(ModelUsageFacts.VIA_SSE, row.getCallKind());
        assertEquals("AGENT", row.getPurposeType());
        assertEquals("qwen-max", row.getModelName());
        assertEquals(FP, row.getKeyFp());
        assertEquals("s-1", row.getSessionId());
        assertEquals("inkhub-check", row.getAgentKey());
        assertEquals("bidrAdmin", row.getAskedBy());
        assertEquals(Integer.valueOf(12), row.getPromptTokens());
        assertEquals(Integer.valueOf(30), row.getCompletionTokens());
        assertEquals(Integer.valueOf(7), row.getReasoningTokens());
        assertEquals(Integer.valueOf(42), row.getTotalTokens());
        assertEquals(Integer.valueOf(1), row.getToolCount());
        assertEquals("tool_calls", row.getFinishReason());
        assertEquals(Long.valueOf(1234L), row.getCostMs());
        assertEquals(new Date(facts.getCallAtMs()), row.getCallAt());
        assertEquals("1", row.getValid());
        assertNull(row.getCreateBy(), "create_by 由落库线程负责（无登录上下文即留空），不由事实伪造");
    }

    /** 🔴 这条是计量表最容易做错的地方：把"没拿到"写成 0，按人／按空间汇总就会系统性少计 */
    @Test
    @DisplayName("网关没回的读数保持 NULL，不补 0、不补空串")
    void keepsNullSemanticsOfMissingReadings() {
        ModelUsageFacts facts = ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SYNC, "DEFAULT",
                "qwen-lite", null, null);
        ModelUsage row = ModelUsageRecorder.toRow(facts);

        assertNull(row.getPromptTokens());
        assertNull(row.getCompletionTokens());
        assertNull(row.getReasoningTokens());
        assertNull(row.getTotalTokens());
        assertNull(row.getToolCount());
        assertNull(row.getFinishReason());
        assertNull(row.getCostMs());
        assertNull(row.getKeyFp());
        assertNull(row.getSessionId());
        assertNull(row.getAgentKey());
        assertNull(row.getAskedBy());
    }

    @Test
    @DisplayName("空白身份栏落 NULL：空串会冒充一份\"有会话但会话号为空\"的假账")
    void blankIdentityColumnsBecomeNull() {
        ModelUsageFacts facts = ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SYNC, "  ", "  ", FP,
                new ModelCallContext("   ", "", null));
        ModelUsage row = ModelUsageRecorder.toRow(facts);

        assertNull(row.getPurposeType());
        assertNull(row.getModelName());
        assertNull(row.getSessionId());
        assertNull(row.getAgentKey());
        assertEquals(FP, row.getKeyFp(), "指纹不是身份栏，照常落");
    }

    // ---------------- 异步落库 ----------------

    @Test
    @DisplayName("一条事实一行，且上报线程不等 INSERT")
    void reportsAsynchronouslyOneRowPerFact() {
        ModelUsageService repo = Mockito.mock(ModelUsageService.class);
        recorder = new ModelUsageRecorder(repo);
        recorder.start();

        long startAt = System.currentTimeMillis();
        recorder.report(fullFacts());
        long elapsed = System.currentTimeMillis() - startAt;

        ArgumentCaptor<ModelUsage> captor = ArgumentCaptor.forClass(ModelUsage.class);
        Mockito.verify(repo, Mockito.timeout(3000)).insert(captor.capture());
        assertEquals("s-1", captor.getValue().getSessionId());
        assertTrue(elapsed < 200, "report 不得阻塞在 INSERT 上（实测 " + elapsed + "ms）");
    }

    @Test
    @DisplayName("null 事实不入队、不查库")
    void nullFactIsIgnored() {
        ModelUsageService repo = Mockito.mock(ModelUsageService.class);
        recorder = new ModelUsageRecorder(repo);
        recorder.start();

        recorder.report(null);
        assertEquals(0, recorder.droppedCount());
        Mockito.verify(repo, Mockito.after(300).never()).insert(Mockito.any(ModelUsage.class));
    }

    /** 🔴 同族事故形态：旁证写不进去把主链路带崩。这里 INSERT 连炸两次，线程须照跑、第二行仍尝试落库 */
    @Test
    @DisplayName("仓储抛错只丢那一行：线程存活，后续事实照落")
    void insertFailureIsSwallowedAndLoopSurvives() {
        ModelUsageService repo = Mockito.mock(ModelUsageService.class);
        AtomicInteger attempts = new AtomicInteger();
        Mockito.when(repo.insert(Mockito.any(ModelUsage.class))).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("库不通");
            }
            return true;
        });
        recorder = new ModelUsageRecorder(repo);
        recorder.start();

        recorder.report(factsOf("s-bad"));
        recorder.report(factsOf("s-good"));

        Mockito.verify(repo, Mockito.timeout(3000).atLeast(2)).insert(Mockito.any(ModelUsage.class));
        assertEquals(0, recorder.droppedCount(), "INSERT 失败不算丢数（丢数指队列满）");
    }

    /** 队列有界：库长时间不通时宁可丢数也不无界堆积（堆积＝把 OOM 换来一行统计） */
    @Test
    @DisplayName("队列满即丢弃并计数，上报线程始终不阻塞")
    void fullQueueDropsAndCountsInsteadOfGrowing() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        ModelUsageService repo = Mockito.mock(ModelUsageService.class);
        Mockito.when(repo.insert(Mockito.any(ModelUsage.class))).thenAnswer(invocation -> {
            block.await(10, TimeUnit.SECONDS);
            return true;
        });
        recorder = new ModelUsageRecorder(repo);
        recorder.start();

        long startAt = System.currentTimeMillis();
        // 远超容量： worker 最多攒 BATCH_SIZE 在手里，其余必须撞上队列边界
        for (int i = 0; i < ModelUsageRecorder.QUEUE_CAPACITY * 2; i++) {
            recorder.report(factsOf("s-" + i));
        }
        long elapsed = System.currentTimeMillis() - startAt;

        assertTrue(recorder.droppedCount() > 0,
                "丢数须计数可见（否则统计读不出这段是下限）: dropped=" + recorder.droppedCount());
        assertTrue(elapsed < 2000, "队列满也不得让上报线程等待（实测 " + elapsed + "ms）");
        block.countDown();
    }

    @Test
    @DisplayName("停机把在途事实排干：最后几行不丢")
    void stopDrainsPendingFacts() {
        ModelUsageService repo = Mockito.mock(ModelUsageService.class);
        recorder = new ModelUsageRecorder(repo);
        recorder.start();

        for (int i = 0; i < 20; i++) {
            recorder.report(factsOf("s-" + i));
        }
        recorder.stop();
        recorder = null;

        ArgumentCaptor<ModelUsage> captor = ArgumentCaptor.forClass(ModelUsage.class);
        Mockito.verify(repo, Mockito.timeout(5000).times(20)).insert(captor.capture());
        assertEquals(20, captor.getAllValues().size(), "20 条上报须落满 20 行");
    }

    private static ModelUsageFacts factsOf(String sessionId) {
        return ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SYNC, "AGENT", "qwen-max", FP,
                new ModelCallContext(sessionId, "inkhub-check", "bidrAdmin"));
    }
}
