package com.bidr.agent.capability.file;

import com.bidr.blob.BlobStore;
import com.bidr.llm.agent.file.FileRef;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: BlobStoreAgentFileStoreTest
 * Description: 对象存储附件实现的契约钉桩（JUnit5）：
 * <ul>
 *     <li>m-r2-2：元数据侧车写失败必须回滚内容对象并抛错（上传端点不得 200 后模型读不到）；</li>
 *     <li>m-r2-6：fileId 字符集契约与 metaKey 的 safeSegment 防御；</li>
 *     <li>2026-11-17 分家：附件键挂 {@code temp/agent-session/}（吃 ILM）、产物键落
 *         {@code agent-artifact/}（耐用不过期）、get 三前缀回查（含旧版 agent-session/ 存量）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
class BlobStoreAgentFileStoreTest {

    /** 正常链：内容对象 + 侧车对象两次写入，返回引用 fileId 满足端口字符集 */
    @Test
    void putWritesContentAndSidecarAndReturnsContractFileId() {
        BlobStore blobStore = mock(BlobStore.class);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        FileRef ref = store.put("s-1", "监理日志.docx", "text/plain", "hi".getBytes(StandardCharsets.UTF_8));

        assertTrue(ref.fileId().matches("[A-Za-z0-9_-]{1,128}"),
                "fileId 必须满足 [A-Za-z0-9_-]{1,128}（read_attachment 入参闸）");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(blobStore, org.mockito.Mockito.times(2)).put(keys.capture(), any(byte[].class), anyString());
        List<String> written = keys.getAllValues();
        assertTrue(written.get(0).startsWith("temp/agent-session/"), "内容键挂 temp/ 前缀（吃桶级 ILM）");
        assertTrue(written.get(0).contains("/s-1/"), "内容键含 sessionId 段");
        assertEquals("temp/agent-session/s-1/" + ref.fileId() + ".meta.json", written.get(1),
                "侧车键 = temp/agent-session/{sessionId}/{fileId}.meta.json");
    }

    /** m-r2-2：侧车写失败 ⇒ 删除已写内容对象并抛人话错误（不留"上传成功但读不到"的孤儿） */
    @Test
    void sidecarFailureRollsBackContentAndThrows() {
        BlobStore blobStore = mock(BlobStore.class);
        AtomicInteger puts = new AtomicInteger();
        doAnswer(invocation -> {
            if (puts.incrementAndGet() == 2) {
                throw new IllegalStateException("sidecar storage down");
            }
            return null;
        }).when(blobStore).put(anyString(), any(byte[].class), anyString());
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> store.put("s-1", "a.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.getMessage().contains("附件元数据写入失败"), "错误文案须是人话且指明可重试");
        ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
        verify(blobStore).remove(removed.capture());
        assertTrue(removed.getValue().startsWith("temp/agent-session/"), "回滚的必须是内容对象");
        assertFalse(removed.getValue().contains(".meta.json"), "侧车键不应被当作内容对象删除");
    }

    /** m-r2-6：get 的 fileId 也过 safeSegment（防御任意调用方传路径片段） */
    @Test
    void metaKeySanitizesArbitraryFileId() {
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.get(anyString())).thenReturn(null);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        store.get("s-1", "../evil");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(blobStore, times(3)).get(key.capture());
        assertEquals("temp/agent-session/s-1/.._evil.meta.json", key.getAllValues().get(0),
                "首个回查前缀＝现行附件侧车（temp/agent-session）");
        assertNotNull(store);
    }

    /** 2026-11-17 分家：产物键落 agent-artifact/（不过期），侧车同前缀 */
    @Test
    void putArtifactWritesDurableNamespaceWithSidecar() {
        BlobStore blobStore = mock(BlobStore.class);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        FileRef ref = store.putArtifact("s-9", "交付件.docx", null,
                "doc".getBytes(StandardCharsets.UTF_8));

        assertTrue(ref.fileId().matches("[A-Za-z0-9_-]{1,128}"));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(blobStore, times(2)).put(keys.capture(), any(byte[].class), anyString());
        List<String> written = keys.getAllValues();
        assertEquals("agent-artifact/s-9/" + ref.fileId() + ".docx", written.get(0),
                "产物内容键 = agent-artifact/{sessionId}/{fileId}.{ext}（不带归属段）");
        assertEquals("agent-artifact/s-9/" + ref.fileId() + ".meta.json", written.get(1),
                "产物侧车键与内容同前缀（file_url 现签的 get 回查依据）");
    }

