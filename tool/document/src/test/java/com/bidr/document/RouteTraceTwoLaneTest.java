package com.bidr.document;

import com.bidr.document.spi.ArtifactStep;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.EnrichStep;
import com.bidr.document.spi.TranscribeStep;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路线留痕两腿判据（立项 :205 判据⑤，§7.3 定形）：同一份 docx——
 * 「convert＋transcribe 两腿齐」出带页正文（degraded=false、留痕点名两腿）vs
 * 「都不装」出内置 POI 正文＋<b>显式无页降级标记</b>（CONVERT_FALLBACK 形状、degraded=true）。
 * 附：retryable 两半不许合并（分支边界 2）、视觉配置取不到抛带指引（边界 3）、enrich/artifact 槽。
 *
 * @author Sharp
 * @since 2026/10/20
 */
class RouteTraceTwoLaneTest {

    /** convert 桩腿：docx→假 PDF（魔数自证通过的最小字节） */
    private static class StubConvertLeg implements ConvertStep {

        @Override
        public boolean isConvertible(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".docx");
        }

        @Override
        public byte[] convertToPdf(String fileName, byte[] content) {
            return "%PDF-1.7 stub-converted".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String legName() {
            return "stub-convert";
        }
    }

    /** transcribe 桩腿：认领 pdf，回带页码的 md（模拟 MinerU 的分页产物） */
    private static class StubTranscribeLeg implements TranscribeStep {

        @Override
        public String legName() {
            return "stub-transcribe";
        }

