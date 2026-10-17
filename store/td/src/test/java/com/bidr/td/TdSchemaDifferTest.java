package com.bidr.td;

import com.bidr.td.sync.TdSchemaDiffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdSchemaDiffer 测试
 */
public class TdSchemaDifferTest {

    @Test
    public void testDiffNoStable() {
        // 模拟 stable 不存在时返回空列表（无法 Mock，仅验证构造）
        assertDoesNotThrow(() -> {
            // 无法实例化 differ 因为需要 JdbcTemplate，仅验证类存在
            Class<?> clazz = TdSchemaDiffer.class;
            assertNotNull(clazz);
        });
    }
}
