package com.bidr.document;

import com.bidr.document.spi.TranscribeStep;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: DocumentProcessorTranscribeFallbackTest
 * Description: 门面路由判据（增强转写腿前置，原 llm 侧同名判据随入口搬家并进门面这一层）：
 * 认领的文件先走腿；腿抛错/空结果 WARN 后回落内置分派（回落段<b>不再过同一条腿</b>＝咨询次数 1），
 * 回落证据用「图片腿→多模态且无视觉配置必抛带指引的错」这条报错坐实；
 * 不认领的文件直走内置分发。产物契约的路线留痕同步判 transcribeLeg/transcribeFallback
 * （无腿档与 convert 槽两半由 {@link RouteTraceTwoLaneTest} 钉，本类不重抄）。
 *
 * @author Sharp
 * @since 2026/10/21
 */
class DocumentProcessorTranscribeFallbackTest {

    /** 可控桩腿：只认 .png，可指定返回文本或抛错，并计咨询次数 */
    private static class StubLeg implements TranscribeStep {

        final String result;

        final IOException failure;

        final AtomicInteger consulted = new AtomicInteger();

        StubLeg(String result, IOException failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public String legName() {
            return "stub";
        }

        @Override
        public boolean isAvailable(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".png");
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            consulted.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static DocumentProcessor with(TranscribeStep leg) {
        return new DocumentProcessor(null, null, leg, null, null, null, null);
    }

    private static byte[] png() {
        return "fake-image".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void 认领的文件先走增强腿并留痕腿名() throws IOException {
        StubLeg leg = new StubLeg("FROM_LEG", null);
        DocumentArtifact artifact = with(leg).process(png(), "x.png", null);
        assertEquals("FROM_LEG", artifact.getMarkdown(), "腿认领的文件必须先走腿");
        assertEquals(1, leg.consulted.get());
        assertEquals("stub", artifact.getTrace().getTranscribeLeg());
        assertFalse(artifact.getTrace().isTranscribeFallback(), "腿成功时不得记回落");
    }

    @Test
    void 腿抛错回落内置且同一条腿不打两遍() throws IOException {
        StubLeg leg = new StubLeg(null, new IOException("外部服务挂了"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> with(leg).process(png(), "x.png", null));
        assertTrue(e.getMessage().contains("多模态"), "须是多模态未配置的指引（证明回落到了内置图片路径）");
        assertEquals(1, leg.consulted.get(), "回落段不得把同一条腿再打一遍");
    }

    @Test
    void 腿返回空回落内置并留痕回落原因() throws IOException {
        StubLeg leg = new StubLeg("   ", null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> with(leg).process(png(), "x.png", null));
        assertTrue(e.getMessage().contains("多模态"));
        assertEquals(1, leg.consulted.get());
    }

    @Test
    void 显式跳过档_外层已咨询过同引擎时直走内置() throws IOException {
        StubLeg leg = new StubLeg("FROM_LEG", null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> with(leg).process(png(), "x.png", null, true));
        assertTrue(e.getMessage().contains("多模态"), "跳过档必须直达内置图片路径");
        assertEquals(0, leg.consulted.get(), "第四参＝true 时增强腿一次都不咨询");
    }

    @Test
    void 不认领的文件直走内置分发() throws IOException {
        StubLeg leg = new StubLeg("FROM_LEG", null);
        DocumentArtifact artifact = with(leg).process("你好 plain".getBytes(StandardCharsets.UTF_8), "x.txt", null);
        assertEquals("你好 plain", artifact.getMarkdown(), "文本类不认领＝直走文本解码");
        assertEquals(0, leg.consulted.get());
        assertEquals(RouteTrace.BUILTIN, artifact.getTrace().getTranscribeLeg());
    }
}