        @Override
        public boolean isAvailable(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".pdf");
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            return "# 转录标题（第 1 页）\n\n带页码可引用的正文";
        }
    }

    /** 与现网 DocxMarkdownConverter 同输入同输出的 docx 样本（probe 定格的构造形态） */
    private static byte[] docxBytes() throws IOException {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph heading = doc.createParagraph();
        heading.setStyle("Heading1");
        heading.createRun().setText("文档标题");
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.createRun().setText("普通段落文字");
        XWPFTable table = doc.createTable(2, 2);
        table.getRow(0).getCell(0).setText("列一");
        table.getRow(0).getCell(1).setText("列二");
        table.getRow(1).getCell(0).setText("1");
        table.getRow(1).getCell(1).setText("2");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.write(bos);
        return bos.toByteArray();
    }

    private static final String DOCX_GOLDEN =
            "# 文档标题\n\n普通段落文字\n\n| 列一 | 列二 |\n| --- | --- |\n| 1 | 2 |";

    @Test
    void 两腿齐_出带页正文且留痕点名两腿() throws IOException {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(),
                Collections.<ConvertStep>singletonList(new StubConvertLeg()),
                new StubTranscribeLeg(), null, null, null, null);

        DocumentArtifact artifact = processor.process(docxBytes(), "报告.docx", null);

        assertEquals("# 转录标题（第 1 页）\n\n带页码可引用的正文", artifact.getMarkdown(),
                "docx 先被 convert 桩转成 PDF，再被 transcribe 桩认领（分页档）");
        RouteTrace trace = artifact.getTrace();
        assertEquals("stub-convert", trace.getConvertLeg());
        assertEquals("stub-transcribe", trace.getTranscribeLeg());
        assertFalse(trace.isDegradedNoPage(), "带版式转换成功＝引用不降级");
        assertFalse(Boolean.TRUE.equals(trace.getConvertFallback()));
    }

    @Test
    void 都不装_出内置正文且显式无页降级() throws IOException {
        DocumentProcessor processor = DocumentProcessor.defaults(null);

        DocumentArtifact artifact = processor.process(docxBytes(), "报告.docx", null);

        assertEquals(DOCX_GOLDEN, artifact.getMarkdown(), "内置 POI 直读＝正文仍有（无页无版面档）");
        RouteTrace trace = artifact.getTrace();
        assertEquals(null, trace.getConvertLeg(), "convert 腿未装配＝无转换发生");
        assertEquals(Boolean.TRUE, trace.getConvertFallback(), "office 输入＋convert 腿未装配＝记 CONVERT_FALLBACK");
        assertTrue(trace.getConvertFallbackReason().contains("未装配"));
        assertEquals(RouteTrace.BUILTIN, trace.getTranscribeLeg());
        assertTrue(trace.isDegradedNoPage(), "降级不许伪装正常：显式无页标记");
        assertTrue(trace.describe().contains("引用降级无页"));
    }

    @Test
    void 转不动_retryable_false_兜底原件直送() throws IOException {
        ConvertLegWithFailure leg = new ConvertLegWithFailure(new DocumentConvertException(false, "文档转不动"));
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(),
                Collections.<ConvertStep>singletonList(leg), null, null, null, null, null);

        DocumentArtifact artifact = processor.process(docxBytes(), "报告.docx", null);

        assertEquals(DOCX_GOLDEN, artifact.getMarkdown(), "转不动＝稳定状态，原件直送走内置");
        assertEquals(Boolean.TRUE, artifact.getTrace().getConvertFallback());
        assertTrue(artifact.getTrace().getConvertFallbackReason().contains("文档转不动"));
        assertTrue(artifact.getTrace().isDegradedNoPage());
    }

    @Test
    void 不可达5xx_retryable_true_必须上抛进重试预算() {
        ConvertLegWithFailure leg = new ConvertLegWithFailure(new DocumentConvertException(true, "服务不可达"));
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(),
                Collections.<ConvertStep>singletonList(leg), null, null, null, null, null);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> processor.process(docxBytes(), "报告.docx", null));
        assertTrue(e.isRetryable(), "不许静默兜底：把「暂时没页」固化成「永远没页」");
    }

    /** 可注入失败语义的 convert 桩 */
    private static class ConvertLegWithFailure implements ConvertStep {

        private final DocumentConvertException failure;

        ConvertLegWithFailure(DocumentConvertException failure) {
            this.failure = failure;
        }

        @Override
        public boolean isConvertible(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".docx");
        }

        @Override
        public byte[] convertToPdf(String fileName, byte[] content) {
            throw failure;
        }
    }

    @Test
    void 扫描页无视觉配置_抛带操作指引() throws IOException {
        // 空内容页＝文本量低于 20 字判扫描页 → 渲染后问视觉配置 → 取不到必须抛（边界 3）
        PDDocument pdf = new PDDocument();
        pdf.addPage(new PDPage());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdf.save(bos);

        DocumentProcessor processor = DocumentProcessor.defaults(null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> processor.process(bos.toByteArray(), "扫描件.pdf", null));
        assertTrue(e.getMessage().contains("多模态"), "报错须带多模态配置操作指引: " + e.getMessage());
    }

    @Test
    void enrich与artifact槽_按序生效() throws IOException {
        EnrichStep appender = (markdown, filename) -> markdown + "\n<!--enriched-->";
        CountingArtifactStep artifactStep = new CountingArtifactStep();
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(), null, null, null,
                Collections.<EnrichStep>singletonList(appender), artifactStep, null);

        DocumentArtifact artifact = processor.process("正文".getBytes(StandardCharsets.UTF_8), "x.txt", null);

        assertEquals("正文\n<!--enriched-->", artifact.getMarkdown());
        assertEquals(1, artifactStep.shaped, "产物落形槽生效且只过一次");
    }

    private static class CountingArtifactStep implements ArtifactStep {

        int shaped;

        @Override
        public DocumentArtifact shape(String markdown, RouteTrace trace) {
            shaped++;
            return new DocumentArtifact(markdown, trace);
        }
    }

    /**
     * B14 负腿：未知扩展名的<b>二进制</b>——能力查询放行（能力面不收窄，受理收口归业务侧闸），
     * 但加工期必须拒绝，且报错要能看懂（把"没腿认领"和"内容不是文本"分清）。
     */
    @Test
    void 未知扩展名二进制_查询放行但加工期拒绝() {
        DocumentProcessor processor = DocumentProcessor.defaults(null);
        byte[] binary = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02};
        assertTrue(processor.isProcessable("打包件.unknownext"), "未知扩展名由内置 NUL 探测腿认领");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> processor.process(binary, "打包件.unknownext", null));
        assertTrue(e.getMessage().contains("暂不支持的文件类型"), "加工期拒绝且指名文件: " + e.getMessage());
    }
}
