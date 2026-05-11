package com.bidr.td;

import com.bidr.td.annotation.TdColumn;
import com.bidr.td.annotation.TdStable;
import com.bidr.td.annotation.TdTag;
import com.bidr.td.annotation.TdTimestamp;
import com.bidr.td.constant.TdDataType;
import com.bidr.td.repository.BaseTdSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdSchema 测试
 */
public class BaseTdSchemaTest {

    @TdStable("test_stable")
    static class TestEntity {
        @TdTimestamp
        private java.util.Date ts;
        @TdColumn(type = TdDataType.FLOAT)
        private Float value;
        @TdTag
        private String deviceId;
    }

    @Test
    public void testBuildCreateStableSql() {
        // 验证通过注解构建 CREATE STABLE SQL
        TestSchema schema = new TestSchema();
        String stableName = schema.getStableName();
        assertEquals("test_stable", stableName);

        String createSql = schema.getCreateStableSql();
        assertNotNull(createSql);
        assertTrue(createSql.contains("CREATE STABLE IF NOT EXISTS test_stable"));
        assertTrue(createSql.contains("ts TIMESTAMP"));
        assertTrue(createSql.contains("value FLOAT"));
        assertTrue(createSql.contains("deviceId BINARY(64)"));
    }

    static class TestSchema extends BaseTdSchema<TestEntity> {
    }
}
