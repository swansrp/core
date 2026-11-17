package com.bidr.llm.agent.file;

import com.bidr.document.FileMarkdownService;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: AgentFileParseServiceTest
 * Description: 发起前素材预解析的契约钉桩（JUnit4）：
 * <ul>
 *     <li>上传后可查：受理返回 PENDING/PARSING/DONE 终态链路，成功带 chars/preview；</li>
 *     <li>失败如实回人话（转写腿异常不吞、不泄露堆栈）；</li>
 *     <li>附件不存在 → FAILED；文档能力缺席 → SKIPPED（不拦发起）；</li>
 *     <li>未知 fileId 查询返回 null（调用方按失败提示重试）；forget 清状态。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentFileParseServiceTest {

    private static final String SESSION = "s-parse";
    private static final String FILE_ID = "f-1";

    private final AgentFileStore store = mock(AgentFileStore.class);

    private final AgentFileCapabilityResolver resolver = mock(AgentFileCapabilityResolver.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<FileMarkdownService> markdownProvider = mock(ObjectProvider.class);

    private final FileRef ref = new FileRef(FILE_ID, "现场记录.docx", 12L, "application/msword", "k");

    @Test
    public void startParsesAndReportsDone() throws Exception {
        FileMarkdownService service = mock(FileMarkdownService.class);
        when(store.get(SESSION, FILE_ID)).thenReturn(ref);
        when(store.read(ref)).thenReturn("hello".getBytes("UTF-8"));
        when(service.isProcessable(ref.name())).thenReturn(true);
        when(service.toMarkdown(any(InputStream.class), eq(ref.name()))).thenReturn("第一行\n第二行 正文");
        when(markdownProvider.getIfUnique()).thenReturn(service);
        when(resolver.fileStore()).thenReturn(store);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        AgentFileParseService.ParseView accepted = parseService.start(SESSION, FILE_ID);
        assertEquals(FILE_ID, accepted.fileId());
        assertFalse("受理后尚未完成", AgentFileParseService.STATUS_DONE.equals(accepted.status()));

        AgentFileParseService.ParseView done = awaitStatus(parseService, AgentFileParseService.STATUS_DONE);
        assertTrue(done.ok());
        assertEquals(Integer.valueOf("第一行\n第二行 正文".length()), done.chars());
        assertEquals("第一行 第二行 正文", done.preview());
        assertNull(done.error());
        assertEquals("解析全文须保留供沙箱放件附带", "第一行\n第二行 正文",
                parseService.parsedMarkdown(SESSION, FILE_ID));
    }

    /** parsedMarkdown：未解析/不存在 → null；解析失败 → null（不放解析正文，仅原件） */
    @Test
    public void parsedMarkdownNullWhenAbsentOrFailed() throws Exception {
        FileMarkdownService service = mock(FileMarkdownService.class);
        when(store.get(SESSION, FILE_ID)).thenReturn(ref);
        when(store.read(ref)).thenReturn("hello".getBytes("UTF-8"));
        when(service.isProcessable(ref.name())).thenReturn(true);
        when(service.toMarkdown(any(InputStream.class), anyString())).thenThrow(new IOException("boom"));
        when(markdownProvider.getIfUnique()).thenReturn(service);
        when(resolver.fileStore()).thenReturn(store);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        assertNull("未受理时取不到全文", parseService.parsedMarkdown(SESSION, FILE_ID));
        parseService.start(SESSION, FILE_ID);
        awaitStatus(parseService, AgentFileParseService.STATUS_FAILED);
        assertNull("解析失败不保留全文", parseService.parsedMarkdown(SESSION, FILE_ID));
        assertNull("不存在的 fileId 回 null", parseService.parsedMarkdown(SESSION, "no-such"));
    }

    @Test
    public void parseFailureReturnsHumanReadableError() throws Exception {
        FileMarkdownService service = mock(FileMarkdownService.class);
        when(store.get(SESSION, FILE_ID)).thenReturn(ref);
        when(store.read(ref)).thenReturn("hello".getBytes("UTF-8"));
        when(service.isProcessable(ref.name())).thenReturn(true);
        when(service.toMarkdown(any(InputStream.class), anyString()))
                .thenThrow(new IOException("MinerU 服务不可达\n请稍后重试"));
        when(markdownProvider.getIfUnique()).thenReturn(service);
        when(resolver.fileStore()).thenReturn(store);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        parseService.start(SESSION, FILE_ID);

        AgentFileParseService.ParseView failed = awaitStatus(parseService, AgentFileParseService.STATUS_FAILED);
        assertFalse(failed.ok());
        assertEquals("MinerU 服务不可达 请稍后重试", failed.error());
    }

    @Test
    public void missingAttachmentFailsImmediately() {
        when(store.get(SESSION, FILE_ID)).thenReturn(null);
        when(resolver.fileStore()).thenReturn(store);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        AgentFileParseService.ParseView view = parseService.start(SESSION, FILE_ID);

        assertEquals(AgentFileParseService.STATUS_FAILED, view.status());
        assertFalse(view.ok());
        assertTrue(view.error().contains("不存在"));
    }

    @Test
    public void absentDocumentCapabilityIsSkippedNotBlocked() {
        when(store.get(SESSION, FILE_ID)).thenReturn(ref);
        when(resolver.fileStore()).thenReturn(store);
        when(markdownProvider.getIfUnique()).thenReturn(null);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        AgentFileParseService.ParseView view = parseService.start(SESSION, FILE_ID);

        assertEquals(AgentFileParseService.STATUS_SKIPPED, view.status());
        assertTrue("跳过校验不应拦发起", view.ok());
    }

    @Test
    public void statusUnknownAndForget() {
        when(store.get(SESSION, FILE_ID)).thenReturn(null);
        when(resolver.fileStore()).thenReturn(store);

        AgentFileParseService parseService = new AgentFileParseService(resolver, markdownProvider);
        assertNull(parseService.status(SESSION, "missing"));
        parseService.start(SESSION, FILE_ID);
        assertEquals(AgentFileParseService.STATUS_FAILED, parseService.status(SESSION, FILE_ID).status());
        parseService.forget(SESSION, FILE_ID);
        assertNull(parseService.status(SESSION, FILE_ID));
    }

    /**
     * 异步解析轮询到终态（超时 3s）：终态判定按 status 而非 ok——FAILED 也属终态。
     */
    private AgentFileParseService.ParseView awaitStatus(AgentFileParseService service, String expected) {
        long deadline = System.currentTimeMillis() + 3000L;
        AgentFileParseService.ParseView view = service.status(SESSION, FILE_ID);
        while (System.currentTimeMillis() < deadline) {
            view = service.status(SESSION, FILE_ID);
            if (expected.equals(view.status())) {
                return view;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return view;
    }
}