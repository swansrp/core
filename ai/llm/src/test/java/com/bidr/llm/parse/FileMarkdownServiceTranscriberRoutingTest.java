package com.bidr.llm.parse;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Title: FileMarkdownServiceTranscriberRoutingTest
 * Description: 门面路由判据（外部转录腿前置）：认领的文件先走外部腿；腿失败/空结果回落
 * 既有本地路径（图片腿回落多模态解析＝无视觉配置时抛带指引的 IllegalStateException，
 * 用这条报错证明回落真的发生）；不认领的文件直走既有分发（文本直读）。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class FileMarkdownServiceTranscriberRoutingTest {

    /** 可控桩腿：只认 .png，可指定返回文本或抛错 */
    private static class StubTranscriber implements DocumentTranscriber {

        final String result;

        final IOException failure;

        StubTranscriber(String result, IOException failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public boolean isAvailable(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".png");
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    @Test
    public void 认领的文件先走外部腿() throws IOException {
        FileMarkdownService service = new FileMarkdownService(null, null,
                new StubTranscriber("FROM_EXTERNAL", null));
        String markdown = service.toMarkdown(
                new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8)), "x.png");
        Assert.assertEquals("外部腿认领的文件必须先走它", "FROM_EXTERNAL", markdown);
    }

    @Test
    public void 腿抛错回落既有路径() {
        FileMarkdownService service = new FileMarkdownService(null, null,
                new StubTranscriber(null, new IOException("外部服务挂了")));
        try {
            service.toMarkdown(
                    new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8)), "x.png");
            Assert.fail("回落到多模态路径且无视觉配置＝必须抛带指引的错");
        } catch (IllegalStateException expected) {
            // 回落多模态＝resolveVisionConfig 的 IllegalStateException（无配置即抛，证明回落真的发生）
            Assert.assertTrue("须是多模态未配置的指引（证明回落到既有路径）",
                    expected.getMessage().contains("多模态"));
        } catch (IOException e) {
            Assert.fail("回落前的 IO 异常不许穿出（门面负责回落）: " + e.getMessage());
        }
    }

    @Test
    public void 腿返回空回落既有路径() {
        FileMarkdownService service = new FileMarkdownService(null, null,
                new StubTranscriber("   ", null));
        try {
            service.toMarkdown(
                    new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8)), "x.png");
            Assert.fail("空结果必须回落既有路径（同上腿）");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("多模态"));
        } catch (IOException e) {
            Assert.fail("空结果回落失败: " + e.getMessage());
        }
    }

    @Test
    public void 不认领的文件直走既有分发() throws IOException {
        FileMarkdownService service = new FileMarkdownService(null, null,
                new StubTranscriber("FROM_EXTERNAL", null));
        String markdown = service.toMarkdown(
                new ByteArrayInputStream("你好 plain".getBytes(StandardCharsets.UTF_8)), "x.txt");
        Assert.assertEquals("文本类不认领＝直走文本解码", "你好 plain", markdown);
    }

    @Test
    public void 无腿构造器行为不变() throws IOException {
        // 旧构造器（两参）仍存在且行为与历史一致——兼容契约的正对照
        FileMarkdownService service = new FileMarkdownService(null, null);
        String markdown = service.toMarkdown(
                new ByteArrayInputStream("legacy".getBytes(StandardCharsets.UTF_8)), "x.txt");
        Assert.assertEquals("legacy", markdown);
    }
}
