package com.bidr.llm.agent.file;

import com.bidr.llm.document.FileMarkdownTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: SessionAttachmentReadToolTest
 * Description: C-1 会话受限读取工具的签名与行为钉桩（JUnit4）：
 * <ul>
 *     <li>参数面只有 file_id/max_chars/offset，<b>没有 urlOrPath</b>（编译面即证）；</li>
 *     <li>工具名 read_attachment；</li>
 *     <li>store.get 返回 null / 抛异常 / fileId 非法 ⇒ 人话错误且不泄露内部细节；</li>
 *     <li>正常 fileId ⇒ get → presignedUrl(3600) → FileMarkdownTool.readFile 正文; </li>
 *     <li>存储/文档能力缺席 ⇒ 明确错误文本。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
public class SessionAttachmentReadToolTest {

    private static final String SESSION = "s-read-tool";

    private final AgentFileStore store = mock(AgentFileStore.class);

    private final FileMarkdownTool markdownTool = mock(FileMarkdownTool.class);

    /** 签名即安全边界：只有 file_id/max_chars/offset，urlOrPath 不存在 */
    @Test
    public void toolSignatureHasNoUrlOrPathParameter() throws Exception {
        Method method = SessionAttachmentReadTool.class.getMethod(
                "readAttachment", String.class, Integer.class, Integer.class);
        assertEquals("工具方法只允许三个参数", 3, method.getParameterCount());

        Tool tool = method.getAnnotation(Tool.class);
        assertNotNull(tool);
        assertEquals("工具名必须是 read_attachment", SessionAttachmentReadTool.TOOL_NAME, tool.name());

        for (Parameter parameter : method.getParameters()) {
            String declaredName = parameter.isAnnotationPresent(P.class)
                    ? parameter.getAnnotation(P.class).name() : parameter.getName();
            assertFalse("参数 " + declaredName + " 不得是任意地址/路径",
                    declaredName.toLowerCase().contains("url") || declaredName.toLowerCase().contains("path"));
        }
        Parameter first = method.getParameters()[0];
        assertEquals("file_id", first.getAnnotation(P.class).name());
        assertEquals(String.class, first.getType());
    }

    /** store.get 返回 null：统一人话错误，不泄露 key/路径 */
    @Test
    public void missingAttachmentReturnsHumanReadableError() {
        when(store.get(SESSION, "not-exist")).thenReturn(null);
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);

        String result = tool.readAttachment("not-exist", 0, 0);

