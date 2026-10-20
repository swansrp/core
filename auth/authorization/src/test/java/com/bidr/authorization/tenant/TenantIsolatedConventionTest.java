package com.bidr.authorization.tenant;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: TenantIsolatedConventionTest
 * Description: {@link TenantIsolated} 标记约定守卫（离线双腿，自证纪律——判据必须证明自己活着）：
 * 正例＝带 {@code tenantId} 属性的标记实体通过；负例＝实现标记而缺 {@code tenantId} 的实体
 * 必须当场抛（fail-fast，消息带实体名与修复指引）——空扫描与恒真判定都不算通过。
 * <p>运行时同一判定挂在 {@code TenantLineIsolationConfiguration.isolatedTables()} 构建腿
 * （首用 fail-fast）；本测试用合成属性清单直喂判定函数，不注册坏探针实体
 * （TableInfoHelper 缓存是 JVM 级静态，坏探针注册后会污染同 JVM 其余测试的隔离集构建）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
public class TenantIsolatedConventionTest {

    @Test
    public void positive_markedEntityWithTenantIdField_passes() {
        // ProbeIsolatedEntity 有 tenantId 属性（探针实体已随 TenantLineIsolationTest 注册进 TableInfo）
        TenantLineIsolationConfiguration.assertIsolatedEntityValid(ProbeIsolatedEntity.class,
                Arrays.asList("id", "tenantId", "name"));
    }

    @Test
    public void negative_markedEntityWithoutTenantId_throwsWithEntityName() {
        try {
            TenantLineIsolationConfiguration.assertIsolatedEntityValid(ProbeFreeEntity.class,
                    Collections.singletonList("id"));
            fail("实现 TenantIsolated 而缺 tenantId 属性必须抛（fail-fast，勿静默漏隔离）");
        } catch (IllegalStateException expected) {
            assertTrue("消息必须带实体名：" + expected.getMessage(),
                    expected.getMessage().contains(ProbeFreeEntity.class.getName()));
            assertTrue("消息必须给修复指引：" + expected.getMessage(),
                    expected.getMessage().contains("tenantId"));
        }
    }

    @Test
    public void negative_synthetics_throwsEvenWithSimilarNames() {
        // 防近似名假阳：tenant_id 字符串形/tenantIds 复数形都不得冒充 tenantId
        assertThrows(IllegalStateException.class, () ->
                TenantLineIsolationConfiguration.assertIsolatedEntityValid(ProbeFreeEntity.class,
                        Arrays.asList("tenant_id", "tenantIds", "tenantIdStr")));
    }

    @Test
    public void marker_semantics_probeEntities() {
        assertTrue(ProbeIsolatedEntity.class.isAssignableFrom(ProbeIsolatedEntity.class));
        assertTrue("探针隔离实体必须实现标记接口",
                TenantIsolated.class.isAssignableFrom(ProbeIsolatedEntity.class));
        assertFalse("对照探针不得实现标记接口", TenantIsolated.class.isAssignableFrom(ProbeFreeEntity.class));
    }

    private void assertFalse(String message, boolean condition) {
        org.junit.Assert.assertFalse(message, condition);
    }
}
