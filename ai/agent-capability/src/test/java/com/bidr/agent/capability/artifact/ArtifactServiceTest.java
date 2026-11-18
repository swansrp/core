package com.bidr.agent.capability.artifact;

import com.bidr.document.DocumentConvertException;
import com.bidr.document.spi.ConvertStep;
import com.bidr.llm.agent.file.AgentArtifact;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
 *     <li>docx 腿：Markdown→docx 字节经 {@code AgentFileStore.putArtifact} 落
 *     {@code agent-artifact/{sessionId}/} 命名空间（键形由存储实现负责），mime 为 docx 固定值，
 *     url 取自 {@code presignedUrl}，ConvertStep 不被调用；</li>
 *     <li>pdf 腿：docx 为中转经 ConvertStep（gotenberg 契约面）转 PDF 后落对象，mime 为 pdf 固定值；</li>
 *     <li>降级面：非法格式/空正文按 IllegalArgumentException 拒绝；存储端口缺席按
 *     IllegalStateException 拒绝；转换腿不可达原样上抛（DocumentConvertException retryable 语义不吞），
 *     工具层统一转人话。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
class ArtifactServiceTest {

    private static final String MARKDOWN = "# 监理日志\n\n**填写人**：张三\n\n| 栏位 | 内容 |\n| --- | --- |\n"
            + "| 施工部位 | 桩号 K12+300 |\n";

    /** 产物引用假件（putArtifact 的回值形态，key 与键形正源一致） */
    private static FileRef artifactRef(String fileId, String name, String mime) {
        return new FileRef(fileId, name, name.length() * 10, mime,
                "agent-artifact/s-1/" + fileId + ".docx");
    }

    /** docx 腿：经 putArtifact 落耐用命名空间、mime/格式/引用契约、URL 来自现签 */
    @Test
    @DisplayName("generate(docx)：经 putArtifact 落 agent-artifact/{sessionId}/，url 来自 presignedUrl，ConvertStep 不被调")
    void generateDocxStoresAndReturnsArtifact() {
        ConvertStep convertStep = mock(ConvertStep.class);
        AgentFileStore store = mock(AgentFileStore.class);
        FileRef ref = artifactRef("f-1", "监理日志-2026-11-13.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        when(store.putArtifact(eq("s-1"), anyString(), anyString(), any(byte[].class))).thenReturn(ref);
        when(store.presignedUrl(eq(ref), anyLong())).thenReturn("https://oss.example/download");
        ArtifactService service = new ArtifactService(convertStep, () -> store);

        AgentArtifact artifact = service.generate("s-1", MARKDOWN, "docx", "监理日志-2026-11-13");

        assertEquals("监理日志-2026-11-13.docx", artifact.name(), "展示名＝清洗后的 title＋扩展名");
        assertEquals("docx", artifact.format());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", artifact.mime());
        assertEquals("f-1", artifact.fileId(), "fileId 直接采用存储端口回值（file_url 持久引用同源）");
        assertEquals("https://oss.example/download", artifact.url());

        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(store).putArtifact(eq("s-1"), names.capture(),
                eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), any(byte[].class));
        assertTrue(names.getValue().endsWith(".docx"), "落盘名以格式扩展名收尾");
        verify(convertStep, never()).convertToPdf(anyString(), any(byte[].class));
        assertTrue(artifact.size() > 0, "产物字节数非空");
    }

