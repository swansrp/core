package com.bidr.llm.agent.sandbox;

import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: SessionSandboxFetchToolTest
 * Description: sandbox_fetch 工具钉桩（棒3a，plain JUnit4 + Mockito，全 mock 端口无真沙箱）：
 * 小文本（严格 UTF-8 且 &lt;32KB）直回正文；大文件/二进制走 AgentFileStore.put＋presignedUrl
 * ＋ARTIFACT 事件（payload 六键经 AgentArtifact.toEventPayload()）；store 缺席人话；
 * 路径空拒绝；沙箱端口缺席人话。
 * <p>
 * 说明：本模块 surefire 走 JUnit4 provider（junit:junit 在场，未声明 platform provider），
 * 故本类用 JUnit4 断言；写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
public class SessionSandboxFetchToolTest {

    private static final String SESSION = "s-sbx-2";

    private AgentSessionContext ctxMock() {
        AgentSessionContext ctx = mock(AgentSessionContext.class);
        when(ctx.getSessionId()).thenReturn(SESSION);
        when(ctx.emit(anyString(), any())).thenReturn(9L);
        return ctx;
    }

    /** 小文本（UTF-8 可解码且 <32KB）＝直接回文件正文，不需要存储端口 */
    @Test
    public void smallTextIsReturnedInlineWithoutStore() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        String content = "桩基检测结果：12 根全部合格";
        when(sandbox.fetch(SESSION, "out/summary.txt")).thenReturn(content.getBytes(StandardCharsets.UTF_8));

        String result = new SessionSandboxFetchTool(SESSION, null, sandbox, null)
                .fetchFile("out/summary.txt");

