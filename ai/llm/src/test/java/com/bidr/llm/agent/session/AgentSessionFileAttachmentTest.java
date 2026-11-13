package com.bidr.llm.agent.session;

import com.bidr.document.spi.TranscribeStep;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.AbstractToolLoopAgent;
import com.bidr.llm.agent.AgentLoopListener;
import com.bidr.llm.agent.AgentFileView;
import com.bidr.llm.agent.AgentLoopOptions;
import com.bidr.llm.agent.AgentSessionController;
import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.file.SessionAttachmentReadTool;
import com.bidr.llm.agent.file.SessionArtifactGenerateTool;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.bidr.llm.agent.sandbox.SessionSandboxFetchTool;
import com.bidr.llm.agent.sandbox.SessionSandboxRunTool;
import com.bidr.llm.document.FileMarkdownTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Title: AgentSessionFileAttachmentTest
 * Description: P0a 附件协议钉桩（plain JUnit4 + Mockito，不起 Spring 容器）：
 * <ul>
 *     <li><b>能力位</b>：{@code /agents} 的 capabilities 按端口/工具在场推导——存储端口在⇒
 *         files.upload、{@code FileMarkdownTool} Bean 在⇒files.read，都缺席⇒空列表；总开关关⇒空列表；</li>
 *     <li><b>事件</b>：{@code AgentSessionContext.attachFile} 追加 file_attached（payload 键
 *         file_id/name/size/mime，key 不入事件），{@code attachedFiles()} 由事件流重建清单；</li>
 *     <li><b>归属</b>：上传/清单先校验操作人＝会话发起人；未登录 fail-closed、非本人拒绝、
 *         能力未装拒绝、大小/数量上限按配置拦、终态会话拒绝上传、事件写失败不回 200；</li>
 *     <li><b>模型可见上下文</b>：{@code AbstractToolLoopAgent.start} 把附件说明拼在业务
 *         userPrompt 之前——白名单文本给原文、非文本只给 file_id 指令（不再给任意 URL）；
 *         无附件/无存储端口时提示词逐字节不变。</li>
 * </ul>
 * 说明：本模块 surefire 走 JUnit4 provider（junit:junit 在场，未声明 platform provider），
 * 故本类用 JUnit4 断言；写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/13
 */
public class AgentSessionFileAttachmentTest {

    private static final String SESSION = "s-file-1";
    private static final String OWNER = "alice";

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    // ---------------- 能力位 ----------------

    /** 存储端口/文件工具/产物端口/转写腿/沙箱端口都在场时依次出
     *  files.upload/files.read/artifacts.doc/ocr/exec.sandbox，缺席为空 */
    @Test
    public void capabilitiesDerivedFromPortsAndTools() {
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        FileMarkdownTool fileTool = Mockito.mock(FileMarkdownTool.class);
        AgentArtifactGenerator generator = Mockito.mock(AgentArtifactGenerator.class);
        TranscribeStep transcribeStep = Mockito.mock(TranscribeStep.class);
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);

