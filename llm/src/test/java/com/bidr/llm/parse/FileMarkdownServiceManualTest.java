package com.bidr.llm.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FileMarkdownService 全链路手工验证（TestNG，mvn test 触发）：
 * 各格式样本文件全部代码生成，不依赖外部资源；
 * 多模态配置经系统属性 vision.baseUrl / vision.apiKey / vision.modelName 传入，不落代码。
 *
 * @author Sharp
 */
public class FileMarkdownServiceManualTest {

    private static final Path SAMPLE_DIR = Paths.get("target", "test-samples");

    private FileMarkdownService service;
    private VisionModelConfig vision;

    @BeforeClass
    public void setUp() throws IOException {
        Files.createDirectories(SAMPLE_DIR);
        String baseUrl = System.getProperty("vision.baseUrl");
        String apiKey = System.getProperty("vision.apiKey");
        String modelName = System.getProperty("vision.modelName");
        vision = (baseUrl != null && apiKey != null && modelName != null)
                ? VisionModelConfig.builder().baseUrl(baseUrl).apiKey(apiKey)
                        .modelName(modelName).timeoutSeconds(180).build()
                : null;
        // 独立验证：不走 Spring/数据库，多模态配置显式传入
        service = new FileMarkdownService(null);
        System.out.println("[setup] vision 配置 " + (vision != null && vision.isUsable() ? "已提供: " + modelName : "未提供（多模态用例将跳过）"));
    }

    // ---------------- 纯文本 ----------------