        assertEquals("小文本必须逐字直回", content, result);
    }

    /** 文本判据第一道闸：非法 UTF-8 字节序列（小二进制）也走产物路线 */
    @Test
    public void binaryEvenIfSmallGoesArtifactRoute() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        byte[] bytes = {0x50, 0x4B, 0x03, 0x04, (byte) 0xFF, 0x0D, 0x0A};
        when(sandbox.fetch(SESSION, "out/result.bin")).thenReturn(bytes);
        AgentFileStore fileStore = mock(AgentFileStore.class);
        FileRef ref = new FileRef("f-bin", "result.bin", bytes.length, "application/octet-stream", "k-bin");
        when(fileStore.put(eq(SESSION), eq("result.bin"), eq("application/octet-stream"), eq(bytes)))
                .thenReturn(ref);
        when(fileStore.presignedUrl(eq(ref), Mockito.anyLong())).thenReturn("https://oss.example/f-bin");

        String result = new SessionSandboxFetchTool(SESSION, ctxMock(), sandbox, fileStore)
                .fetchFile("out/result.bin");

        assertTrue(result.startsWith("文件已取出：result.bin（" + bytes.length + "B），下载链接：https://oss.example/f-bin"));
    }

    /** 大文本（≥32KB 的合法 UTF-8）走产物路线：put＋presignedUrl(3600)＋ARTIFACT 事件六键 */
    @Test
    public void largeTextGoesThroughStoreAndEmitsArtifactEvent() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        byte[] bytes = repeat('a', 32 * 1024 + 1).getBytes(StandardCharsets.UTF_8);
        when(sandbox.fetch(SESSION, "out/big.txt")).thenReturn(bytes);
        AgentFileStore fileStore = mock(AgentFileStore.class);
        FileRef ref = new FileRef("f-big", "big.txt", bytes.length, "application/octet-stream", "k-big");
        when(fileStore.put(eq(SESSION), eq("big.txt"), eq("application/octet-stream"), eq(bytes)))
                .thenReturn(ref);
        when(fileStore.presignedUrl(eq(ref), eq(3600L))).thenReturn("https://oss.example/f-big");
        AgentSessionContext ctx = ctxMock();

        String result = new SessionSandboxFetchTool(SESSION, ctx, sandbox, fileStore)
                .fetchFile("out/big.txt");

        assertEquals("文件已取出：big.txt（" + bytes.length + "B），下载链接：https://oss.example/f-big", result);
        // 事件 payload 必须经 AgentArtifact.toEventPayload() 六键（snake_case），不手拼 Map
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(ctx).emit(eq(AgentEvent.ARTIFACT), payloadCaptor.capture());
        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getValue();
        assertEquals("file_id", "f-big", payload.get("file_id"));
        assertEquals("name", "big.txt", payload.get("name"));
        assertEquals("size", (long) bytes.length, payload.get("size"));
        assertEquals("mime", "application/octet-stream", payload.get("mime"));
        assertEquals("format", "txt", payload.get("format"));
        assertEquals("url", "https://oss.example/f-big", payload.get("url"));
        assertEquals("payload 只许六键", 6, payload.size());
    }

    /** 大文件但存储端口缺席＝人话（小文本路线不受影响） */
    @Test
    public void storeAbsentOnArtifactRouteReturnsHumanText() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        byte[] bytes = repeat('x', 40 * 1024).getBytes(StandardCharsets.UTF_8);
        when(sandbox.fetch(SESSION, "out/large.log")).thenReturn(bytes);

        String result = new SessionSandboxFetchTool(SESSION, null, sandbox, null)
                .fetchFile("out/large.log");

        assertTrue(result.startsWith("沙箱取件未启用：当前宿主未装配文件存储"));
    }

    /** 沙箱端口缺席＝人话（与 sandbox_run 同口径） */
    @Test
    public void sandboxAbsentReturnsHumanText() {
        String result = new SessionSandboxFetchTool(SESSION, null, null, null).fetchFile("out/a.txt");
        assertEquals("沙箱未启用：当前宿主未装配沙箱服务", result);
    }

    /** 空路径拒绝（不触碰端口） */
    @Test
    public void blankPathIsRejectedWithoutTouchingPort() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        String result = new SessionSandboxFetchTool(SESSION, null, sandbox, null).fetchFile("  ");
        assertTrue(result.startsWith("拒绝：path 不能为空"));
        verify(sandbox, never()).fetch(anyString(), anyString());
    }

    /** 文件不存在（fetch 回 null）＝人话提示核对路径 */
    @Test
    public void missingFileReturnsHumanText() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.fetch(SESSION, "out/nope.csv")).thenReturn(null);
        String result = new SessionSandboxFetchTool(SESSION, null, sandbox, null).fetchFile("out/nope.csv");
        assertTrue(result.startsWith("沙箱内文件不存在"));
    }

    /** 非法路径（穿越/绝对路径，实现侧抛 IllegalArgumentException）＝「拒绝：」人话 */
    @Test
    public void illegalPathIsRejectedWithPrefix() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.fetch(SESSION, "../etc/passwd"))
                .thenThrow(new IllegalArgumentException("路径必须为沙箱内相对路径"));
        String result = new SessionSandboxFetchTool(SESSION, null, sandbox, null).fetchFile("../etc/passwd");
        assertEquals("拒绝：路径必须为沙箱内相对路径", result);
    }

    /** put/presign 抛异常＝人话（不向模型抛栈）；不 emit 事件 */
    @Test
    public void storeFailureBecomesHumanTextWithoutEvent() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        byte[] bytes = repeat('y', 33 * 1024).getBytes(StandardCharsets.UTF_8);
        when(sandbox.fetch(SESSION, "out/x.log")).thenReturn(bytes);
        AgentFileStore fileStore = mock(AgentFileStore.class);
        when(fileStore.put(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenThrow(new IllegalStateException("oss down"));
        AgentSessionContext ctx = ctxMock();

        String result = new SessionSandboxFetchTool(SESSION, ctx, sandbox, fileStore)
                .fetchFile("out/x.log");

        assertTrue(result.startsWith("沙箱取件失败（转存暂不可用）"));
        verify(ctx, never()).emit(anyString(), any());
    }

    /** 常量契约：工具名（console 能力包按此登记） */
    @Test
    public void toolNameConstant() {
        assertEquals("sandbox_fetch", SessionSandboxFetchTool.TOOL_NAME);
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
