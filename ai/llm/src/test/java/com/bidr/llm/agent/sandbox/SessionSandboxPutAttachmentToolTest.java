package com.bidr.llm.agent.sandbox;

import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Title: SessionSandboxPutAttachmentToolTest
 * Description: sandbox_put_attachment 工具钉桩（plain JUnit4 + Mockito，全 mock 端口无真沙箱）：
 * 工具名唯一、端口缺席人话、非法 file_id/附件不存在、happy path 缺省 {@code input/<原名>}
 * 且原始字节原样透传、{@code path} 空白（null/空串/纯空格）同样走缺省、自定义 path、
 * 非法相对路径（{@code ../x}/绝对路径/反斜杠/控制字符/空段/点号段/冒号）、会话标识缺失、
 * read 异常人话、端口返回改名后实际路径时工具文本如实回显、端口回空落点时按请求路径兜底回显、
 * 参数 schema 守卫（{@code path} 可选 / {@code file_id} 必填，防模型侧退化为 path 必填）。
 * <p>
 * 说明：本模块 surefire 走 JUnit4 provider（junit:junit 在场，未声明 platform provider），
 * 故本类用 JUnit4 断言；写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
public class SessionSandboxPutAttachmentToolTest {

    private static final String SESSION = "s-put-1";

    private static final byte[] DOCX_BYTES = {0x50, 0x4B, 0x03, 0x04};

    private static final FileRef DOCX = new FileRef("f-docx", "监理日志.docx", DOCX_BYTES.length,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "k-docx");

