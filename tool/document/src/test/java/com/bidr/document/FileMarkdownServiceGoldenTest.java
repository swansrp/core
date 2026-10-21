package com.bidr.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: FileMarkdownServiceGoldenTest
 * Description: <b>搬家前后逐字对照</b>（M16 棒 3 判据②，入口随修复轮从 llm 搬进本模块）：期望值＝
 * 内置腿集尚在 llm 时现网 {@code FileMarkdownService} 同批样本输出的<b>硬编码字面量</b>
 * （golden 探针于改前落盘定格）——委托 {@link DocumentProcessor} 后同输入必须逐字相等，
 * 覆盖文本/GBK/BOM/html/POI 三件/PDF 文本页/未知扩展名全部分支。
 *
 * @author Sharp
 * @since 2026/10/20
 */
class FileMarkdownServiceGoldenTest {

    private final FileMarkdownService service = new FileMarkdownService(null);

    // ---------------- 文本族（UTF-8 严格解码→GBK 回落→剥 BOM） ----------------

    @Test
    void golden_文本_utf8() throws IOException {
        assertEquals("你好 plain", parse("你好 plain", StandardCharsets.UTF_8, "x.txt"));
    }

    @Test
    void golden_文本_gbk回落() throws IOException {
        assertEquals("中文GBK内容", parse("中文GBK内容", Charset.forName("GBK"), "y.txt"));
    }

    @Test
    void golden_文本_bom剥离() throws IOException {
        assertEquals("带BOM文本", parse("﻿带BOM文本", StandardCharsets.UTF_8, "z.txt"));
    }

    @Test
    void golden_文本族各扩展名同直读() throws IOException {
        assertEquals("# 标题\n\n正文", parse("# 标题\n\n正文", StandardCharsets.UTF_8, "a.md"));
        assertEquals("h1,h2\n1,2", parse("h1,h2\n1,2", StandardCharsets.UTF_8, "b.csv"));
        assertEquals("{\"k\":\"v\"}", parse("{\"k\":\"v\"}", StandardCharsets.UTF_8, "c.json"));
        assertEquals("<a><b>1</b></a>", parse("<a><b>1</b></a>", StandardCharsets.UTF_8, "d.xml"));
        assertEquals("select 1;", parse("select 1;", StandardCharsets.UTF_8, "e.sql"));
        assertEquals("k=v", parse("k=v", StandardCharsets.UTF_8, "f.properties"));
    }

    @Test
    void golden_未知扩展名_非二进制当文本() throws IOException {
        assertEquals("unknown plain text", parse("unknown plain text", StandardCharsets.UTF_8, "h.xyz"));
    }

    @Test
    void golden_未知扩展名_二进制拒绝口径不变() {
        byte[] binary = new byte[]{1, 2, 0, 3, 4, 5, 6, 7};
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.toMarkdown(new ByteArrayInputStream(binary), "bin.xyz"));
        assertTrue(e.getMessage().contains("暂不支持的文件类型"), "二进制必须按原口径拒绝");
    }

    // ---------------- html ----------------

    @Test
    void golden_html结构转() throws IOException {
        assertEquals("# 标题一\n\n段落文字\n\n- 项一\n- 项二\n\n| A1 | B1 |\n| --- | --- |",
                parse("<html><head><title>页题</title></head><body><h1>标题一</h1><p>段落文字</p>"
                                + "<ul><li>项一</li><li>项二</li></ul><table><tr><td>A1</td><td>B1</td></tr></table></body></html>",
                        StandardCharsets.UTF_8, "g.html"));
    }

    // ---------------- POI 三件 ----------------

    @Test
    void golden_docx() throws IOException {
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
        assertEquals("# 文档标题\n\n普通段落文字\n\n| 列一 | 列二 |\n| --- | --- |\n| 1 | 2 |",
                toMarkdown(doc, "i.docx"));
    }

    @Test
    void golden_xlsx() throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("表一");
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("名称");
        r0.createCell(1).setCellValue(123);
        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("甲");
        r1.createCell(1).setCellValue(45.5);
        assertEquals("| 名称 | 123 |\n| --- | --- |\n| 甲 | 45.5 |", toMarkdown(wb, "j.xlsx"));
    }

    @Test
    void golden_pptx() throws IOException {
        XMLSlideShow ppt = new XMLSlideShow();
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox box = slide.createTextBox();
        box.setText("幻灯片文本");
        assertEquals("## 幻灯片 1\n\n幻灯片文本", toMarkdown(ppt, "k.pptx"));
    }

    // ---------------- pdf 文本页 ----------------

    @Test
    void golden_pdf文本页() throws IOException {
        PDDocument pdf = new PDDocument();
        PDPage page = new PDPage();
        pdf.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(pdf, page);
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 700);
        contentStream.showText("hello pdf text page with enough words inside");
        contentStream.endText();
        contentStream.close();
        assertEquals("hello pdf text page with enough words inside", toMarkdown(pdf, "l.pdf"));
    }

    // ---------------- 夹具 ----------------

    private String parse(String text, Charset charset, String filename) throws IOException {
        return service.toMarkdown(new ByteArrayInputStream(text.getBytes(charset)), filename);
    }

    private String toMarkdown(XWPFDocument doc, String filename) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.write(bos);
        return service.toMarkdown(new ByteArrayInputStream(bos.toByteArray()), filename);
    }

    private String toMarkdown(XSSFWorkbook wb, String filename) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return service.toMarkdown(new ByteArrayInputStream(bos.toByteArray()), filename);
    }

    private String toMarkdown(XMLSlideShow ppt, String filename) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ppt.write(bos);
        return service.toMarkdown(new ByteArrayInputStream(bos.toByteArray()), filename);
    }

    private String toMarkdown(PDDocument pdf, String filename) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdf.save(bos);
        return service.toMarkdown(new ByteArrayInputStream(bos.toByteArray()), filename);
    }
}
