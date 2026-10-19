package com.bidr.oss.service;

import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.platform.dao.entity.SysConfig;
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
 * Description: 选型器（系统参数 {@code OSS_SERVER_TYPE} 优先 → 配置文件 {@code oss.server-type} 兜底
 * → {@link BaseOssService}）的取值链腿：枚举里的 {@code service} 字段由 Spring 装配器注入，
 * 单测无容器时用反射替换成哨兵实现，测后还原——钉住三条语义：
 * <b>库值压过 yml</b>（运维在参数页表过态就以库为准，与改造前逐字一致）、
 * <b>库值空/参数行缺失才轮到 yml</b>（新装现场不改库也能定底座）、
 * <b>两头都空或值非法即拒</b>（装机必配项出错要当场炸，不静默回落某一家）。
 *
 * @author Sharp
 * @since 2026-10-15
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
    @DisplayName("库值 1/3 分别命中 MinIO 与火山实现——配置决定实现，不硬编码某一家")
    void selectsByConfiguredType() {
        dbValue("1");
        assertSame(OssServiceTypeDict.MINIO.getService(), selector.select(), "库值=1 必须选 MinIO 实现");

        dbValue("3");
        assertSame(OssServiceTypeDict.VOLCANO.getService(), selector.select(), "库值=3 必须选火山实现");
    }

    @Test
    @DisplayName("库值优先：参数页填了 1、配置文件写 3 ⇒ 仍走 MinIO（运维表态压过 yml，改造前后行为一致）")
    void dbValueWinsOverYml() {
        dbValue("1");
        ymlValue("3");
        assertSame(OssServiceTypeDict.MINIO.getService(), selector.select(),
                "库里非空时 yml 不得顶掉库值——否则参数页改选型失效，比改造前更坏");
    }

    @Test
    @DisplayName("库值为空或参数行根本不存在 ⇒ 回落 oss.server-type（换现场只改配置文件即可）")
    void fallsBackToYmlWhenDbUndeclared() {
        ymlValue("3");

        dbValue("");
        assertSame(OssServiceTypeDict.VOLCANO.getService(), selector.select(),
                "库值为空串＝未表态，必须轮到配置文件");

        when(cacheService.getCache(any())).thenReturn(null);
        assertSame(OssServiceTypeDict.VOLCANO.getService(), selector.select(),
                "参数行缺失（专有化形态没种过这一行）同样属未表态，不得因为少一行就报错");
    }

    @Test
    @DisplayName("两头都没给即抛——不静默假定某一家；库值非法同样抛")
    void rejectsUnresolvedOrUnknownValue() {
        ymlValue("");

        dbValue(null);
        assertThrows(Exception.class, selector::select, "库值与 yml 双空必须抛，不得回落");

        when(cacheService.getCache(any())).thenReturn(null);
        assertThrows(Exception.class, selector::select, "参数行缺失且 yml 未给也必须抛");

        dbValue("9");
        assertThrows(Exception.class, selector::select, "未知值必须抛，不得回落");
    }

    private void dbValue(String value) {
        SysConfig row = new SysConfig();
        row.setConfigKey("OSS_SERVER_TYPE");
        row.setConfigValue(value);
        when(cacheService.getCache(any())).thenReturn(value == null ? null : row);
    }

    private void ymlValue(String value) {
        ReflectionTestUtils.setField(selector, "ymlServerType", value);
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