        assertEquals("附件不存在或不属于本会话", result);
        assertFalse(result.contains("key"));
        assertFalse(result.contains(SESSION));
    }

    /** store.get 抛异常：返回同一人话错误，内部细节只进日志 */
    @Test
    public void storeFailureReturnsHumanReadableErrorWithoutInternals() {
        when(store.get(SESSION, "f1")).thenThrow(new IllegalStateException("metaKey=/secret/path"));
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);

        String result = tool.readAttachment("f1", 0, 0);

        assertEquals("附件不存在或不属于本会话", result);
        assertFalse(result.contains("/secret/path"));
    }

    /** 正常 fileId：get → presignedUrl(3600) → readFile(url,maxChars,offset)，正文原样返回 */
    @Test
    public void existingAttachmentIsParsedThroughMarkdownTool() {
        FileRef full = new FileRef("f1", "监理日志.docx", 4L,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "agent-session/1/s/f1.docx");
        when(store.get(SESSION, "f1")).thenReturn(full);
        when(store.presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS))
                .thenReturn("http://localhost:8090/oss/f1.docx");
        when(markdownTool.readFile("http://localhost:8090/oss/f1.docx", 1200, 40))
                .thenReturn("监理日志正文");

        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);
        String result = tool.readAttachment("f1", 1200, 40);

        assertEquals("监理日志正文", result);
        verify(store).presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS);
        verify(markdownTool).readFile("http://localhost:8090/oss/f1.docx", 1200, 40);
    }

    /** m-r2-3：同一 (sessionId,fileId) 分页续读复用同一预签 URL（命中 FileMarkdownTool 解析快照） */
    @Test
    public void presignedUrlIsCachedAcrossPages() {
        FileRef full = new FileRef("f1", "监理日志.docx", 4L,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "k");
        when(store.get(SESSION, "f1")).thenReturn(full);
        when(store.presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS))
                .thenReturn("http://localhost:8090/oss/f1.docx");
        when(markdownTool.readFile("http://localhost:8090/oss/f1.docx", 100, 0)).thenReturn("第一页");
        when(markdownTool.readFile("http://localhost:8090/oss/f1.docx", 100, 100)).thenReturn("第二页");

        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);
        assertEquals("第一页", tool.readAttachment("f1", 100, 0));
        assertEquals("第二页", tool.readAttachment("f1", 100, 100));

        verify(store, org.mockito.Mockito.times(1))
                .presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS);
        verify(markdownTool, org.mockito.Mockito.times(2))
                .readFile(eq("http://localhost:8090/oss/f1.docx"), anyInt(), anyInt());
    }

    /** 非法 fileId（带路径分隔符）按不存在处理，不触达存储 */
    @Test
    public void unsafeFileIdIsRejectedBeforeStoreLookup() {
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);

        String result = tool.readAttachment("../other-session/meta", 0, 0);

        assertEquals("附件不存在或不属于本会话", result);
        verify(store, org.mockito.Mockito.never()).get(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 空 fileId 给明确引导 */
    @Test
    public void blankFileIdIsRejected() {
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);
        assertTrue(tool.readAttachment("  ", 0, 0).contains("file_id 不能为空"));
    }

    /** 存储端口缺席：明确错误文本，不抛 */
    @Test
    public void missingStoreReturnsExplicitError() {
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, null, markdownTool);
        assertTrue(tool.readAttachment("f1", 0, 0).contains("未装配附件存储"));
    }

    /** 文档解析能力缺席：明确错误文本，不抛 */
    @Test
    public void missingMarkdownToolReturnsExplicitError() {
        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, null);
        assertTrue(tool.readAttachment("f1", 0, 0).contains("未装配文档解析能力"));
    }

    /** 签址失败：返回通用失败文案（不回显异常信息） */
    @Test
    public void presignFailureReturnsGenericError() {
        FileRef full = new FileRef("f1", "a.txt", 2L, "text/plain", "k");
        when(store.get(SESSION, "f1")).thenReturn(full);
        when(store.presignedUrl(eq(full), eq(AttachmentContextAssembler.URL_TTL_SECONDS)))
                .thenThrow(new IllegalStateException("signature secret"));

        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);
        String result = tool.readAttachment("f1", 0, 0);

        assertEquals("附件读取失败，请稍后重试", result);
        assertFalse(result.contains("signature"));
    }

    /** 解析器返回分页文本原样透传（offset/maxChars 语义由 FileMarkdownTool 统一） */
    @Test
    public void pagedTextIsPassedThrough() {
        FileRef full = new FileRef("f1", "a.txt", 2L, "text/plain", "k");
        when(store.get(SESSION, "f1")).thenReturn(full);
        when(store.presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS)).thenReturn("u");
        when(markdownTool.readFile(eq("u"), anyInt(), anyInt())).thenReturn("窗口正文\n\n[已到末尾]");

        SessionAttachmentReadTool tool = new SessionAttachmentReadTool(SESSION, store, markdownTool);
        assertEquals("窗口正文\n\n[已到末尾]", tool.readAttachment("f1", 20000, 20000));
    }

    /** 工具对象只有一个 @Tool 方法（装配面不会混入 raw read_file） */
    @Test
    public void toolObjectExposesSingleReadAttachmentMethod() {
        long annotated = 0;
        for (Method method : SessionAttachmentReadTool.class.getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                annotated++;
                assertEquals("readAttachment", method.getName());
            }
        }
        assertEquals("工具对象只应暴露 read_attachment", 1, annotated);
        assertFalse(Collections.singletonList(SessionAttachmentReadTool.class.getMethods())
                .toString().contains("readFile"));
    }
}