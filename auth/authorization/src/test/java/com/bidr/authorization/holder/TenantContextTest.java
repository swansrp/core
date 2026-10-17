package com.bidr.authorization.holder;

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
}