    /** get 三前缀回查：附件（temp）缺席 → 产物（agent-artifact）命中 */
    @Test
    void getFallsThroughTempPrefixToArtifactSidecar() {
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.get(anyString())).thenReturn(null);
        FileRef artifactRef = new FileRef("f-a1", "交付件.docx", 4, "application/octet-stream",
                "agent-artifact/s-9/f-a1.docx");
        when(blobStore.get("agent-artifact/s-9/f-a1.meta.json"))
                .thenReturn(("{\"fileId\":\"f-a1\",\"name\":\"交付件.docx\",\"size\":4,"
                        + "\"mime\":\"application/octet-stream\",\"key\":\"agent-artifact/s-9/f-a1.docx\"}")
                        .getBytes(StandardCharsets.UTF_8));
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        FileRef ref = store.get("s-9", "f-a1");

        assertNotNull(ref, "产物 fileId 应能经 get 回查（file_url 现签链路）");
        assertEquals("agent-artifact/s-9/f-a1.docx", ref.key());
    }

    /** get 三前缀回查：旧版 agent-session/ 存量侧车（2026-11-17 前落盘）仍可解析 */
    @Test
    void getResolvesLegacyAgentSessionSidecar() {
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.get(anyString())).thenReturn(null);
        when(blobStore.get("agent-session/s-old/f-l1.meta.json"))
                .thenReturn(("{\"fileId\":\"f-l1\",\"name\":\"旧附件.txt\",\"size\":2,"
                        + "\"mime\":\"text/plain\",\"key\":\"agent-session/1/s-old/f-l1.txt\"}")
                        .getBytes(StandardCharsets.UTF_8));
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        FileRef ref = store.get("s-old", "f-l1");

        assertNotNull(ref, "旧版前缀存量键不因切换而断读");
        assertEquals("agent-session/1/s-old/f-l1.txt", ref.key());
    }

    /** delete 由内容键推侧车：产物键（agent-artifact/{sid}/{fileId}.ext）删对应产物侧车 */
    @Test
    void deleteDerivesArtifactSidecarFromContentKey() {
        BlobStore blobStore = mock(BlobStore.class);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        store.delete(new FileRef("f-a2", "x.pdf", 3, "application/pdf", "agent-artifact/s-2/f-a2.pdf"));

        ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
        verify(blobStore, times(2)).remove(removed.capture());
        assertEquals("agent-artifact/s-2/f-a2.pdf", removed.getAllValues().get(0));
        assertEquals("agent-artifact/s-2/f-a2.meta.json", removed.getAllValues().get(1));
    }

    /** delete 由内容键推侧车：附件键（temp/agent-session/{owner}/{sid}/{fileId}.ext）去归属段 */
    @Test
    void deleteDerivesTempAttachmentSidecarFromContentKey() {
        BlobStore blobStore = mock(BlobStore.class);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        store.delete(new FileRef("f-t1", "y.txt", 2, "text/plain", "temp/agent-session/7/s-3/f-t1.txt"));

        ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
        verify(blobStore, times(2)).remove(removed.capture());
        assertEquals("temp/agent-session/7/s-3/f-t1.txt", removed.getAllValues().get(0));
        assertEquals("temp/agent-session/s-3/f-t1.meta.json", removed.getAllValues().get(1),
                "侧车键去归属段（删除路径不依赖线程上下文）");
    }

    /** 旧版内容键（agent-session/{owner}/{sid}/{fileId}.ext）删除时推旧版侧车 */
    @Test
    void deleteDerivesLegacySidecarFromLegacyContentKey() {
        BlobStore blobStore = mock(BlobStore.class);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        store.delete(new FileRef("f-l2", "z.txt", 1, "text/plain", "agent-session/2/s-4/f-l2.txt"));

        ArgumentCaptor<String> removed = ArgumentCaptor.forClass(String.class);
        verify(blobStore, times(2)).remove(removed.capture());
        assertEquals("agent-session/s-4/f-l2.meta.json", removed.getAllValues().get(1));
    }

    /** 三前缀全缺席 → null（清单端点按没有该附件处理） */
    @Test
    void getReturnsNullWhenAllPrefixesMiss() {
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.get(anyString())).thenReturn(null);
        BlobStoreAgentFileStore store = new BlobStoreAgentFileStore(blobStore);

        assertNull(store.get("s-none", "f-none"));
    }
}
