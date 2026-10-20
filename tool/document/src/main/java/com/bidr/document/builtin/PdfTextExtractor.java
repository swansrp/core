package com.bidr.document.builtin;

import com.bidr.document.DocumentProperties;
import com.bidr.document.VisionEndpoint;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Title: PdfTextExtractor
 * Description: 内置 PDF 腿＝历史 {@code FileMarkdownService.convertPdf} 逐字迁入：
 * PDFBox 逐页提取文本；短于 {@code minTextPerPage} 字的页判为扫描页，渲染 {@code renderDpi}
 * 交多模态转录，单文件最多 {@code maxVisionPages} 页、超限注明跳过。硬参数全部
 * {@code tool.document.*} 可配、缺省值不变（50/150/20）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@RequiredArgsConstructor
public class PdfTextExtractor {

    private final DocumentProperties properties;

    private final VisionTranscriber visionTranscriber;

    public String extract(byte[] bytes, VisionEndpoint vision) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            int total = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            int visionPages = 0;
            for (int page = 1; page <= total; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (text.length() >= properties.getMinTextPerPage()) {
                    sb.append(text).append("\n\n");
                    continue;
                }
                // 扫描页：渲染为图片交多模态模型转录
                if (visionPages >= properties.getMaxVisionPages()) {
                    sb.append("> 扫描页数量超过上限 ").append(properties.getMaxVisionPages())
                            .append(" 页，其余页面已跳过\n\n");
                    break;
                }
                visionPages++;
                BufferedImage image = renderer.renderImageWithDPI(page - 1, properties.getRenderDpi());
                sb.append(visionTranscriber.transcribe(toPngBytes(image), "image/png", vision)).append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