    /** 工具名常量固定；反射可见的 @Tool 方法只有一只且 name 与常量一致 */
    @Test
    public void toolNameIsUniqueAndFixed() {
        assertEquals("sandbox_put_attachment", SessionSandboxPutAttachmentTool.TOOL_NAME);
        List<String> names = new ArrayList<>();
        for (Method method : SessionSandboxPutAttachmentTool.class.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                names.add(tool.name());
            }
        }
        assertEquals("工具面只许一个 @Tool 方法", 1, names.size());
        assertEquals(SessionSandboxPutAttachmentTool.TOOL_NAME, names.get(0));
    }

    /**
     * R2-S3：schema 半边守卫——{@code path} 必须保持 {@code required=false}（省略/空白＝默认落点，
     * 删掉注解或改必填会让模型侧退化为“path 必填”，正是 R1-I1 的原始缺陷），{@code file_id} 仍必填。
     * 按 {@code @P.name()} 匹配，不依赖参数声明顺序。
     */
    @Test
    public void toolParameterSchemaKeepsPathOptionalAndFileIdRequired() throws Exception {
        Method method = SessionSandboxPutAttachmentTool.class
                .getMethod("putAttachment", String.class, String.class);
        Parameter[] parameters = method.getParameters();
        assertEquals("putAttachment 参数个数", 2, parameters.length);
        P pathParam = null;
        P fileIdParam = null;
        for (Parameter parameter : parameters) {
            P annotation = parameter.getAnnotation(P.class);
            if (annotation == null) {
                continue;
            }
            if ("path".equals(annotation.name())) {
                pathParam = annotation;
            } else if ("file_id".equals(annotation.name())) {
                fileIdParam = annotation;
            }
        }
        assertNotNull("path 参数必须带 @P(name=\"path\")", pathParam);
        assertFalse("path 必须 required=false（模型可省略，缺省 input/<原文件名>）", pathParam.required());
        assertNotNull("file_id 参数必须带 @P(name=\"file_id\")", fileIdParam);
        assertTrue("file_id 必须 required=true（缺省注解默认值）", fileIdParam.required());
    }

    /** 沙箱端口缺席：人话文本，且不触碰附件存储 */
    @Test
    public void sandboxAbsentReturnsHumanText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, null)
                .putAttachment("f-docx", null);

        assertTrue(result.startsWith("沙箱未启用"));
        verifyNoInteractions(fileStore);
    }

    /** 存储端口缺席：人话文本，且不触碰沙箱 */
    @Test
    public void fileStoreAbsentReturnsHumanText() {
        AgentSandbox sandbox = mock(AgentSandbox.class);

        String result = new SessionSandboxPutAttachmentTool(SESSION, null, sandbox)
                .putAttachment("f-docx", null);

        assertTrue(result.startsWith("附件能力未启用"));
        verifyNoInteractions(sandbox);
    }

    /** 非法 file_id（含空/路径分隔符/点/超长）统一按"不存在"口径且不回显 */
    @Test
    public void illegalFileIdIsRejectedAsMissingWithoutEcho() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        SessionSandboxPutAttachmentTool tool = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox);

        String[] badIds = {null, "", "   ", "../f1", "f/1", "f 1", "f.1", "f@1", repeat('a', 129)};
        for (String badId : badIds) {
            assertEquals("非法 file_id 统一按不存在口径且不回显: " + badId,
                    "附件不存在或不属于本会话", tool.putAttachment(badId, null));
        }
        verifyNoInteractions(fileStore, sandbox);
    }

    /** 附件不存在（get 返回 null）：人话文本，不读字节、不碰沙箱 */
    @Test
    public void missingAttachmentReturnsHumanTextWithoutTouchingSandbox() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-missing")).thenReturn(null);

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-missing", null);

        assertEquals("附件不存在或不属于本会话", result);
        verify(fileStore, never()).read(any());
        verifyNoInteractions(sandbox);
    }

    /** happy path：缺省落点 input/<原文件名>（name 含路径取末段），原始字节原样透传给端口 */
    @Test
    public void defaultPathUsesOriginalNameAndRawBytesReachPort() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        FileRef ref = new FileRef("f-docx", "dir/监理日志.docx", DOCX_BYTES.length,
                "application/octet-stream", "k-docx");
        when(fileStore.get(SESSION, "f-docx")).thenReturn(ref);
        when(fileStore.read(ref)).thenReturn(DOCX_BYTES);
        when(sandbox.put(SESSION, "input/监理日志.docx", DOCX_BYTES))
                .thenReturn("input/监理日志.docx");

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", null);

        assertEquals("已放入沙箱：input/监理日志.docx（4 字节）。可用 sandbox_run 直接处理（工作目录＝沙箱根）。",
                result);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(sandbox).put(eq(SESSION), eq("input/监理日志.docx"), bytesCaptor.capture());
        assertSame("必须原样透传附件字节（不解析/不转码/不拷贝）", DOCX_BYTES, bytesCaptor.getValue());
    }

    /** R1-I1：path 空白（null/空串/纯空格）＝模型未指定 ⇒ 一律走默认 input/<原文件名>，不判非法 */
    @Test
    public void blankPathFallsBackToDefault() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(SESSION, "input/监理日志.docx", DOCX_BYTES))
                .thenReturn("input/监理日志.docx");

        for (String blank : new String[]{null, "", "   ", "\t"}) {
            String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                    .putAttachment("f-docx", blank);
            assertEquals("path 空白必须按未指定处理（走默认落点）: [" + blank + "]",
                    "已放入沙箱：input/监理日志.docx（4 字节）。可用 sandbox_run 直接处理（工作目录＝沙箱根）。",
                    result);
        }
        verify(sandbox, Mockito.times(4))
                .put(SESSION, "input/监理日志.docx", DOCX_BYTES);
    }

    /** R1-S7：端口违约回 null/空白落点时，工具按请求路径兜底回显（仍给模型可用路径） */
    @Test
    public void blankActualPathFromPortFallsBackToRequestedPath() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(SESSION, "data/copy.docx", DOCX_BYTES))
                .thenReturn(null, "", "   ");

        for (int i = 0; i < 3; i++) {
            String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                    .putAttachment("f-docx", "data/copy.docx");
            assertTrue("实际落点缺失时按请求路径兜底: " + result,
                    result.startsWith("已放入沙箱：data/copy.docx（4 字节）"));
        }
        verify(sandbox, Mockito.times(3)).put(SESSION, "data/copy.docx", DOCX_BYTES);
    }

    /** R1-S7：会话标识缺失 ⇒ 明确人话且不触碰存储/沙箱 */
    @Test
    public void missingSessionIdReturnsHumanText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);

        for (String badSession : new String[]{null, "", "   "}) {
            String result = new SessionSandboxPutAttachmentTool(badSession, fileStore, sandbox)
                    .putAttachment("f-docx", null);
            assertEquals("沙箱未启用：当前会话标识缺失", result);
        }
        verifyNoInteractions(fileStore, sandbox);
    }

    /** 自定义 path：按模型给定值原样落点 */
    @Test
    public void customRelativePathIsUsedVerbatim() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(SESSION, "data/copy.docx", DOCX_BYTES)).thenReturn("data/copy.docx");

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", "data/copy.docx");

        assertTrue(result.startsWith("已放入沙箱：data/copy.docx（4 字节）"));
        verify(sandbox).put(SESSION, "data/copy.docx", DOCX_BYTES);
    }

    /**
     * 非法相对路径统一拒绝，且在触碰存储/沙箱之前短路。R1-S3：空段／纯点号段／冒号与底座
     * {@code safeRelPath} 同尺（不再"先过工具闸、再被底座拒"）；空白（""、空格）不在此列——
     * 那是"未指定 path"，走默认落点（见 {@link #blankPathFallsBackToDefault}）。
     */
    @Test
    public void illegalRelativePathsAreRejectedBeforeTouchingPorts() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        SessionSandboxPutAttachmentTool tool = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox);

        String[] badPaths = {"../x", "a/../../b", "/abs/x", "C:/x", "C:x", "a\\b", "a\nb", "a\u0000b",
                "input/", "a//b", "./x", ".", "..", "foo:bar", "input/./x"};
        for (String badPath : badPaths) {
            String result = tool.putAttachment("f-docx", badPath);
            assertTrue("非法相对路径必须拒绝: " + badPath,
                    result.startsWith("拒绝：path 必须是沙箱内相对路径"));
        }
        verifyNoInteractions(fileStore, sandbox);
    }

    /** 存储读异常：回"稍后重试"人话，不碰沙箱 */
    @Test
    public void readFailureReturnsRetryText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenThrow(new IllegalStateException("oss unavailable"));

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", null);

        assertEquals("附件读取失败，请稍后重试", result);
        verifyNoInteractions(sandbox);
    }

    /** 字节为空/读取返回 null：明确人话，不碰沙箱 */
    @Test
    public void nullOrEmptyAttachmentReturnsExplicitText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);

        when(fileStore.read(DOCX)).thenReturn(null);
        assertEquals("附件为空或读取失败",
                new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                        .putAttachment("f-docx", null));

        when(fileStore.read(DOCX)).thenReturn(new byte[0]);
        assertEquals("附件为空或读取失败",
                new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                        .putAttachment("f-docx", null));

        verifyNoInteractions(sandbox);
    }

    /** 端口同名自动改名后返回实际落点：工具文本必须如实回显实际路径（不是请求路径） */
    @Test
    public void renamedActualPathFromPortIsEchoedVerbatim() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(SESSION, "input/监理日志.docx", DOCX_BYTES))
                .thenReturn("input/监理日志-2.docx");

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", null);

        assertEquals("已放入沙箱：input/监理日志-2.docx（4 字节）。可用 sandbox_run 直接处理（工作目录＝沙箱根）。",
                result);
    }

    /** 端口抛 IllegalArgumentException（路径非法/同名探测超限）⇒ 「拒绝：」＋我方人话 message */
    @Test
    public void portIllegalArgumentBecomesRejectionText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(eq(SESSION), any(String.class), eq(DOCX_BYTES)))
                .thenThrow(new IllegalArgumentException("同名文件过多，请指定其他 path"));

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", null);

        assertEquals("拒绝：同名文件过多，请指定其他 path", result);
    }

    /** 端口其他异常 ⇒ 统一"稍后重试"人话，不透出内部细节 */
    @Test
    public void portFailureReturnsRetryText() {
        AgentFileStore fileStore = mock(AgentFileStore.class);
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(fileStore.get(SESSION, "f-docx")).thenReturn(DOCX);
        when(fileStore.read(DOCX)).thenReturn(DOCX_BYTES);
        when(sandbox.put(eq(SESSION), any(String.class), eq(DOCX_BYTES)))
                .thenThrow(new IllegalStateException("container exploded"));

        String result = new SessionSandboxPutAttachmentTool(SESSION, fileStore, sandbox)
                .putAttachment("f-docx", null);

        assertEquals("放入沙箱失败，请稍后重试", result);
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}