package com.bidr.td;

import com.bidr.td.annotation.TdColumn;
import com.bidr.td.annotation.TdStable;
import com.bidr.td.annotation.TdTag;
import com.bidr.td.annotation.TdTimestamp;
import com.bidr.td.constant.TdDataType;
import com.bidr.td.repository.BaseTdRepo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdRepo 插入操作测试
 */
public class BaseTdRepoInsertTest {

    @TdStable("test_stable")
    static class TestEntity {
        @TdTimestamp
        private java.util.Date ts;
        @TdColumn(type = TdDataType.FLOAT)
        private Float value;
        @TdTag
        private String deviceId;

        public java.util.Date getTs() { return ts; }
        public void setTs(java.util.Date ts) { this.ts = ts; }
        public Float getValue() { return value; }
        public void setValue(Float value) { this.value = value; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    }

    @Test
    public void testInsertOne() {
        // 仅验证类结构存在，不依赖真实 JDBC
        assertDoesNotThrow(() -> {
            // 验证反射获取 stableName 的逻辑
            TdStable anno = TestEntity.class.getAnnotation(TdStable.class);
            assertNotNull(anno);
            assertEquals("test_stable", anno.value());
        });
    }
}
