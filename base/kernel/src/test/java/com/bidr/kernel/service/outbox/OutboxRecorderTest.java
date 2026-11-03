package com.bidr.kernel.service.outbox;

import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.repository.SyncOutboxService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: OutboxRecorderTest
 * Description: 变更事件记录器单测（T3.3a，JUnit4＋Mockito）——不连库，只钉"透传面"：
 * <ul>
 *   <li>五个入参原样写进实体（任一被改写＝对端按错误的聚合/类型过滤，静默丢事件）；</li>
 *   <li>{@code occurredAt} 在 record 内取（事件时刻不是落库时刻，留空则保留期无边界可依）；</li>
 *   <li>{@code save} 只调一次且没有别的交互——发件箱语义是"一条变更一行事件"，
 *       多写或顺手查一次表都会让调用方的事务多背东西；</li>
 *   <li>负对照：一次 {@code record} 不得产生第二行（防"写两遍"这类改动悄悄通过）。</li>
 * </ul>
 * <p>
 * 刻意不对 {@code save} 的返回值做断言：record 返回 void，落库成败由调用方的事务语义决定
 * （D2：核心不 try/catch 也不自开事务），这层不测。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class OutboxRecorderTest {

    private SyncOutboxService syncOutboxService;
    private OutboxRecorder recorder;

    @Before
    public void setUp() {
        syncOutboxService = Mockito.mock(SyncOutboxService.class);
        recorder = new OutboxRecorder(syncOutboxService);
    }

    @Test
    public void recordPassesAllFiveArgumentsThrough() {
        recorder.record(30L, "USER", 7L, "UPDATED", "{\"name\":\"王五\"}");

        SyncOutbox event = captureSingleSavedEvent();
        Assert.assertEquals("tenantId 必须原样透传（空＝全局事件，非空＝该租户）", Long.valueOf(30L), event.getTenantId());
        Assert.assertEquals("USER", event.getAggType());
        Assert.assertEquals(Long.valueOf(7L), event.getAggId());
        Assert.assertEquals("UPDATED", event.getEventType());
        Assert.assertEquals("{\"name\":\"王五\"}", event.getPayload());
        Assert.assertNotNull("occurredAt 必须在 record 内取（事件时刻），留空则保留期无边界可依",
                event.getOccurredAt());
        // 落库时刻交给库默认值：核心不替库决定时钟口径
        Assert.assertNull("createAt 不该由记录器填（DDL 的 DEFAULT CURRENT_TIMESTAMP(3) 是唯一时钟源）",
                event.getCreateAt());
    }

    /** 全局事件腿：tenantId 允许为空，不得被"补"成任何值 */
    @Test
    public void recordKeepsNullTenantIdAsGlobalEvent() {
        recorder.record(null, "ORG", 1L, "CREATED", null);

        SyncOutbox event = captureSingleSavedEvent();
        Assert.assertNull("tenantId=null 是合法形态（全局事件），记录器不得补值", event.getTenantId());
        Assert.assertNull(event.getPayload());
        Assert.assertEquals("ORG", event.getAggType());
    }

    /** 负对照腿：一次 record 只能落一行，且除了 save 不许有别的交互 */
    @Test
    public void recordSavesExactlyOnceAndTouchesNothingElse() {
        recorder.record(1L, "PROJECT", 2L, "DELETED", "{}");

        Mockito.verify(syncOutboxService, Mockito.times(1)).save(Mockito.any(SyncOutbox.class));
        Mockito.verifyNoMoreInteractions(syncOutboxService);
    }

    private SyncOutbox captureSingleSavedEvent() {
        ArgumentCaptor<SyncOutbox> captor = ArgumentCaptor.forClass(SyncOutbox.class);
        Mockito.verify(syncOutboxService).save(captor.capture());
        return captor.getValue();
    }
}