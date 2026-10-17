package com.bidr.td;

import com.bidr.td.dao.entity.TdTagMapping;
import com.bidr.td.dao.schema.TdTagMappingSchema;
import com.bidr.td.dao.schema.TdSyncLogSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdTagMappingSchema 测试
 */
public class TdTagMappingSchemaTest {

    @Test
    public void testSchemaCreation() {
        TdTagMappingSchema schema = new TdTagMappingSchema();
        assertNotNull(schema);
        assertEquals("td_tag_mapping", schema.getTableName());
        assertNotNull(schema.getCreateSql());
        assertTrue(schema.getCreateSql().contains("CREATE TABLE"));
        assertTrue(schema.getCreateSql().contains("td_tag_mapping"));
    }

    @Test
    public void testSyncLogSchemaCreation() {
        TdSyncLogSchema schema = new TdSyncLogSchema();
        assertNotNull(schema);
        assertEquals("td_sync_log", schema.getTableName());
        assertNotNull(schema.getCreateSql());
        assertTrue(schema.getCreateSql().contains("CREATE TABLE"));
        assertTrue(schema.getCreateSql().contains("td_sync_log"));
    }
}