        AgentFileCapabilityResolver all = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(fileTool), providerOf(generator),
                providerOf(transcribeStep), providerOf(sandbox));
        assertEquals(Arrays.asList(AgentFileCapabilityResolver.FILES_UPLOAD,
                AgentFileCapabilityResolver.FILES_READ, AgentFileCapabilityResolver.ARTIFACTS_DOC,
                AgentFileCapabilityResolver.OCR, AgentFileCapabilityResolver.EXEC_SANDBOX), all.current());

        AgentFileCapabilityResolver artifactsOnlyNoOcrNoSandbox = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(fileTool), providerOf(generator),
                providerOf(null), providerOf(null));
        assertEquals("转写腿/沙箱端口缺席＝不出 ocr/exec.sandbox 位（声明级：Bean 在场才算）", Arrays.asList(
                AgentFileCapabilityResolver.FILES_UPLOAD, AgentFileCapabilityResolver.FILES_READ,
                AgentFileCapabilityResolver.ARTIFACTS_DOC), artifactsOnlyNoOcrNoSandbox.current());

        AgentFileCapabilityResolver sandboxOnly = new AgentFileCapabilityResolver(
                providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(sandbox));
        assertEquals("仅沙箱端口在场＝只出 exec.sandbox 位（棒3a：端口纯声明，无前四位依赖）",
                Collections.singletonList(AgentFileCapabilityResolver.EXEC_SANDBOX), sandboxOnly.current());

        AgentFileCapabilityResolver both = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(fileTool), providerOf(null), providerOf(null),
                providerOf(null));
        assertEquals(Arrays.asList(AgentFileCapabilityResolver.FILES_UPLOAD,
                AgentFileCapabilityResolver.FILES_READ), both.current());

        AgentFileCapabilityResolver uploadOnly = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));
        assertEquals(Collections.singletonList(AgentFileCapabilityResolver.FILES_UPLOAD), uploadOnly.current());

        AgentFileCapabilityResolver none = new AgentFileCapabilityResolver(
                providerOf(null), providerOf(null), providerOf(null), providerOf(null), providerOf(null));
        assertTrue("端口与工具都缺席时能力位必须为空（前端不渲染附件控件）", none.current().isEmpty());
        assertNull(none.fileStore());
    }

    /** I-r2-1：总开关关＝能力位整体回退（端口/工具 Bean 都在场也不报告，含 exec.sandbox）；
     *  存储端口仍可取（上传/清单端点是宿主管理面，注入面由工具面/上下文同键关闭） */
    @Test
    public void disabledSwitchClearsAllFileCapabilities() {
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        FileMarkdownTool fileTool = Mockito.mock(FileMarkdownTool.class);
        AgentArtifactGenerator generator = Mockito.mock(AgentArtifactGenerator.class);
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);
        AgentFileCapabilityResolver resolver = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(fileTool), providerOf(generator), providerOf(null),
                providerOf(sandbox));
        ReflectionTestUtils.setField(resolver, "fileToolsEnabled", false);

        assertTrue("总开关关＝/agents 不得报告任何能力位（含 artifacts.doc/exec.sandbox）",
                resolver.current().isEmpty());
        assertEquals("上传/清单端点仍可用（不属于模型可见性）", fileStore, resolver.fileStore());
    }

    /** /agents 注册表条目携带能力位（autonomous 型同口径） */
    @Test
    public void agentSummaryCarriesCapabilities() {
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        AgentSessionController controller = controller(OWNER, fileStore, Mockito.mock(FileMarkdownTool.class),
                service(definitions()));
        List<AgentSessionController.AgentSummary> agents = controller.listAgents();
        assertEquals(1, agents.size());
        assertEquals(Arrays.asList(AgentFileCapabilityResolver.FILES_UPLOAD,
                AgentFileCapabilityResolver.FILES_READ), agents.get(0).getCapabilities());
    }

    // ---------------- 事件与清单读取口 ----------------

    /** attachFile：事件 payload 键契约固定，key 不入事件；attachedFiles 由事件重建 */
    @Test
    public void attachFileEmitsContractPayloadAndRebuildsList() {
        seedSession(OWNER);
        AgentSessionContext ctx = new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
        FileRef ref = new FileRef("f1", "示例.txt", 12L, "text/plain", "agent-session/1/s/f1.txt");

        long seq = ctx.attachFile(ref);

        assertTrue(seq > 0);
        List<AgentEvent> events = store.events(SESSION, 0);
        AgentEvent attached = events.get(events.size() - 1);
        assertEquals(AgentEvent.FILE_ATTACHED, attached.getType());
        Map<?, ?> payload = (Map<?, ?>) attached.getPayload();
        assertEquals("f1", payload.get("file_id"));
        assertEquals("示例.txt", payload.get("name"));
        assertEquals(12L, payload.get("size"));
        assertEquals("text/plain", payload.get("mime"));
        assertEquals("payload 只许 file_id/name/size/mime 四键", 4, payload.size());
        assertTrue("存储键不得进事件流", !payload.containsKey("key"));

        List<FileRef> files = ctx.attachedFiles();
        assertEquals(1, files.size());
        assertEquals("f1", files.get(0).fileId());
        assertEquals("示例.txt", files.get(0).name());
        assertEquals(12L, files.get(0).size());
        assertEquals("text/plain", files.get(0).mime());
        assertNull("事件重建的引用 key 为 null 属契约形态", files.get(0).key());
    }

    /** attachFile 空引用拒绝（入参错误，不进事件流） */
    @Test
    public void attachFileRejectsNull() {
        seedSession(OWNER);
        AgentSessionContext ctx = new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
        assertThrows(IllegalArgumentException.class, () -> ctx.attachFile(null));
    }

    // ---------------- 上传端点：归属与上限 ----------------

    /** 归属：未登录（operator 解析为空）即 fail-closed，不回落 anonymous */
    @Test
    public void missingOperatorIsRejected() {
        seedSession(OWNER);
        AgentSessionController controller = controller(null, Mockito.mock(AgentFileStore.class),
                null, service(Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        ServiceException upload = assertThrows(ServiceException.class,
                () -> controller.uploadFile(SESSION, file));
        assertTrue(upload.getMessage().contains("未登录，无法操作会话附件"));
        ServiceException list = assertThrows(ServiceException.class, () -> controller.listFiles(SESSION));
        assertTrue(list.getMessage().contains("未登录，无法操作会话附件"));
    }

    /** 归属：非发起人上传/清单均拒绝（ServiceException 人话拒绝） */
    @Test
    public void nonOwnerIsRejected() {
        seedSession(OWNER);
        AgentSessionController controller = controller("bob", Mockito.mock(AgentFileStore.class),
                null, service(Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        ServiceException upload = assertThrows(ServiceException.class,
                () -> controller.uploadFile(SESSION, file));
        assertTrue(upload.getMessage().contains("无权操作该会话"));
        ServiceException list = assertThrows(ServiceException.class, () -> controller.listFiles(SESSION));
        assertTrue(list.getMessage().contains("无权操作该会话"));
    }

    /** 能力未装配：本人上传报未启用，清单回退事件重建（不 500） */
    @Test
    public void uploadWithoutStoreIsRejectedAndListStillWorks() {
        seedSession(OWNER);
        AgentSessionController controller = controller(OWNER, null, null, service(Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        ServiceException e = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, file));
        assertTrue(e.getMessage().contains("附件能力未启用"));
        assertTrue(controller.listFiles(SESSION).isEmpty());
    }

    /** 上限：超过 max-size-mb 与 max-per-session 均拒绝 */
    @Test
    public void sizeAndCountLimitsAreEnforced() {
        seedSession(OWNER);
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        when(fileStore.put(anyString(), anyString(), any(), any(byte[].class)))
                .thenAnswer(invocation -> new FileRef("f-" + System.nanoTime(), invocation.getArgument(1),
                        ((byte[]) invocation.getArgument(3)).length, invocation.getArgument(2), "k"));
        when(fileStore.get(anyString(), anyString())).thenReturn(null);

        AgentSessionController controller = controller(OWNER, fileStore, null, service(Collections.emptyList()));
        ReflectionTestUtils.setField(controller, "maxFileSizeMb", 1L);
        ReflectionTestUtils.setField(controller, "maxFilesPerSession", 1);

        MockMultipartFile tooBig = new MockMultipartFile("file", "big.bin", "application/octet-stream",
                new byte[1024 * 1024 + 1]);
        ServiceException size = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, tooBig));
        assertTrue(size.getMessage().contains("文件超过上限 1MB"));

        MockMultipartFile ok = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());
        assertNotNull(controller.uploadFile(SESSION, ok));
        ServiceException count = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, ok));
        assertTrue(count.getMessage().contains("附件数量已达上限 1"));
    }

    /** 终态会话拒绝上传（m-3）：run 线程已结束，写事件也不会再注入 */
    @Test
    public void terminalSessionUploadIsRejected() {
        seedSession(OWNER);
        store.getState(SESSION).setStatus(AgentSessionState.FINISHED);
        AgentSessionController controller = controller(OWNER, Mockito.mock(AgentFileStore.class),
                null, service(Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        ServiceException e = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, file));
        assertTrue(e.getMessage().contains("会话已结束"));
        assertTrue("终态上传不得落事件", controller.listFiles(SESSION).isEmpty());
    }

    /** 事件写入失败（attachFile 返回 0）不得静默 200（I-2） */
    @Test
    public void uploadFailsWhenAttachEventCannotBeWritten() {
        AgentSessionService mockedService = Mockito.mock(AgentSessionService.class);
        when(mockedService.status(SESSION)).thenReturn(runningState(OWNER));
        when(mockedService.attachFile(eq(SESSION), any(FileRef.class))).thenReturn(0L);

        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        FileRef stored = new FileRef("f-event-fail", "a.txt", 2L, "text/plain", "k");
        when(fileStore.put(anyString(), anyString(), any(), any(byte[].class))).thenReturn(stored);
        AgentSessionController controller = controller(OWNER, fileStore, null, mockedService);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());
        ServiceException e = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, file));
        assertTrue(e.getMessage().contains("事件写入失败"));
    }

    /** m-r2-2：存储写入失败（如元数据侧车失败）⇒ 上传端点人话错误、不落事件（不得 200） */
    @Test
    public void uploadFailsWhenStorePutThrows() {
        seedSession(OWNER);
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        when(fileStore.put(anyString(), anyString(), any(), any(byte[].class)))
                .thenThrow(new IllegalStateException("sidecar write failed"));
        AgentSessionController controller = controller(OWNER, fileStore, null, service(Collections.emptyList()));

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());
        ServiceException e = assertThrows(ServiceException.class, () -> controller.uploadFile(SESSION, file));

        assertTrue("存储失败必须显式失败并给人话错误", e.getMessage().contains("附件保存失败"));
        assertTrue("失败不得落 file_attached 事件", controller.listFiles(SESSION).isEmpty());
    }

    /** 成功链：本人上传→落事件→清单回显元数据；HTTP 视图不含存储 key（m-r2-7） */
    @Test
    public void ownerUploadEmitsEventAndListReturnsViewWithoutKey() throws Exception {
        seedSession(OWNER);
        AgentFileStore fileStore = Mockito.mock(AgentFileStore.class);
        FileRef stored = new FileRef("f-1", "a.txt", 2L, "text/plain", "agent-session/1/s/file-1/f-1.txt");
        when(fileStore.put(eq(SESSION), eq("a.txt"), eq("text/plain"), any(byte[].class))).thenReturn(stored);
        when(fileStore.get(SESSION, "f-1")).thenReturn(stored);

        AgentSessionController controller = controller(OWNER, fileStore, null, service(Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());
        AgentFileView returned = controller.uploadFile(SESSION, file);
        assertEquals(new AgentFileView("f-1", "a.txt", 2L, "text/plain"), returned);

        List<AgentEvent> events = store.events(SESSION, 0);
        assertEquals(AgentEvent.FILE_ATTACHED, events.get(events.size() - 1).getType());

        List<AgentFileView> files = controller.listFiles(SESSION);
        assertEquals(1, files.size());
        assertEquals("清单回显四字段元数据", new AgentFileView("f-1", "a.txt", 2L, "text/plain"), files.get(0));

        String json = new ObjectMapper().writeValueAsString(returned);
        assertFalse("HTTP 响应不得回传存储 key", json.contains("agent-session/1/s/file-1/f-1.txt"));
        assertFalse("HTTP 响应不得含 key 字段", json.contains("\"key\""));
    }

    // ---------------- 模型可见上下文（AbstractToolLoopAgent 附件前置注入） ----------------

    /** 附件上下文：小文本附件按原文注入业务 userPrompt 之前 */
    @Test
    public void smallTextAttachmentIsInlinedBeforeBusinessPrompt() throws Exception {
        seedSession(OWNER);
        String content = "项目进展：今日完成桩基 12 根。";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        fileStore.seed(new FileRef("f-text", "日志.txt", bytes.length, "text/plain",
                "agent-session/1/s/f-text.txt"), bytes);
        attachEvent("f-text", "日志.txt", bytes.length, "text/plain");

        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "【任务】业务提示词").start(ctx(fileStore), Collections.emptyMap());

        String prompt = model.firstUserText();
        assertTrue("原文围栏块必须拼在业务提示词之前",
                prompt.startsWith("【附件 日志.txt（" + bytes.length + " 字节）原文】\n```\n" + content + "\n```\n\n"));
        assertTrue(prompt.endsWith("【任务】业务提示词"));
        assertEquals(Collections.singletonList("f-text"), fileStore.readCalls);
        assertTrue("小文本直接给原文，不再签读址", fileStore.presignCalls.isEmpty());
    }

    /** 附件上下文：非文本（docx）附件只给 file_id 指令（不给可被模型复用的任意 URL） */
    @Test
    public void binaryAttachmentGivesFileIdInsteadOfPresignedUrl() throws Exception {
        seedSession(OWNER);
        byte[] bytes = new byte[]{1, 2, 3, 4};
        String docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        InMemoryFileStore fileStore = new InMemoryFileStore();
        fileStore.seed(new FileRef("f-docx", "监理日志.docx", bytes.length, docxMime,
                "agent-session/1/s/f-docx.docx"), bytes);
        attachEvent("f-docx", "监理日志.docx", bytes.length, docxMime);

        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "业务提示词").start(ctx(fileStore), Collections.emptyMap());

        String prompt = model.firstUserText();
        assertTrue("非文本附件必须给模型 file_id 指令", prompt.contains("【附件 监理日志.docx（" + bytes.length
                + " 字节），file_id=f-docx：请用 read_attachment 读取】"));
        assertFalse("提示词不得携带 http 链接（C-1：任意 URL 不能成为工具入参）", prompt.contains("http"));
        assertFalse("二进制内容不得注入提示词", prompt.contains(new String(bytes, StandardCharsets.ISO_8859_1)));
        assertTrue("非文本不读正文，只签读址做内部校验", fileStore.readCalls.isEmpty());
        assertEquals(Collections.singletonList("f-docx"), fileStore.presignCalls);
    }

    /** I-r2-1：总开关关 + 有附件 ⇒ 首轮提示词与无附件版本逐字节相等（不读附件、不拼前缀、工具面空） */
    @Test
    public void disabledSwitchKeepsPromptByteIdenticalEvenWithAttachments() throws Exception {
        seedSession(OWNER);
        byte[] bytes = "开关关闭时不得进入提示词的正文".getBytes(StandardCharsets.UTF_8);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        fileStore.seed(new FileRef("f-off", "a.txt", bytes.length, "text/plain", "k"), bytes);
        attachEvent("f-off", "a.txt", bytes.length, "text/plain");

        RecordingModel model = new RecordingModel();
        PromptCapturingAgent agent = new PromptCapturingAgent(model, "【任务】原样提示词");
        ReflectionTestUtils.setField(agent, "fileToolsEnabled", false);
        agent.start(ctx(fileStore), Collections.emptyMap());

        assertEquals("开关关：提示词必须与无附件版本逐字节相等", "【任务】原样提示词", model.firstUserText());
        assertTrue("开关关不得读取附件正文", fileStore.readCalls.isEmpty());
        assertTrue("开关关不得签发读址", fileStore.presignCalls.isEmpty());
        assertTrue("开关关工具面必须为空", agent.frameworkToolsForTest(ctx(fileStore)).isEmpty());
    }

    /** I-r2-1：总开关关 ⇒ 逐轮注入返回 null（同一配置键，非 Spring 托管对象经 Environment 读取） */
    @Test
    public void disabledSwitchSuppressesPendingUserText() {
        seedSession(OWNER);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        AgentSessionContext ctx = ctxWithProperty(fileStore, "llm.agent.file-tools.enabled", "false");
        // 模拟首轮已建立扫描位点（真实场景由 attachmentContext 完成；配置运行期不变，此处仅为
        // 让断言命中"开关闸"而不是"位点闸"）
        ctx.attachedFiles();

        byte[] bytes = "迟到上传的正文".getBytes(StandardCharsets.UTF_8);
        fileStore.seed(new FileRef("f-off-late", "late.txt", bytes.length, "text/plain", "k"), bytes);
        attachEvent("f-off-late", "late.txt", bytes.length, "text/plain");

        assertNull("开关关：逐轮附件上下文不得注入", ctx.pendingUserText());
    }

    /** 附件上下文：无附件时提示词逐字节不变 */
    @Test
    public void noAttachmentLeavesPromptByteIdentical() throws Exception {
        seedSession(OWNER);
        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "【任务】原样提示词").start(ctx(new InMemoryFileStore()), Collections.emptyMap());
        assertEquals("【任务】原样提示词", model.firstUserText());
    }

    /** 附件上下文：有附件但存储端口缺席时提示词逐字节不变（不报错） */
    @Test
    public void missingStoreLeavesPromptByteIdentical() throws Exception {
        seedSession(OWNER);
        attachEvent("f-1", "a.txt", 2L, "text/plain");
        // BeanUtil 未设置应用上下文 ⇒ getBean(AgentFileStore.class) 返回 null
        BeanUtil.setContext(null);
        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "【任务】原样提示词")
                .start(new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap()),
                        Collections.emptyMap());
        assertEquals("【任务】原样提示词", model.firstUserText());
    }

    // ---------------- 运行期逐轮附件注入（先 start 后 upload 的真实时序） ----------------

    /**
     * 真实时序：先 start（会话内无附件）→ 再上传 → 第一次 pendingUserText 返回新增附件块，
     * 第二次返回 null（同一附件不重复注入）。小文本按原文直出。
     */
    @Test
    public void pendingUserTextInjectsLateAttachmentOnce() throws Exception {
        seedSession(OWNER);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        AgentSessionContext ctx = ctx(fileStore);
        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "业务提示词").start(ctx, Collections.emptyMap());
        assertEquals("业务提示词", model.firstUserText());
        assertNull("无附件时首轮不追加", ctx.pendingUserText());

        String content = "迟到上传的日志正文";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        fileStore.seed(new FileRef("f-late", "迟到的日志.txt", bytes.length, "text/plain",
                "agent-session/1/s/f-late.txt"), bytes);
        attachEvent("f-late", "迟到的日志.txt", bytes.length, "text/plain");

        String pending = ctx.pendingUserText();
        assertNotNull("start 之后上传的附件必须在下一轮可见", pending);
        assertTrue("文本小件直注原文", pending.contains("【附件 迟到的日志.txt（" + bytes.length
                + " 字节）原文】\n```\n" + content + "\n```"));
        assertEquals("文本小件不再签读址", Collections.emptyList(), fileStore.presignCalls);
        assertNull("同一附件不得重复注入", ctx.pendingUserText());
    }

    /** 运行期新增的非文本附件（docx）只给 file_id 指令，不注入二进制/URL */
    @Test
    public void pendingUserTextGivesFileIdForBinaryLateAttachment() throws Exception {
        seedSession(OWNER);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        AgentSessionContext ctx = ctx(fileStore);
        new PromptCapturingAgent(new RecordingModel(), "业务提示词").start(ctx, Collections.emptyMap());

        byte[] bytes = new byte[]{0x50, 0x4B, 0x03, 0x04};
        String docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        fileStore.seed(new FileRef("f-docx-late", "监理日志.docx", bytes.length, docxMime,
                "agent-session/1/s/f-docx-late.docx"), bytes);
        attachEvent("f-docx-late", "监理日志.docx", bytes.length, docxMime);

        String pending = ctx.pendingUserText();
        assertNotNull(pending);
        assertTrue("非文本附件必须给 file_id 指令", pending.contains("【附件 监理日志.docx（" + bytes.length
                + " 字节），file_id=f-docx-late：请用 read_attachment 读取】"));
        assertFalse("运行期注入同样不得携带 http 链接", pending.contains("http"));
        assertTrue("非文本不读正文", fileStore.readCalls.isEmpty());
    }

    /** 首轮已拼进提示词的附件要记投递：下一轮 pendingUserText 必须返回 null（防重复注入） */
    @Test
    public void startInjectedAttachmentsAreNotInjectedAgain() throws Exception {
        seedSession(OWNER);
        String content = "开局就在会话里的文本";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        fileStore.seed(new FileRef("f-start", "开局.txt", bytes.length, "text/plain",
                "agent-session/1/s/f-start.txt"), bytes);
        attachEvent("f-start", "开局.txt", bytes.length, "text/plain");

        AgentSessionContext ctx = ctx(fileStore);
        RecordingModel model = new RecordingModel();
        new PromptCapturingAgent(model, "业务提示词").start(ctx, Collections.emptyMap());
        assertTrue("首轮提示词含附件原文", model.firstUserText().contains(content));

        assertNull("首轮已注入的附件不得在后续轮次重复追加", ctx.pendingUserText());
    }

    /** 无附件 / 存储端口缺席：pendingUserText 返回 null（不抛、不追加消息） */
    @Test
    public void pendingUserTextIsNullWithoutStoreOrAttachments() {
        seedSession(OWNER);
        AgentSessionContext ctx = new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
        BeanUtil.setContext(null);
        assertNull("无附件返回 null", ctx.pendingUserText());

        attachEvent("f-no-store", "a.txt", 2L, "text/plain");
        assertNull("存储端口缺席返回 null", ctx.pendingUserText());
    }

    /** 框架工具面收窄（C-1/P2a/棒3a）：默认只挂四只会话受限工具——read_attachment＋
     *  generate_artifact＋sandbox_run＋sandbox_fetch，不再挂 raw read_file；
     *  总开关关＝空（sandbox 两只同退，与 disabledSwitchKeepsPromptByteIdentical… 的
     *  「工具面集合为空」断言同键同口径） */
    @Test
    public void frameworkToolsExposeSessionScopedReadAttachmentOnly() {
        seedSession(OWNER);
        AgentSessionContext ctx = new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
        PromptCapturingAgent agent = new PromptCapturingAgent(new RecordingModel(), "业务提示词");

        List<Object> enabled = agent.frameworkToolsForTest(ctx);
        assertEquals("默认框架工具面应为附件读取＋产物生成＋沙箱两只共四只", 4, enabled.size());
        assertTrue("第一只框架工具必须是 SessionAttachmentReadTool",
                enabled.get(0) instanceof SessionAttachmentReadTool);
        assertTrue("第二只框架工具必须是 SessionArtifactGenerateTool（P2a）",
                enabled.get(1) instanceof SessionArtifactGenerateTool);
        assertTrue("第三只框架工具必须是 SessionSandboxRunTool（棒3a）",
                enabled.get(2) instanceof SessionSandboxRunTool);
        assertTrue("第四只框架工具必须是 SessionSandboxFetchTool（棒3a）",
                enabled.get(3) instanceof SessionSandboxFetchTool);
        for (Object tool : enabled) {
            assertFalse("raw read_file 不得默认并入（C-1）", tool instanceof FileMarkdownTool);
        }

        ReflectionTestUtils.setField(agent, "fileToolsEnabled", false);
        List<Object> disabled = agent.frameworkToolsForTest(ctx);
        assertTrue("总开关关闭时框架工具面必须为空（含 sandbox 两只同退）", disabled.isEmpty());
        for (Object tool : disabled) {
            assertFalse("开关关后不得残留 sandbox_run", tool instanceof SessionSandboxRunTool);
            assertFalse("开关关后不得残留 sandbox_fetch", tool instanceof SessionSandboxFetchTool);
        }
    }

    /** 引擎接线：会话 listener 必须把 pendingUserText 委派给上下文实现（漏接＝逐轮注入整体失效，
     *  e2e 首轮实测暴露过的接线缺口，此处钉死） */
    @Test
    public void loopListenerDelegatesPendingUserText() {
        seedSession(OWNER);
        InMemoryFileStore fileStore = new InMemoryFileStore();
        AgentSessionContext ctx = ctx(fileStore);
        // 首轮清单扫描建立增量位点（真实链路由 attachmentContext 完成）
        ctx.attachedFiles();
        assertNull("无新增时 listener 返回 null", ctx.loopListener().pendingUserText());

        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        fileStore.seed(new FileRef("f-wire", "w.txt", bytes.length, "text/plain", "k"), bytes);
        attachEvent("f-wire", "w.txt", bytes.length, "text/plain");

        String pending = ctx.loopListener().pendingUserText();
        assertNotNull("会话 listener 必须委派到 AgentSessionContext.pendingUserText", pending);
        assertTrue(pending.contains("w.txt"));
        assertNull("同一附件不重复", ctx.loopListener().pendingUserText());
    }

    /** 引擎接线：本轮 pendingUserText 非空时作为用户消息追加在业务提示词之后、LLM 调用之前 */
    @Test
    public void runnerAppendsPendingUserTextBeforeLlmCall() {
        RecordingModel model = new RecordingModel();
        AtomicInteger rounds = new AtomicInteger();
        AgentLoopListener listener = new AgentLoopListener() {
            @Override
            public void log(String line) {
            }

            @Override
            public boolean shouldStop() {
                return false;
            }

            @Override
            public String pendingUserText() {
                return rounds.getAndIncrement() == 0 ? "【附件 迟到.txt（5 字节），file_id=f-late：请用 read_attachment 读取】" : null;
            }
        };

        new ToolAgentRunner().run(model, null, "业务提示词", Collections.emptyList(),
                new AgentLoopOptions(), listener);

        List<ChatMessage> first = model.firstRequest();
        assertEquals("无 system 槽时首条是业务提示词、次条是附件追加", 2, first.size());
        assertEquals("业务提示词", ((UserMessage) first.get(0)).singleText());
        assertEquals("【附件 迟到.txt（5 字节），file_id=f-late：请用 read_attachment 读取】",
                ((UserMessage) first.get(1)).singleText());
    }

    @After
    public void clearBeanUtilSeam() {
        BeanUtil.setContext(null);
    }

    /** 向事件流追加 file_attached（模拟上传端点已落事件；模型注入只认事件正源） */
    private void attachEvent(String fileId, String name, long size, String mime) {
        new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap())
                .attachFile(new FileRef(fileId, name, size, mime, null));
    }

    /** 已装上下文 + fake 存储端口的会话 ctx（BeanUtil 缝：getBean(AgentFileStore.class) 返回 fake） */
    private AgentSessionContext ctx(InMemoryFileStore fileStore) {
        WebApplicationContext applicationContext = Mockito.mock(WebApplicationContext.class);
        when(applicationContext.getBean(AgentFileStore.class)).thenReturn(fileStore);
        BeanUtil.setContext(applicationContext);
        return new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
    }

    /** 同上 + 指定配置键值（模拟 application.yml 的 llm.agent.file-tools.enabled=false） */
    private AgentSessionContext ctxWithProperty(InMemoryFileStore fileStore, String key, String value) {
        WebApplicationContext applicationContext = Mockito.mock(WebApplicationContext.class);
        when(applicationContext.getBean(AgentFileStore.class)).thenReturn(fileStore);
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getProperty(key)).thenReturn(value);
        when(applicationContext.getEnvironment()).thenReturn(environment);
        BeanUtil.setContext(applicationContext);
        return new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
    }

    /** 内存 AgentFileStore：get 还原完整引用，read/presign 记账 */
    private static final class InMemoryFileStore implements AgentFileStore {

        static final String URL_PREFIX = "https://oss.example/";

        final Map<String, FileRef> refs = new HashMap<>();

        final Map<String, byte[]> bytes = new HashMap<>();

        final List<String> readCalls = new ArrayList<>();

        final List<String> presignCalls = new ArrayList<>();

        void seed(FileRef ref, byte[] content) {
            refs.put(ref.fileId(), ref);
            bytes.put(ref.fileId(), content);
        }

        @Override
        public FileRef put(String sessionId, String filename, String mime, byte[] content) {
            throw new UnsupportedOperationException("本测试不走上传");
        }

        @Override
        public FileRef get(String sessionId, String fileId) {
            return refs.get(fileId);
        }

        @Override
        public byte[] read(FileRef ref) {
            readCalls.add(ref.fileId());
            return bytes.get(ref.fileId());
        }

        @Override
        public String presignedUrl(FileRef ref, long ttlSeconds) {
            presignCalls.add(ref.fileId());
            return URL_PREFIX + ref.fileId();
        }
    }

    /** 记录每次请求消息的假模型（首轮即给结论，不触发工具循环） */
    private static final class RecordingModel implements ChatModel {

        private final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(new ArrayList<>(request.messages()));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .tokenUsage(new TokenUsage(1, 1))
                    .build();
        }

        /** 首轮请求里第一条 UserMessage 的文本（= systemPrompt 之后进循环的 userPrompt） */
        String firstUserText() {
            for (ChatMessage message : requests.get(0)) {
                if (message instanceof UserMessage) {
                    UserMessage user = (UserMessage) message;
                    return user.hasSingleText() ? user.singleText() : String.valueOf(user);
                }
            }
            throw new AssertionError("首轮请求没有 UserMessage");
        }

        /** 首轮请求的完整消息序列（逐轮附件追加腿断言用） */
        List<ChatMessage> firstRequest() {
            return requests.get(0);
        }
    }

    /** 最小 AbstractToolLoopAgent 子类：只填四钩子，用来验证 start() 的附件前置注入 */
    private static final class PromptCapturingAgent extends AbstractToolLoopAgent<Void> {

        private final ChatModel model;

        private final String businessPrompt;

        PromptCapturingAgent(ChatModel model, String businessPrompt) {
            this.model = model;
            this.businessPrompt = businessPrompt;
        }

        @Override
        public String agentKey() {
            return "probe";
        }

        @Override
        public String displayName() {
            return "探针";
        }

        @Override
        public String skillCode() {
            return "probe";
        }

        @Override
        protected ChatModel model(Void state, AgentSessionContext ctx, Map<String, Object> payload) {
            return model;
        }

        @Override
        protected String systemPrompt(Void state, AgentSessionContext ctx, Map<String, Object> payload) {
            return "SYS";
        }

        @Override
        protected String userPrompt(Void state, AgentSessionContext ctx, Map<String, Object> payload) {
            return businessPrompt;
        }

        @Override
        protected List<Object> tools(Void state, AgentSessionContext ctx, Map<String, Object> payload) {
            return Collections.emptyList();
        }

        /** 暴露 protected 钩子供 C-1 工具面收窄断言 */
        List<Object> frameworkToolsForTest(AgentSessionContext ctx) {
            return frameworkTools(null, ctx, Collections.emptyMap());
        }
    }

    // ---------------- fixture ----------------

    private void seedSession(String operator) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(SESSION);
        state.setAgentKey("probe");
        state.setOperator(operator);
        state.setStatus(AgentSessionState.RUNNING);
        state.setStartedAt(System.currentTimeMillis());
        state.setHeartbeat(System.currentTimeMillis());
        store.saveState(state);
        // 真实链路 runSession 在 definition.start 前先落 RUN_START：逐轮附件扫描的初始位点
        // 因此恒 >=1（pendingUserText 的 attachmentScanSeq<=0 短路以该前提为契约）
        store.appendEvent(SESSION, AgentEvent.RUN_START, Collections.emptyMap());
    }

    /** 未落 store 的 RUNNING 快照（mock 服务腿用） */
    private static AgentSessionState runningState(String operator) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(SESSION);
        state.setAgentKey("probe");
        state.setOperator(operator);
        state.setStatus(AgentSessionState.RUNNING);
        state.setStartedAt(System.currentTimeMillis());
        state.setHeartbeat(System.currentTimeMillis());
        return state;
    }

    private AgentSessionService service(List<AutonomousAgentDefinition> definitions) {
        return new AgentSessionService(store, definitions, Mockito.mock(ObjectProvider.class));
    }

    private AgentSessionController controller(String operator, AgentFileStore fileStore,
                                              FileMarkdownTool fileTool, AgentSessionService service) {
        AgentFileCapabilityResolver resolver = new AgentFileCapabilityResolver(
                providerOf(fileStore), providerOf(fileTool), providerOf(null), providerOf(null),
                providerOf(null));
        ObjectProvider<OperatorResolver> operatorProvider = providerOf(() -> operator);
        return new AgentSessionController(service, null, null, Collections.emptyList(), operatorProvider,
                null, Mockito.mock(ObjectProvider.class), Collections.emptyList(), resolver);
    }

    /** 已注册 agent 占位（/agents 能力位腿只需要注册表非空） */
    private static List<AutonomousAgentDefinition> definitions() {
        return Collections.singletonList(new AutonomousAgentDefinition() {
            @Override
            public String agentKey() {
                return "probe";
            }

            @Override
            public String displayName() {
                return "探针";
            }

            @Override
            public String skillCode() {
                return "probe";
            }

            @Override
            public void start(AgentSessionContext ctx, Map<String, Object> payload) {
            }
        });
    }

    /** ObjectProvider 桩：getIfUnique/getIfAvailable 返回给定值（解析器已切多 Bean 安全口径） */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(value);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}