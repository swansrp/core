package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.document.spi.AsyncTranscribeLeg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 产物落盘默认实现单测（离线，M17 棒 3 自业务 ParseArtifactServiceTest 原样移植）：
 * 钉住"图片改写、页码映射格式、偏移坐标系"三条对下游的契约——A/B 判据（R-4 同输入逐字节一致）
 * 的单测载体。
 *
 * @author Sharp
 * @since 2026-10-21
 */
class DefaultParseArtifactWriterTest {

    private static final String SHA = "a1b2c3";

    private final BlobStore storage = mock(BlobStore.class);

    private final DefaultParseArtifactWriter service = new DefaultParseArtifactWriter(storage);

    @Test
    void rewritesOnlyReferencedImagesAndKeepsAltAndTitle() {
        String markdown = "# 报告\n\n![图一](images/one.png)\n\n![带标题](images/two.png \"图二\")\n\n![没落库](images/missing.png)\n";
        String rewritten = DefaultParseArtifactWriter.rewriteImages(markdown,
                Collections.singletonMap("images/one.png", "http://minio/one?sig=1"));

        assertTrue(rewritten.contains("![图一](http://minio/one?sig=1)"), "命中映射的引用应换 URL 且保留 alt");
        assertTrue(rewritten.contains("![带标题](images/two.png \"图二\")"), "未入库的图片保持原样（不写坏链接）");
        assertTrue(rewritten.contains("![没落库](images/missing.png)"));
        assertFalse(rewritten.contains("](images/one.png)"), "旧相对路径不应残留");
    }

    @Test
    void unusedImageMappingDoesNotBreakMarkdown() {
        String markdown = "正文没有图片";
        assertEquals(markdown, DefaultParseArtifactWriter.rewriteImages(markdown, Collections.emptyMap()));
    }

    @Test
    void pageMapIsOrderedAndCarriesOffsetsForLocating() throws Exception {
        PageBlock second = block("text", "第二段正文", 1, 100, new double[]{1, 2, 3, 4});
        second.setTextLevel(2);
        PageBlock first = block("text", "第一段正文", 0, 10, null);
        PageBlock blank = block("text", "   ", 0, 50, null);
        PageBlock unlocated = block("text", "找不到的块", 0, -1, null);

        String json = service.pageMapJson(Arrays.asList(second, blank, first, unlocated));
        JsonNode root = new ObjectMapper().readTree(json);

        assertEquals(2, root.get("pageCount").asInt(), "页数取最大 pageIdx + 1");
        assertEquals(2, root.get("blocks").size(), "空文本块与未定位块应被丢弃");
        JsonNode firstBlock = root.get("blocks").get(0);
        assertEquals(10, firstBlock.get("start").asInt());
        assertEquals(15, firstBlock.get("end").asInt(), "end = start + 块文本长度（文本可由 md 反查）");
        assertEquals(0, firstBlock.get("pageIdx").asInt());
        assertFalse(firstBlock.has("bbox"), "无 bbox 时不写该字段");
        assertFalse(firstBlock.has("textLevel"), "非标题块不写 textLevel");
        JsonNode secondBlock = root.get("blocks").get(1);
        assertEquals(1, secondBlock.get("pageIdx").asInt());
        assertEquals(4, secondBlock.get("bbox").size(), "bbox 为 [x0,y0,x1,y1]");
        assertEquals(2, secondBlock.get("textLevel").asInt(), "textLevel 须进 pagemap（章节切分输入）");
    }

