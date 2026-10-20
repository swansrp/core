package com.bidr.authorization.tenant.job;

import com.bidr.authorization.holder.TenantContext;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Title: TenantJobExecutorTest
 * Description: 逐租户执行器离线单测（纯 JUnit4，2026-10-20 下沉 core）——
 * 逐租户上下文套用/恢复、异常透明传播、快照取数。
 * （原 erp 项目侧 TenantForeachRunner 的语义在 core 侧复证；ERP 身份面归业务侧模板，不在本射程。）
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TenantJobExecutorTest {

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    @Test
    public void setsTenantContextPerIteration() {
        List<Long> seen = new ArrayList<>();
        List<Long> currentInAction = new ArrayList<>();
        TenantJobExecutor.runForAllTenants(Arrays.asList(11L, 22L), tenantId -> {
            seen.add(tenantId);
            currentInAction.add(TenantContext.currentTenantId());
            assertEquals(Arrays.asList(tenantId), TenantContext.getTenantIds());
        });
        assertEquals(Arrays.asList(11L, 22L), seen);
        assertEquals(Arrays.asList(11L, 22L), currentInAction);
    }

    @Test
    public void restoresPreviousContext() {
        TenantContext.set(Arrays.asList(7L, 8L));
        TenantContext.setCurrentTenantId(7L);
        TenantJobExecutor.runForAllTenants(Arrays.asList(11L), tid -> { });
        assertEquals(Arrays.asList(7L, 8L), TenantContext.getTenantIds());
        assertEquals(Long.valueOf(7L), TenantContext.currentTenantId());

        TenantContext.remove();
        TenantJobExecutor.runForAllTenants(Arrays.asList(11L), tid -> { });
        assertNull(TenantContext.getTenantIds());
        assertNull(TenantContext.currentTenantId());
    }

    @Test
    public void propagatesExceptionAndRestores() {
        TenantContext.setCurrentTenantId(99L);
        try {
            TenantJobExecutor.runForAllTenants(Arrays.asList(1L, 2L), tid -> {
                if (Long.valueOf(1L).equals(tid)) {
                    throw new IllegalStateException("boom");
                }
            });
            fail("动作异常必须原样上抛（透明传播，不吞）");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
        assertEquals(Long.valueOf(99L), TenantContext.currentTenantId());
    }

    @Test
    public void nullListAndSourceOverload() {
        List<Long> executed = new ArrayList<>();
        TenantJobExecutor.runForAllTenants((List<Long>) null, executed::add);
        assertEquals(0, executed.size());

        AtomicReference<List<Long>> askedFor = new AtomicReference<>();
        List<Long> snapshot = Arrays.asList(31L, 32L);
        TenantJobExecutor.runForAllTenants(() -> {
            askedFor.set(snapshot);
            return snapshot;
        }, executed::add);
        assertSame(snapshot, askedFor.get());
        assertEquals(Arrays.asList(31L, 32L), executed);
    }

    @Test
    public void sourceReturningNullMeansEmpty() {
        List<Long> executed = new ArrayList<>();
        TenantJobExecutor.runForAllTenants(() -> null, executed::add);
        assertEquals(0, executed.size());
        assertNull(TenantContext.currentTenantId());
    }
}
