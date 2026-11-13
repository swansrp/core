package com.bidr.agent.capability.artifact;

import com.bidr.blob.BlobStore;
import com.bidr.document.DocumentConvertException;
import com.bidr.document.spi.ConvertStep;
import com.bidr.llm.agent.file.AgentArtifact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: ArtifactServiceTest
 * Description: 产物生成服务的契约钉桩（JUnit5＋mockito，不起 Spring 容器）：
 * <ul>
 *     <li>docx 腿：Markdown→docx 字节落 {@code agent-artifact/{sessionId}/} 命名空间，
 *     mime 为 docx 固定值，url 取自 BlobStore.presign，ConvertStep 不被调用；</li>
 *     <li>pdf 腿：docx 为中转经 ConvertStep（gotenberg 契约面）转 PDF 后落对象，mime 为 pdf 固定值；</li>
 *     <li>降级面：非法格式/空正文按 IllegalArgumentException 拒绝；转换腿不可达原样上抛
     （DocumentConvertException retryable 语义不吞），工具层统一转人话。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
class ArtifactServiceTest {

    private static final String MARKDOWN = "# 监理日志\n\n**填写人**：张三\n\n| 栏位 | 内容 |\n| --- | --- |\n"
            + "| 施工部位 | 桩号 K12+300 |\n";

    /** docx 腿：落 agent-artifact 命名空间、mime/格式/引用契约、URL 来自现签 */
    @Test
    @DisplayName("generate(docx)：渲染字节落 agent-artifact/{sessionId}/，url 来自 presign，ConvertStep 不被调")
    void generateDocxStoresAndReturnsArtifact() {
        ConvertStep convertStep = mock(ConvertStep.class);
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.presign(anyString())).thenReturn("https://oss.example/download");
        ArtifactService service = new ArtifactService(convertStep, blobStore);

        AgentArtifact artifact = service.generate("s-1", MARKDOWN, "docx", "监理日志-2026-11-13");

        assertEquals("监理日志-2026-11-13.docx", artifact.name(), "展示名＝清洗后的 title＋扩展名");
        assertEquals("docx", artifact.format());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", artifact.mime());
        assertTrue(artifact.fileId().matches("[A-Za-z0-9_-]{1,128}"), "fileId 满足字符集契约");
        assertEquals("https://oss.example/download", artifact.url());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(blobStore).put(keys.capture(), any(byte[].class),
                eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(keys.getValue().startsWith("agent-artifact/s-1/"), "产物键带独立命名空间＋会话段");
        assertTrue(keys.getValue().endsWith(".docx"), "产物键以格式扩展名收尾");
        assertTrue(keys.getValue().contains(artifact.fileId()), "产物键含 file_id（可按事件重构）");

        verify(convertStep, never()).convertToPdf(anyString(), any(byte[].class));
        assertTrue(artifact.size() > 0, "产物字节数非空");
    }

    /** pdf 腿：ConvertStep（gotenberg）是必经通道，产出落对象且 mime 为 application/pdf */
    @Test
    @DisplayName("generate(pdf)：docx 中转经 ConvertStep 转 PDF，mime=application/pdf")
    void generatePdfGoesThroughConvertStep() {
        ConvertStep convertStep = mock(ConvertStep.class);
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
        when(convertStep.convertToPdf(eq("artifact.docx"), any(byte[].class))).thenReturn(pdf);
        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.presign(anyString())).thenReturn("https://oss.example/pdf");
        ArtifactService service = new ArtifactService(convertStep, blobStore);

        AgentArtifact artifact = service.generate("s-1", MARKDOWN, "PDF", "日志"); // 大小写不敏感

        assertEquals("pdf", artifact.format());
        assertEquals("application/pdf", artifact.mime());
        assertEquals("日志.pdf", artifact.name());
        verify(convertStep).convertToPdf(eq("artifact.docx"), any(byte[].class));
        verify(blobStore).put(anyString(), eq(pdf), eq("application/pdf"));
    }

    /** 非法格式拒绝（工具层把该文案转给模型自行纠正），存储零写入 */
    @Test
    @DisplayName("generate(非法格式)：IllegalArgumentException，不触存储")
    void rejectsUnsupportedFormat() {
        BlobStore blobStore = mock(BlobStore.class);
        ArtifactService service = new ArtifactService(mock(ConvertStep.class), blobStore);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.generate("s-1", MARKDOWN, "html", "日志"));
        assertTrue(e.getMessage().contains("html"));
        verify(blobStore, never()).put(anyString(), any(byte[].class), anyString());
    }

    /** 空正文拒绝；缺省 title 按时间戳回落成合法展示名 */
    @Test
    @DisplayName("generate：空正文拒绝；title 缺省回落 artifact-时间戳.docx")
    void rejectsBlankMarkdownAndDefaultsTitle() {
        ArtifactService service = new ArtifactService(mock(ConvertStep.class), mock(BlobStore.class));
        assertThrows(IllegalArgumentException.class, () -> service.generate("s-1", "  ", "docx", "日志"));

        ArtifactService storing = new ArtifactService(mock(ConvertStep.class), mock(BlobStore.class));
        AgentArtifact artifact = storing.generate("s-1", MARKDOWN, "docx", null);
        assertNotNull(artifact.name());
        assertTrue(artifact.name().startsWith("artifact-") && artifact.name().endsWith(".docx"),
                "缺省名＝artifact-{时间戳}.docx");
    }

    /** 转换腿临时故障原样上抛（retryable 语义不吞），工具层负责转人话 */
    @Test
    @DisplayName("generate(pdf) 转换腿不可达：DocumentConvertException 原样上抛")
    void convertFailurePropagates() {
        ConvertStep convertStep = mock(ConvertStep.class);
        when(convertStep.convertToPdf(anyString(), any(byte[].class)))
                .thenThrow(new DocumentConvertException(true, "office 转换服务不可达: x"));
        ArtifactService service = new ArtifactService(convertStep, mock(BlobStore.class));

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> service.generate("s-1", MARKDOWN, "pdf", null));
        assertTrue(e.isRetryable());
    }

    /** 渲染器烟囱：合法 docx（zip 魔数 PK）且包含标题/表格结构面 */
    @Test
    @DisplayName("MarkdownDocxRenderer：产出 PK 魔数 docx，标题与表格行数落位")
    void rendererProducesValidDocxSkeleton() throws Exception {
        String md = "# 一级标题\n\n正文段 **粗体** 与 `code`。\n\n- 甲\n- 乙\n\n"
                + "| 表头A | 表头B |\n| --- | --- |\n| 1 | 2 |\n";
        byte[] docx = MarkdownDocxRenderer.render(md);
        assertTrue(docx.length > 4 && docx[0] == 'P' && docx[1] == 'K', "docx＝zip 容器（PK 魔数）");
        try (java.util.zip.ZipInputStream zip =
                     new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docx))) {
            boolean hasDocument = false;
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    hasDocument = true;
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(xml.contains("一级标题"));
                    assertTrue(xml.contains("表头A"));
                }
            }
            assertTrue(hasDocument, "docx 内含 word/document.xml");
        }
    }
}
