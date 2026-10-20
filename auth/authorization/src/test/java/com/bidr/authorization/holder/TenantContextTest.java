package com.bidr.authorization.holder;

import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: TenantContextTest
 * Description: 租户上下文持有器离线单测（纯 JUnit4）——钉死棒1 写死的口径：
 * 未 set ⇒ getTenantIds()=null（旁路形态）；set 空列表 ⇒ 空列表（无归属）；
 * isPlatform() 未标记=false；remove() 必须清干净两个 ThreadLocal（防线程池串档）。
 * <p>2026-10-20 补：requireCurrentTenantId 的 fail-closed 异常族（用户可触发的拒绝＝
 * ServiceException，只有它的 message 进前端 details）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantContextTest {

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    @Test
    public void unsetContextYieldsNullBypassAndNonPlatform() {
        TenantContext.remove();
        Assert.assertNull(TenantContext.getTenantIds());
        Assert.assertFalse(TenantContext.isPlatform());
    }

    @Test
    public void setThenGetReturnsSameIds() {
        List<Long> ids = Arrays.asList(1L, 2L);
        TenantContext.set(ids);
        Assert.assertEquals(ids, TenantContext.getTenantIds());
        Assert.assertEquals(ids, TenantContext.get());
        Assert.assertFalse("set 单参重载不得顺手置平台档", TenantContext.isPlatform());
    }

    @Test
    public void setNullYieldsNullBypass() {
        TenantContext.set(Arrays.asList(3L));
        TenantContext.set(null);
        Assert.assertNull(TenantContext.getTenantIds());
    }

    @Test
    public void setEmptyListYieldsEmptyListNotBypass() {
        TenantContext.set(Collections.<Long>emptyList());
        List<Long> ids = TenantContext.getTenantIds();
        Assert.assertNotNull("holder 层如实保留空列表形态（闸侧按终裁与 null 同旁路，见 TenantContext 注释）", ids);
        Assert.assertTrue(ids.isEmpty());
    }

    @Test
    public void platformFlagRoundTrip() {
        TenantContext.set(Arrays.asList(4L), true);
        Assert.assertTrue(TenantContext.isPlatform());
        TenantContext.set(Arrays.asList(4L), false);
        Assert.assertFalse(TenantContext.isPlatform());
        TenantContext.markPlatform();
        Assert.assertTrue(TenantContext.isPlatform());
        Assert.assertEquals(Arrays.asList(4L), TenantContext.getTenantIds());
    }

    @Test
    public void removeClearsBothThreadLocals() {
        TenantContext.set(Arrays.asList(5L), true);
        TenantContext.remove();
        Assert.assertNull(TenantContext.getTenantIds());
        Assert.assertFalse("remove 后平台档必须回落 false（防线程池串档）", TenantContext.isPlatform());
    }

    /**
     * 2026-10-19 扩展的两槽（当前租户、隔离开关）也必须被 remove 带走：
     * 线程池复用时残留上一请求的当前租户＝下一请求的写入直接落进别租户面（跨租户写通道）。
     * 断言取在 runWithoutIsolation 段<b>内</b>——段外的 false 可能是外层 finally 恢复的，
     * 判不出 remove() 自己有没有清（负向腿口径见 [[feedback-tests-alongside-and-progress-honesty]]）。
     */
    @Test
    public void removeClearsCurrentTenantAndIsolationSlots() {
        TenantContext.set(Arrays.asList(5L));
        TenantContext.setCurrentTenantId(5L);
        final boolean[] isolationOffAfterRemove = new boolean[1];
        final Long[] currentAfterRemove = new Long[]{5L};
        TenantContext.callWithoutIsolation(() -> {
            TenantContext.remove();
            isolationOffAfterRemove[0] = TenantContext.isIsolationOff();
            currentAfterRemove[0] = TenantContext.currentTenantId();
            return null;
        });
        Assert.assertNull("remove 必须清当前租户槽", currentAfterRemove[0]);
        Assert.assertFalse("remove 必须清隔离开关槽", isolationOffAfterRemove[0]);
        Assert.assertFalse("段结束后开关不得自行复活", TenantContext.isIsolationOff());
    }

    /** 读侧 fail-closed 的异常族（2026-10-20 用户拍板）：拒绝类失败须 ServiceException 才进得了前端 details */
    @Test
    public void requireCurrentTenantIdRejectsWithActionableMessageWhenUnselected() {
        TenantContext.set(Arrays.asList(7L));
        try {
            TenantContext.requireCurrentTenantId();
            Assert.fail("租户集非空但未选当前租户必须 fail-closed");
        } catch (ServiceException expected) {
            Assert.assertTrue("文案须给可行动出口：" + expected.getMessage(),
                    expected.getMessage().contains("请先选择租户"));
        }
        TenantContext.setCurrentTenantId(7L);
        Assert.assertEquals("负向腿：已选租户时正常返回、不抛", Long.valueOf(7L),
                TenantContext.requireCurrentTenantId());
    }
}
