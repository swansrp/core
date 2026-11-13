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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: BlobStoreAgentFileStoreTest
 * Description: 对象存储附件实现的契约钉桩（JUnit5）：
 * <ul>
 *     <li>m-r2-2：元数据侧车写失败必须回滚内容对象并抛错（上传端点不得 200 后模型读不到）；</li>
 *     <li>m-r2-6：fileId 字符集契约与 metaKey 的 safeSegment 防御。</li>
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
        assertTrue(written.get(0).startsWith("agent-session/"), "内容键带会话前缀");
        assertTrue(written.get(0).contains("/s-1/"), "内容键含 sessionId 段");
        assertEquals("agent-session/s-1/" + ref.fileId() + ".meta.json", written.get(1),
                "侧车键 = agent-session/{sessionId}/{fileId}.meta.json");
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
        assertTrue(removed.getValue().startsWith("agent-session/"), "回滚的必须是内容对象");
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
        verify(blobStore).get(key.capture());
        assertEquals("agent-session/s-1/.._evil.meta.json", key.getValue());
        assertNotNull(store);
    }
}