package com.bidr.authorization.audit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: AuditContextTest
 * Description: 审计运行时上下文（ThreadLocal 逃生口）离线单测——
 * put/get/clear 三件套、覆写、缺键、快照隔离、clear 收尾。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
public class AuditContextTest {

    @Before
    public void setUp() {
        AuditContext.clear();
    }

    @After
    public void tearDown() {
        AuditContext.clear();
    }

    @Test
    public void t1_putGetRoundtrip() {
        AuditContext.put("docNo", "D-1");
        assertEquals("D-1", AuditContext.get("docNo"));
    }

    @Test
    public void t2_overwriteWins() {
        AuditContext.put("k", "v1");
        AuditContext.put("k", "v2");
        assertEquals("v2", AuditContext.get("k"));
    }

    @Test
    public void t3_missingKeyIsNull() {
        assertNull(AuditContext.get("absent"));
    }

    @Test
    public void t4_clearEmptiesAll() {
        AuditContext.put("a", "1");
        AuditContext.put("b", "2");
        AuditContext.clear();
        assertNull(AuditContext.get("a"));
        assertNull(AuditContext.get("b"));
        assertTrue(AuditContext.snapshot().isEmpty());
    }

    @Test
    public void t5_snapshotIsIsolatedCopy() {
        AuditContext.put("k", "v");
        Map<String, Object> snap = AuditContext.snapshot();
        assertEquals("v", snap.get("k"));
        AuditContext.put("k2", "v2");
        assertTrue("快照是拷贝——后续 put 不影响已取快照", !snap.containsKey("k2"));
        try {
            snap.put("x", "y");
            fail("快照必须不可变（防 sink 侧误改上下文）");
        } catch (UnsupportedOperationException expected) {
            // 快照只读契约
        }
    }

    @Test
    public void t6_conventionKeysExist() {
        // 约定键与注解 javadoc/切面取值口径一致——改名即翻红（防三处漂移）
        assertEquals("oldValue", AuditContext.OLD_VALUE);
        assertEquals("newValue", AuditContext.NEW_VALUE);
    }
}
