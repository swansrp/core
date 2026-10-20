package com.bidr.document;

import com.bidr.document.spi.TranscribeStep;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三态语义判据（立项 :205 判据③，§3.1 正源）：
 * <ol>
 *   <li>什么都没配→内置腿能出 md，<b>不抛</b>；</li>
 *   <li>配了 {@code mineru} 但包没引→WARN「选型指向不存在的腿，已走内置」继续跑；</li>
 *   <li>配了且在→增强腿；失败回落内置（门面回落、实现方别吞错——回落痕迹进 RouteTrace）。</li>
 * </ol>
 *
 * @author Sharp
 * @since 2026/10/20
 */
class DocumentThreeStateTest {

    /** 可控桩腿：认领一切，可指定腿名/返回文本/抛错 */
    private static class StubLeg implements TranscribeStep {

        final String name;

        final String result;

        final IOException failure;

        StubLeg(String result, IOException failure) {
            this("stub", result, failure);
        }

        StubLeg(String name, String result, IOException failure) {
            this.name = name;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public String legName() {
            return name;
        }

        @Override
        public boolean isAvailable(String filename) {
            return true;
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void 态一_什么都没配_内置能出md且不抛() throws IOException {
        TranscribeStep selected = TranscribeLegSelector.select(Collections.<TranscribeStep>emptyList(), "");
        assertNull(selected, "键空＋无腿＝不选增强腿");

        DocumentProcessor processor = DocumentProcessor.defaults(null);
        DocumentArtifact artifact = processor.process(utf8("你好 plain"), "x.txt", null);
        assertEquals("你好 plain", artifact.getMarkdown());
        assertEquals(RouteTrace.BUILTIN, artifact.getTrace().getTranscribeLeg(), "走内置直读");
        assertTrue(!artifact.getTrace().isDegradedNoPage(), "文本类无降级");
    }

    @Test
    void 态二_配了但包没引_WARN后走内置() throws IOException {
        Logger selectorLogger = (Logger) LoggerFactory.getLogger(TranscribeLegSelector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        selectorLogger.addAppender(appender);
        try {
            TranscribeStep selected = TranscribeLegSelector.select(
                    Collections.<TranscribeStep>emptyList(), "mineru");
            assertNull(selected, "选型指向不存在的腿＝不选（走内置）");
            boolean warned = appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("选型指向不存在的腿，已走内置"));
            assertTrue(warned, "必须 WARN 一条后继续跑，不是抛: " + appender.list);
        } finally {
            appender.stop();
            selectorLogger.detachAppender(appender);
        }

        DocumentProcessor processor = DocumentProcessor.defaults(null);
        assertEquals("你好 plain", processor.process(utf8("你好 plain"), "x.txt", null).getMarkdown());
    }

    @Test
    void 态三_配了且在_增强腿失败回落内置() throws IOException {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(), null,
                new StubLeg(null, new IOException("外部服务挂了")), null, null, null, null);
        DocumentArtifact artifact = processor.process(utf8("你好 plain"), "x.txt", null);

        assertEquals("你好 plain", artifact.getMarkdown(), "回落内置＝解析不挂");
        assertEquals(RouteTrace.BUILTIN, artifact.getTrace().getTranscribeLeg());
        assertTrue(artifact.getTrace().isTranscribeFallback(), "回落痕迹要显式（不许伪装成直接走内置）");
        assertTrue(artifact.getTrace().getTranscribeFallbackReason().contains("外部服务挂了"));
    }

    @Test
    void 态三_增强腿返回空也回落内置() throws IOException {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(), null,
                new StubLeg("   ", null), null, null, null, null);
        DocumentArtifact artifact = processor.process(utf8("你好 plain"), "x.txt", null);
        assertEquals("你好 plain", artifact.getMarkdown());
        assertTrue(artifact.getTrace().isTranscribeFallback());
    }

    @Test
    void 态三_增强腿成功则走增强() throws IOException {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(), null,
                new StubLeg("FROM_ENHANCED", null), null, null, null, null);
        DocumentArtifact artifact = processor.process(utf8("fake"), "a.pdf", null);
        assertEquals("FROM_ENHANCED", artifact.getMarkdown());
        assertEquals("stub", artifact.getTrace().getTranscribeLeg());
    }

    @Test
    void 选型键按腿名匹配() {
        TranscribeStep a = new StubLeg("leg-a", "a", null);
        TranscribeStep b = new StubLeg("leg-b", "b", null);
        // 两条自装腿＋键选 b：必须选中 b（List 收口不炸，按名选）
        assertEquals("leg-b", rename(TranscribeLegSelector.select(Arrays.asList(a, b), "leg-b")));
        assertEquals("leg-a", rename(TranscribeLegSelector.select(Arrays.asList(a, b), "leg-a")));
    }

    private String rename(TranscribeStep leg) {
        return leg == null ? null : leg.legName();
    }
}
