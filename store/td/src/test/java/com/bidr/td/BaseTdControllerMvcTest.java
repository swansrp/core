package com.bidr.td;

import com.bidr.td.controller.BaseTdController;
import com.bidr.td.vo.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdController MVC 测试
 */
public class BaseTdControllerMvcTest {

    @Test
    public void testControllerStructure() {
        // 验证类存在及结构
        assertDoesNotThrow(() -> {
            Class<?> clazz = BaseTdController.class;
            assertNotNull(clazz);
            assertTrue(Modifier.isAbstract(clazz.getModifiers()));
        });
    }

    @Test
    public void testVoClasses() {
        // 验证所有 VO 类可正常创建
        assertNotNull(new TdRangeReq());
        assertNotNull(new TdLastReq());
        assertNotNull(new TdAdvancedReq());
        assertNotNull(new TdIntervalReq());
        assertNotNull(new TdGroupReq());
        assertNotNull(new TdTopNReq());
        assertNotNull(new TdInsertMultiBatchReq<>());
    }
}