    /**
     * 🔴 偏移坐标系缺陷回归（2026-09-20 发现）：pagemap 必须按<b>改写后</b>（占位替换）
     * 的正文算偏移——引擎检索用的就是改写后的那份。
     */
    @Test
    void pageMapOffsetsFollowRewrittenMarkdown() {
        String original = "![图](images/one.jpg)\n\n图片后的正文段落。";
        AsyncTranscribeLeg.Artifact artifact = artifact(original,
                "[{\"type\":\"image\",\"img_path\":\"images/one.jpg\",\"page_idx\":0},"
                        + "{\"type\":\"text\",\"text\":\"图片后的正文段落。\",\"page_idx\":0}]",
                Collections.singletonList(image("images/one.jpg", "JPEG".getBytes(StandardCharsets.UTF_8))));

        ParseArtifactWriter.Written written = service.transcribe(SHA, 1, artifact);

        byte[] pageMap = storageJson(written.getPageMapKey());
        String rewritten = new String(storageJson(written.getMarkdownKey()), StandardCharsets.UTF_8);
        assertTrue(rewritten.contains(BlobStore.IMAGE_PLACEHOLDER_SCHEME), "占位已替换");
        JsonNode root = null;
        try {
            root = new ObjectMapper().readTree(pageMap);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        JsonNode textBlock = root.get("blocks").get(1);
        assertEquals(rewritten.indexOf("图片后的正文段落"), textBlock.get("start").asInt(),
                "正文块 start 必须等于它在改写后正文里的真实位置（占位比相对路径长，原偏移必漂移）");
    }

    /** 从 mock 的 storage 里取回指定 key 的字节（落库走 put 捕获） */
    private byte[] storageJson(String key) {
        org.mockito.ArgumentCaptor<byte[]> data = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storage, atLeastOnce()).put(eq(key), data.capture(), anyString());
        return data.getValue();
    }

    @Test
    void storePersistsImagesMarkdownAndPageMap() {
        AsyncTranscribeLeg.Artifact artifact = artifact("![图](images/one.png)\n\n内容",
                "[{\"type\":\"text\",\"text\":\"内容\",\"page_idx\":2}]",
                Collections.singletonList(image("images/one.png", "PNG".getBytes(StandardCharsets.UTF_8))));

        ParseArtifactWriter.Written written = service.transcribe(SHA, 1, artifact);

        assertEquals("parsed/" + SHA + "/v1.md", written.getMarkdownKey());
        assertEquals("pagemap/" + SHA + "/v1.json", written.getPageMapKey());
        assertEquals(1, written.getImageCount());
        assertEquals(1, written.getBlockCount());
        assertEquals(3, written.getPageCount());
        String markdown = new String(storageJson(written.getMarkdownKey()), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("![图](" + BlobStore.IMAGE_PLACEHOLDER_SCHEME + SHA + "/one.png)"),
                "入库正文里的图片须是稳定占位");
        assertFalse(markdown.contains("http"), "正文里不应残留任何 http(s) 链接");

        verify(storage).put(eq("image/" + SHA + "/one.png"), any(), eq("image/png"));
        verify(storage).put(eq(written.getMarkdownKey()), any(), eq("text/markdown; charset=utf-8"));
        verify(storage).put(eq(written.getPageMapKey()), any(), eq("application/json; charset=utf-8"));
        verify(storage, never()).presign(anyString());
    }

    @Test
    void builtinWritesMarkdownOnlyWithoutPageMap() {
        ParseArtifactWriter.Written written = service.builtin(SHA, 3, "# 直读正文");
        assertEquals("parsed/" + SHA + "/v3.md", written.getMarkdownKey());
        assertEquals(null, written.getPageMapKey(), "内置腿无页码映射＝引用降级无页");
        verify(storage).put(eq(written.getMarkdownKey()), any(), eq("text/markdown; charset=utf-8"));
    }

    private static PageBlock block(String type, String text, int pageIdx, int start, double[] bbox) {
        PageBlock block = new PageBlock();
        block.setType(type);
        block.setText(text);
        block.setPageIdx(pageIdx);
        block.setStart(start);
        block.setBbox(bbox);
        return block;
    }

    /** 异步腿产物假件：markdown＋content_list 原文＋图片字节 */
    private static AsyncTranscribeLeg.Artifact artifact(String markdown, String contentListJson,
                                                        java.util.List<AsyncTranscribeLeg.Image> images) {
        return new AsyncTranscribeLeg.Artifact(markdown, contentListJson, images);
    }

    private static AsyncTranscribeLeg.Image image(String name, byte[] data) {
        return new AsyncTranscribeLeg.Image(name, data);
    }
}
