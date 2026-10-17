package com.bidr.oss.service;

import com.bidr.kernel.constant.param.Param;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: OssServiceSelectorTest
 * Description: 选型器（sys_config {@code OSS_SERVER_TYPE} → {@link BaseOssService}）的映射腿：
 * 枚举里的 {@code service} 字段由 Spring 装配器注入，单测无容器时用反射替换成哨兵实现，
 * 测后还原——钉住"配置值决定实现"与"值缺失即拒（不静默回落）"两条语义。
 *
 * @author Sharp
 * @since 2026/10/15
 */
class OssServiceSelectorTest {

    private final SysConfigCacheService cacheService = mock(SysConfigCacheService.class);

    private final OssServiceSelector selector = new OssServiceSelector();

    private Object minioOriginal;

    private Object volcanoOriginal;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(selector, "sysConfigCacheService", cacheService);
        minioOriginal = enumService(OssServiceTypeDict.MINIO);
        volcanoOriginal = enumService(OssServiceTypeDict.VOLCANO);
        setEnumService(OssServiceTypeDict.MINIO, mock(BaseOssService.class, "minio-sentinel"));
        setEnumService(OssServiceTypeDict.VOLCANO, mock(BaseOssService.class, "volcano-sentinel"));
    }

    @AfterEach
    void restore() throws Exception {
        setEnumService(OssServiceTypeDict.MINIO, minioOriginal);
        setEnumService(OssServiceTypeDict.VOLCANO, volcanoOriginal);
    }

    @Test
    @DisplayName("配置值 1/3 分别命中 MinIO 与火山实现——配置决定实现，不硬编码某一家")
    void selectsByConfiguredType() {
        when(cacheService.getParamValueAvail(any(Param.class))).thenReturn("1");
        assertSame(OssServiceTypeDict.MINIO.getService(), selector.select(), "type=1 必须选 MinIO 实现");

        when(cacheService.getParamValueAvail(any(Param.class))).thenReturn("3");
        assertSame(OssServiceTypeDict.VOLCANO.getService(), selector.select(), "type=3 必须选火山实现");
    }

    @Test
    @DisplayName("值缺失/非法即抛——装机必配项出错要当场炸，不静默回落")
    void rejectsMissingOrUnknownValue() {
        when(cacheService.getParamValueAvail(any(Param.class))).thenReturn(null);
        assertThrows(Exception.class, selector::select, "值缺失必须抛，不得回落");

        when(cacheService.getParamValueAvail(any(Param.class))).thenReturn("9");
        assertThrows(Exception.class, selector::select, "未知值必须抛，不得回落");
    }

    private static Object enumService(OssServiceTypeDict item) throws Exception {
        Field field = OssServiceTypeDict.class.getDeclaredField("service");
        field.setAccessible(true);
        return field.get(item);
    }

    private static void setEnumService(OssServiceTypeDict item, Object service) throws Exception {
        Field field = OssServiceTypeDict.class.getDeclaredField("service");
        field.setAccessible(true);
        field.set(item, service);
    }
}
