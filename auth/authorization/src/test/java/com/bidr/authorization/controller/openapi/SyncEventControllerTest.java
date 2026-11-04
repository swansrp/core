package com.bidr.authorization.controller.openapi;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthPartner;
import com.bidr.authorization.vo.sync.SyncEventRes;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.repository.SyncOutboxService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Title: SyncEventControllerTest
 * Description: 变更事件拉取端点单测（T3.3a，JUnit4＋Mockito）——不连库、不起 Web 容器，
 * 直调 {@code events(cursor, limit)} 钉三件事：
 * <ol>
 *   <li><b>limit 归一</b>：没填/非正一律退化成 500，超上限按 1000 硬收敛。
 *       上限失效不会报错，只会让一个手误的对端把全表拉走——所以正向腿旁边必须有一条
 *       "刚好越界一格（1001）"的负对照腿，证明收敛不是靠"总之不会那么大"；</li>
 *   <li><b>cursor 原样透传</b>（含 null）：改写游标＝对端静默漏事件，且漏得毫无痕迹；</li>
 *   <li><b>实体→VO 映射</b>正确，且 {@code createAt} 不外发（响应里没有这一面）。</li>
 * </ol>
 * <p>
 * 另钉一条契约腿：类级 {@code @Auth(AuthPartner.class)}——这是系统级机器凭证口，
 * 掉成默认登录鉴权＝外部系统再也进不来，而单测不覆盖鉴权时没人会发现。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class SyncEventControllerTest {

    private SyncOutboxService syncOutboxService;
    private SyncEventController controller;

    @Before
    public void setUp() {
        syncOutboxService = Mockito.mock(SyncOutboxService.class);
        controller = new SyncEventController(syncOutboxService);
        Mockito.when(syncOutboxService.listAfter(Mockito.any(), Mockito.anyInt()))
                .thenReturn(Collections.<SyncOutbox>emptyList());
    }

    @Test
    public void nullLimitFallsBackToDefault500() {
        controller.events(null, null);

        Assert.assertEquals("limit 未填＝500（对端不传 limit 是常态）", 500, capturedLimit());
    }

    @Test
    public void zeroLimitFallsBackToDefault500() {
        controller.events(null, 0);

        Assert.assertEquals("limit=0 不得原样下传（LIMIT 0＝对端永远拉不到东西，且不会报错）",
                500, capturedLimit());
    }

    @Test
    public void negativeLimitFallsBackToDefault500() {
        controller.events(null, -5);

        Assert.assertEquals("limit 为负不得原样下传", 500, capturedLimit());
    }

    @Test
    public void oversizedLimitCollapsesToMax1000() {
        controller.events(null, 5000);

        Assert.assertEquals("超过上限按 1000 收（一次拉十万行会拖住本端连接与内存）", 1000, capturedLimit());
    }

    /** 负对照腿：刚好越界一格也必须收敛，防"上限没生效"被正向腿的宽松数值掩盖 */
    @Test
    public void limitJustAboveMaxStillCollapses() {
        controller.events(null, 1001);

        Assert.assertEquals("1001 必须收敛到 1000（上限刚越界就没兜住＝上限形同虚设）", 1000, capturedLimit());
    }

    @Test
    public void inRangeLimitPassesThroughUnchanged() {
        controller.events(null, 100);

        Assert.assertEquals("区间内的 limit 原样下传", 100, capturedLimit());
    }

    @Test
    public void cursorPassesThroughIncludingNull() {
        controller.events(1234L, 10);
        Assert.assertEquals("游标必须原样透传（改写＝对端静默漏事件）",
                Long.valueOf(1234L), capturedCursor());
    }

    @Test
    public void nullCursorMeansPullFromStart() {
        controller.events(null, 10);

        Assert.assertNull("不传游标＝从头拉，不得被补成 0 或任何值", capturedCursor());
    }

    @Test
    public void entityMapsToVoAndCreateAtIsNotExposed() {
        Date occurredAt = new Date(1767225600000L);
        SyncOutbox event = new SyncOutbox();
        event.setId(99L);
        event.setTenantId(30L);
        event.setAggType("USER");
        event.setAggId(7L);
        event.setEventType("UPDATED");
        event.setPayload("{\"name\":\"王五\"}");
        event.setOccurredAt(occurredAt);
        event.setCreateAt(new Date());
        Mockito.when(syncOutboxService.listAfter(Mockito.any(), Mockito.anyInt()))
                .thenReturn(Collections.singletonList(event));

        List<SyncEventRes> res = controller.events(null, null);

        Assert.assertEquals(1, res.size());
        SyncEventRes item = res.get(0);
        Assert.assertEquals(Long.valueOf(99L), item.getId());
        Assert.assertEquals("tenantId 抽查：空＝全局事件的语义靠它承载", Long.valueOf(30L), item.getTenantId());
        Assert.assertEquals("USER", item.getAggType());
        Assert.assertEquals(Long.valueOf(7L), item.getAggId());
        Assert.assertEquals("UPDATED", item.getEventType());
        Assert.assertEquals("{\"name\":\"王五\"}", item.getPayload());
        Assert.assertEquals("occurredAt 抽查：对端按它判新旧", occurredAt, item.getOccurredAt());
        // createAt 不在 VO 里：字段缺失即外发面收窄，反射扫一遍防止日后"顺手补上"
        boolean createAtExposed = false;
        for (java.lang.reflect.Field field : SyncEventRes.class.getDeclaredFields()) {
            if ("createAt".equals(field.getName())) {
                createAtExposed = true;
            }
        }
        Assert.assertFalse("SyncEventRes 不得外发 createAt（落库时刻对消费方无意义，多一面就多一个误用点）",
                createAtExposed);
    }

    /** 空表腿：发件箱还没有任何事件时，端点要答空列表而不是 null/异常 */
    @Test
    public void emptyOutboxAnswersEmptyList() {
        List<SyncEventRes> res = controller.events(null, null);

        Assert.assertNotNull("空表必须答空列表（null 会让消费方 NPE 在反序列化之后才炸）", res);
        Assert.assertTrue(res.isEmpty());
    }

    /** 契约腿：类级机器凭证鉴权 */
    @Test
    public void classLevelAuthIsPartner() {
        Auth auth = SyncEventController.class.getAnnotation(Auth.class);

        Assert.assertNotNull("SyncEventController 必须挂类级 @Auth（否则走默认登录鉴权，外部系统进不来）", auth);
        Assert.assertTrue("类级鉴权角色必须是 AuthPartner（机器凭证 x-api-key），实际="
                + Arrays.toString(auth.value()), Arrays.asList(auth.value()).contains(AuthPartner.class));
    }

    private int capturedLimit() {
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(syncOutboxService).listAfter(Mockito.any(), limit.capture());
        return limit.getValue();
    }

    private Long capturedCursor() {
        ArgumentCaptor<Long> cursor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(syncOutboxService).listAfter(cursor.capture(), limit.capture());
        return cursor.getValue();
    }
}