    @Test
    public void testTxtUtf8() throws IOException {
        Path file = SAMPLE_DIR.resolve("utf8.txt");
        Files.write(file, "第一行中文内容\n第二行 ABC123".getBytes(StandardCharsets.UTF_8));
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("第一行中文内容"), "UTF-8 文本应原样输出");
        Assert.assertTrue(md.contains("ABC123"), "ASCII 内容应保留");
        System.out.println("[txt-utf8]\n" + md);
    }

    @Test
    public void testTxtGbk() throws IOException {
        Path file = SAMPLE_DIR.resolve("gbk.txt");
        Files.write(file, "GBK 编码的中文：采购合同额".getBytes(Charset.forName("GBK")));
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("采购合同额"), "GBK 文本应回落解码成功");
        System.out.println("[txt-gbk]\n" + md);
    }

    @Test
    public void testInputStreamEntry() throws IOException {
        byte[] bytes = "流式入口内容".getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            String md = service.toMarkdown(in, "demo.txt");
            Assert.assertTrue(md.contains("流式入口内容"), "InputStream 入口应按文件名识别格式");
        }
    }

    // ---------------- HTML ----------------

    @Test
    public void testHtml() throws IOException {
        Path file = SAMPLE_DIR.resolve("page.html");
        String html = "<html><head><title>t</title></head><body>"
                + "<h1>项目概览</h1><p>正文段落 <strong>加粗</strong></p>"
                + "<ul><li>第一项</li><li>第二项</li></ul>"
                + "<table><tr><th>名称</th><th>数量</th></tr><tr><td>钢筋</td><td>100</td></tr></table>"
                + "<script>var x=1;</script></body></html>";
        Files.write(file, html.getBytes(StandardCharsets.UTF_8));
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("# 项目概览"), "h1 应转 # 标题");
        Assert.assertTrue(md.contains("- 第一项"), "ul 应转 - 列表");
        Assert.assertTrue(md.contains("| 名称 | 数量 |"), "表格应转 Markdown 表格");
        Assert.assertTrue(md.contains("**加粗**"), "strong 应转加粗语法");
        Assert.assertFalse(md.contains("var x=1"), "script 应被剔除");
        System.out.println("[html]\n" + md);
    }

    // ---------------- docx ----------------

    @Test
    public void testDocx() throws IOException {
        Path file = SAMPLE_DIR.resolve("doc.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph heading = doc.createParagraph();
            // POI 4.1.2 无公开 setStyleID，经 CT 层写入段落样式 id
            CTPPr ppr = heading.getCTP().isSetPPr() ? heading.getCTP().getPPr() : heading.getCTP().addNewPPr();
            ppr.addNewPStyle().setVal("Heading1");
            heading.createRun().setText("施工方案总述");
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("本方案适用于主体结构施工阶段。");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("工序");
            table.getRow(0).getCell(1).setText("工期(天)");
            table.getRow(1).getCell(0).setText("绑扎钢筋");
            table.getRow(1).getCell(1).setText("12");
            write(docToBytes(doc), file);
        }
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("# 施工方案总述"), "heading 样式应转 # 标题");
        Assert.assertTrue(md.contains("| 工序 | 工期(天) |"), "docx 表格应转 Markdown 表格");
        Assert.assertTrue(md.contains("绑扎钢筋"), "表格内容不丢");
        System.out.println("[docx]\n" + md);
    }

    // ---------------- xlsx ----------------

    @Test
    public void testXlsx() throws IOException {
        Path file = SAMPLE_DIR.resolve("book.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet1 = workbook.createSheet("合同台账");
            fillRow(sheet1.createRow(0), "合同编号", "合同额", "签订日期");
            fillRow(sheet1.createRow(1), "HT-2026-001", "12500000", "2026-01-15");
            XSSFSheet sheet2 = workbook.createSheet("备注页");
            fillRow(sheet2.createRow(0), "说明", "多Sheet验证");
            write(workbookToBytes(workbook), file);
        }
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("## 合同台账"), "多 Sheet 应加二级标题");
        Assert.assertTrue(md.contains("| 合同编号 | 合同额 | 签订日期 |"), "表头应成首行");
        Assert.assertTrue(md.contains("HT-2026-001"), "数据行不丢");
        Assert.assertTrue(md.contains("## 备注页"), "第二个 Sheet 应输出");
        System.out.println("[xlsx]\n" + md);
    }

    // ---------------- pptx ----------------

    @Test
    public void testPptx() throws IOException {
        Path file = SAMPLE_DIR.resolve("slides.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide1 = ppt.createSlide();
            XSLFTextBox box1 = slide1.createTextBox();
            box1.setText("安全培训计划");
            XSLFSlide slide2 = ppt.createSlide();
            XSLFTextBox box2 = slide2.createTextBox();
            box2.setText("第一课：高空作业规范");
            write(pptToBytes(ppt), file);
        }
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("## 幻灯片 1"), "首页应有二级标题");
        Assert.assertTrue(md.contains("安全培训计划"), "第一页文本不丢");
        Assert.assertTrue(md.contains("第一课：高空作业规范"), "第二页文本不丢");
        System.out.println("[pptx]\n" + md);
    }

    // ---------------- PDF 文本页 ----------------

    @Test
    public void testPdfTextPage() throws IOException {
        Path file = SAMPLE_DIR.resolve("text.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 14);
                cs.newLineAtOffset(60, 750);
                cs.showText("PDF TEXT PAGE ONE");
                cs.newLineAtOffset(0, -24);
                cs.showText("This line verifies direct text extraction.");
                cs.endText();
            }
            write(docToBytes(doc), file);
        }
        String md = service.toMarkdown(file);
        Assert.assertTrue(md.contains("PDF TEXT PAGE ONE"), "文本页应直接提取不走多模态");
        System.out.println("[pdf-text]\n" + md);
    }

    // ---------------- PDF 扫描页（走多模态） ----------------

    @Test
    public void testPdfScannedPage() throws IOException {
        if (vision == null || !vision.isUsable()) {
            System.out.println("[pdf-scanned] 跳过：未提供 vision 配置");
            return;
        }
        Path file = SAMPLE_DIR.resolve("scanned.pdf");
        BufferedImage image = drawTextImage("SCANNED PAGE DEMO 88877");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, image), 40, 500, 500, 188);
            }
            write(docToBytes(doc), file);
        }
        String md = service.toMarkdown(file, vision);
        System.out.println("[pdf-scanned] 多模态转录结果：\n" + md);
        Assert.assertNotNull(md, "扫描页转录结果不应为 null");
        Assert.assertFalse(md.trim().isEmpty(), "扫描页转录结果不应为空");
    }

    // ---------------- 图片直传（走多模态） ----------------

    @Test
    public void testImageVision() throws IOException {
        if (vision == null || !vision.isUsable()) {
            System.out.println("[image] 跳过：未提供 vision 配置");
            return;
        }
        Path file = SAMPLE_DIR.resolve("vision-test.png");
        ImageIO.write(drawTextImage("HSE VISION TEST 2026"), "png", file.toFile());
        String md = service.toMarkdown(file, vision);
        System.out.println("[image] 多模态转录结果：\n" + md);
        Assert.assertNotNull(md, "图片转录结果不应为 null");
        Assert.assertFalse(md.trim().isEmpty(), "图片转录结果不应为空");
    }

    // ---------------- 工具 ----------------

    private BufferedImage drawTextImage(String text) {
        BufferedImage image = new BufferedImage(800, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 300);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        g.drawString(text, 60, 160);
        g.dispose();
        return image;
    }

    private void fillRow(XSSFRow row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private byte[] docToBytes(XWPFDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    private byte[] workbookToBytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private byte[] pptToBytes(XMLSlideShow ppt) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ppt.write(out);
        return out.toByteArray();
    }

    private byte[] docToBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    private void write(byte[] bytes, Path file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            out.write(bytes);
        }
    }
}
