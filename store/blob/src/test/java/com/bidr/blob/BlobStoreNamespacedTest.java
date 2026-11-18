package com.bidr.blob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Title: BlobStoreNamespacedTest
 * Description: 命名空间包装（2026-11-17 temp 前缀定案）契约钉桩：空＝键逐字节不变（KB/正式链路
 * 零回归的判据锚点）；非空＝trim 后以 "/" 相接。键形本体由 {@link BlobKeyGoldenTest} 钉住，
 * 这里只钉包装行为。
 *
 * @author Sharp
 * @since 2026/11/17
 */
class BlobStoreNamespacedTest {

    @Test
    void nullOrBlankNamespaceReturnsKeyVerbatim() {
        String key = "parsed/a1b2/v1.md";
        assertEquals(key, BlobStore.namespaced(null, key), "null namespace＝原键（逐字节不变）");
        assertEquals(key, BlobStore.namespaced("", key), "空串＝原键");
        assertEquals(key, BlobStore.namespaced("   ", key), "纯空白＝原键");
    }

    @Test
    void namespaceIsTrimmedAndSlashJoined() {
        assertEquals("temp/material/raw/a1b2.pdf",
                BlobStore.namespaced("temp/material", BlobStore.rawKey("a1b2", "pdf")));
        assertEquals("temp/material/raw/a1b2.pdf",
                BlobStore.namespaced(" temp/material ", BlobStore.rawKey("a1b2", "pdf")),
                "首尾空白 trim 后相接");
    }

    @Test
    void composesWithEveryBaseKeyRule() {
        assertEquals("ns/parsed/s/v0.md", BlobStore.namespaced("ns", BlobStore.parsedMarkdownKey("s", 0)));
        assertEquals("ns/pagemap/s/v0.json", BlobStore.namespaced("ns", BlobStore.pageMapKey("s", 0)));
        assertEquals("ns/convert/s.pdf", BlobStore.namespaced("ns", BlobStore.convertedPdfKey("s")));
    }
}
