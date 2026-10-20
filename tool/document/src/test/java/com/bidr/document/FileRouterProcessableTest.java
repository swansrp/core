package com.bidr.document;

import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.TranscribeStep;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * isProcessable 判据（立项 :205 判据⑥，§7.2 正源）：＝路由分派<b>副产物</b>
 * （逐腿问认领＋内置分派面），不是清单判定；能力面不削（「core 支持，业务自己挡」）；
 * 不装任何增强腿时查询不因「不支持」抛。
 *
 * @author Sharp
 * @since 2026/10/20
 */
class FileRouterProcessableTest {

    /** 认领 pdf 的增强腿桩（形如 mineru 的认领面） */
    private static class PdfLeg implements TranscribeStep {

        @Override
        public String legName() {
            return "pdf-leg";
        }

        @Override
        public boolean isAvailable(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".pdf");
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            return "md";
        }
    }

    /** 认领 office 六扩展名的 convert 腿桩（形如 gotenberg 的认领面） */
    private static class OfficeConvertLeg implements ConvertStep {

        @Override
        public boolean isConvertible(String filename) {
            String lower = filename == null ? "" : filename.toLowerCase();
            return lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
                    || lower.endsWith(".doc") || lower.endsWith(".xls") || lower.endsWith(".ppt");
        }

        @Override
        public byte[] convertToPdf(String fileName, byte[] content) {
            throw new UnsupportedOperationException("本判据只问认领，不真转");
        }
    }

    @Test
    void 装增强腿时_各类型认领判定() {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(), null,
                new PdfLeg(), null, null, null, null);
        assertTrue(processor.isProcessable("a.pdf"), "增强腿认领 pdf");
        assertTrue(processor.isProcessable("台账.xlsx"), "xlsx 由内置 POI 分派面认领（能力面不削）");
        assertTrue(processor.isProcessable("汇报.ppt"), "ppt 旧格式同理");
        assertTrue(processor.isProcessable("x.txt"));
        assertTrue(processor.isProcessable("y.html"));
        assertTrue(processor.isProcessable("图.png"));
    }

    @Test
    void 不装任何增强腿_查询不抛且内置面照常认领() {
        DocumentProcessor processor = DocumentProcessor.defaults(null);
        assertTrue(processor.isProcessable("a.xlsx"), "无增强腿：xlsx 仍由内置分派面认领");
        assertTrue(processor.isProcessable("a.pdf"));
        assertTrue(processor.isProcessable("无扩展名"), "未知扩展名归 NUL 探测腿（内容级拒绝在加工时发生）");
        assertTrue(processor.isProcessable(null) , "null 文件名也不抛（探测腿兜住）");
        assertTrue(processor.isProcessable("纯文本.xyz"));
    }

    @Test
    void convert腿在场时由它认领office() {
        DocumentProcessor processor = new DocumentProcessor(DocumentProperties.defaults(),
                Collections.<ConvertStep>singletonList(new OfficeConvertLeg()), null, null, null, null, null);
        assertTrue(processor.isProcessable("报告.docx"), "convert 腿认领＝office 先转 PDF 的路走支持");
        assertTrue(processor.isProcessable("旧档.doc"));
        assertTrue(processor.isProcessable("表.xls"));
        assertTrue(processor.isProcessable("notes.md"), "非 office 文件不经 convert 腿，仍由内置面认领");
    }
}
