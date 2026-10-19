package com.bidr.td;

import com.bidr.td.controller.TdTagMappingController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdTagMappingController 测试
 */
public class TdTagMappingControllerTest {

    @Test
    public void testControllerCreation() {
        // 仅验证类结构存在
        assertDoesNotThrow(() -> {
            Class<?> clazz = TdTagMappingController.class;
            assertNotNull(clazz);
            // 验证继承自 BaseAdminController
            assertEquals(com.bidr.kernel.controller.BaseAdminController.class, clazz.getSuperclass());
        });
    }
}