    /** pdf 腿：ConvertStep（gotenberg）是必经通道，产物 mime 为 application/pdf */
    @Test
    @DisplayName("generate(pdf)：docx 中转经 ConvertStep 转 PDF，mime=application/pdf")
    void generatePdfGoesThroughConvertStep() {
        ConvertStep convertStep = mock(ConvertStep.class);
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
        when(convertStep.convertToPdf(eq("artifact.docx"), any(byte[].class))).thenReturn(pdf);
        AgentFileStore store = mock(AgentFileStore.class);
        FileRef ref = new FileRef("f-2", "日志.pdf", pdf.length, "application/pdf",
                "agent-artifact/s-1/f-2.pdf");
        when(store.putArtifact(eq("s-1"), anyString(), anyString(), any(byte[].class))).thenReturn(ref);
        when(store.presignedUrl(eq(ref), anyLong())).thenReturn("https://oss.example/pdf");
        ArtifactService service = new ArtifactService(convertStep, () -> store);

        AgentArtifact artifact = service.generate("s-1", MARKDOWN, "PDF", "日志"); // 大小写不敏感

        assertEquals("pdf", artifact.format());
        assertEquals("application/pdf", artifact.mime());
        assertEquals("日志.pdf", artifact.name());
        verify(convertStep).convertToPdf(eq("artifact.docx"), any(byte[].class));
        verify(store).putArtifact(eq("s-1"), eq("日志.pdf"), eq("application/pdf"), eq(pdf));
    }

    /** 非法格式拒绝（工具层把该文案转给模型自行纠正），存储零写入 */
    @Test
    @DisplayName("generate(非法格式)：IllegalArgumentException，不触存储")
    void rejectsUnsupportedFormat() {
        AgentFileStore store = mock(AgentFileStore.class);
        ArtifactService service = new ArtifactService(mock(ConvertStep.class), () -> store);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.generate("s-1", MARKDOWN, "html", "日志"));
        assertTrue(e.getMessage().contains("html"));
        verify(store, never()).putArtifact(anyString(), anyString(), anyString(), any(byte[].class));
    }

    /** 空正文拒绝；缺省 title 按时间戳回落成合法展示名 */
    @Test
    @DisplayName("generate：空正文拒绝；title 缺省回落 artifact-时间戳.docx")
    void rejectsBlankMarkdownAndDefaultsTitle() {
        AgentFileStore store = mock(AgentFileStore.class);
        when(store.putArtifact(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenAnswer(inv -> artifactRef("f-3", inv.getArgument(1), inv.getArgument(2)));
        when(store.presignedUrl(any(), anyLong())).thenReturn("https://oss.example/x");
        ArtifactService service = new ArtifactService(mock(ConvertStep.class), () -> store);
        assertThrows(IllegalArgumentException.class, () -> service.generate("s-1", "  ", "docx", "日志"));

        ArtifactService storing = new ArtifactService(mock(ConvertStep.class), () -> store);
        AgentArtifact artifact = storing.generate("s-1", MARKDOWN, "docx", null);
        assertNotNull(artifact.name());
        assertTrue(artifact.name().startsWith("artifact-") && artifact.name().endsWith(".docx"),
                "缺省名＝artifact-{时间戳}.docx");
    }

    /** 存储端口缺席：人话 IllegalStateException（不 NPE），转换腿零调用 */
    @Test
    @DisplayName("generate：存储端口缺席→IllegalStateException 人话错误")
    void rejectsWhenStorePortAbsent() {
        ConvertStep convertStep = mock(ConvertStep.class);
        ArtifactService service = new ArtifactService(convertStep, () -> null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.generate("s-1", MARKDOWN, "docx", "日志"));
        assertTrue(e.getMessage().contains("AgentFileStore"), "错误须指明缺席的端口");
        verify(convertStep, never()).convertToPdf(anyString(), any(byte[].class));
    }

    /** 转换腿临时故障原样上抛（retryable 语义不吞），工具层负责转人话 */
    @Test
    @DisplayName("generate(pdf) 转换腿不可达：DocumentConvertException 原样上抛")
    void convertFailurePropagates() {
        ConvertStep convertStep = mock(ConvertStep.class);
        when(convertStep.convertToPdf(anyString(), any(byte[].class)))
                .thenThrow(new DocumentConvertException(true, "office 转换服务不可达: x"));
        ArtifactService service = new ArtifactService(convertStep, () -> mock(AgentFileStore.class));

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
