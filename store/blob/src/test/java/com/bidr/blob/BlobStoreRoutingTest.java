package com.bidr.blob;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.service.OssServiceSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对象存储门面的选型路由腿（自 inkhub-file 的 BlobStorageServiceRoutingTest 原样迁入；
 * 2026-10-15 zz 事故的守卫）：草稿保存整腿曾直连 {@code MinioTemplate}，TOS 底座下
 * 即使 sys_config 改成火山也救不回——本测试钉住门面只经 {@link OssServiceSelector}
 * 取实现、自身不认厂商的接线。配置值→实现的映射腿在框架侧 {@code OssServiceSelectorTest}。
 *
 * @author sharp
 */
class BlobStoreRoutingTest {

    private final OssServiceSelector selector = mock(OssServiceSelector.class);

    private final BaseOssService storage = mock(BaseOssService.class);

    private final BlobStore facade = new BlobStore(selector);

    @BeforeEach
    void setUp() {
        when(selector.select()).thenReturn(storage);
        ReflectionTestUtils.setField(facade, "bucket", "test-bucket");
        ReflectionTestUtils.setField(facade, "appKey", "ak");
        ReflectionTestUtils.setField(facade, "appSecret", "sk");
        ReflectionTestUtils.setField(facade, "endpoint", "http://oss.example");
    }

    @Test
    @DisplayName("put/presign/get/remove 四动作全部委托给当前选型实现，自带 MIME 推断")
    void delegatesAllActionsToSelectedStorage() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

        facade.put("raw/abc.pdf", payload, null);
        verify(storage).putBytes("raw/abc.pdf", payload, "application/pdf");

        when(storage.buildAccessUrl("raw/abc.pdf")).thenReturn("http://oss.example/test-bucket/raw/abc.pdf");
        when(storage.getReadUrl("http://oss.example/test-bucket/raw/abc.pdf")).thenReturn("http://signed");
        assertEquals("http://signed", facade.presign("raw/abc.pdf"));
        verify(storage).buildAccessUrl("raw/abc.pdf");
        verify(storage).getReadUrl("http://oss.example/test-bucket/raw/abc.pdf");

        facade.get("raw/abc.pdf");
        verify(storage).readBytes("raw/abc.pdf");

        facade.remove("raw/abc.pdf");
        verify(storage).deleteObject("http://oss.example/test-bucket/raw/abc.pdf");
    }

    @Test
    @DisplayName("显式 contentType 优先于扩展名推断")
    void explicitContentTypeWins() {
        byte[] payload = {1};
        facade.put("raw/abc.bin", payload, "application/x-custom");
        verify(storage).putBytes("raw/abc.bin", payload, "application/x-custom");
    }

    @Test
    @DisplayName("remove 吞实现侧异常只告警——清理失败不回滚调用方事务（既有契约）")
    void removeSwallowsStorageFailure() {
        doThrow(new IllegalStateException("tos down"))
                .when(storage).deleteObject(anyString());
        facade.remove("raw/abc.pdf");
    }

    @Test
    @DisplayName("凭据缺失即拒（NoticeException），不落到实现层再炸")
    void rejectsWhenCredentialsMissing() {
        ReflectionTestUtils.setField(facade, "appKey", "");
        assertThrows(NoticeException.class, () -> facade.put("raw/abc.pdf", new byte[0], null));
        assertThrows(NoticeException.class, () -> facade.presign("raw/abc.pdf"));
        assertThrows(NoticeException.class, () -> facade.get("raw/abc.pdf"));
        assertThrows(NoticeException.class, () -> facade.remove("raw/abc.pdf"));
    }
}
