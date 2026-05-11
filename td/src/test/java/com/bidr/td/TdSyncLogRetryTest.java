package com.bidr.td;

import com.bidr.td.dao.entity.TdSyncLog;
import com.bidr.td.dao.entity.TdTagMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdSyncLog 重试机制测试
 */
public class TdSyncLogRetryTest {

    @Test
    public void testTdSyncLogEntity() {
        TdSyncLog log = new TdSyncLog();
        log.setBizId("biz001");
        log.setSyncType("INSERT");
        log.setSyncStatus(0);
        log.setRetryCount(0);

        assertEquals("biz001", log.getBizId());
        assertEquals("INSERT", log.getSyncType());
        assertEquals(0, log.getSyncStatus().intValue());
        assertEquals(0, log.getRetryCount().intValue());
    }

    @Test
    public void testTdTagMappingEntity() {
        TdTagMapping mapping = new TdTagMapping();
        mapping.setSubTableName("sub_dev001");
        mapping.setBizId("biz001");
        mapping.setStableName("test_stable");
        mapping.setTagJson("{\"deviceId\":\"dev001\"}");

        assertEquals("sub_dev001", mapping.getSubTableName());
        assertEquals("biz001", mapping.getBizId());
        assertEquals("test_stable", mapping.getStableName());
    }
}
