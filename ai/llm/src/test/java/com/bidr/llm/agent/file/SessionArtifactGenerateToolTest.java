package com.bidr.llm.agent.file;

import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: SessionArtifactGenerateToolTest
 * Description: 产物生成工具的契约钉桩（plain JUnit4＋Mockito，不起 Spring 容器；llm surefire
 * 走 JUnit4 provider，JUnit5 会被静默跳过）：
 * <ul>
 *     <li><b>注册名</b>：@Tool 显式 name＝{@code generate_artifact}、@P 参数名
 *     content/format/title（能力包登记件按此名对账，手写名漂移＝勾选面静默失配）；</li>
 *     <li><b>降级面</b>：生成端口缺席/会话标识缺失→「产物生成未启用」人话文本；content 空白→拒绝；</li>
 *     <li><b>成功面</b>：返回文本带文件名与下载链接；{@link AgentEvent#ARTIFACT} 事件落事件流，
 *     payload 键契约 file_id/name/size/mime/format/url（snake_case，前端下载卡片数据源）；</li>
 *     <li><b>失败面</b>：非法格式（IllegalArgumentException）回「拒绝：」前缀文案；转换/存储异常
 *     回「产物生成失败」，不向模型暴露内部细节。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
public class SessionArtifactGenerateToolTest {

    private static final String SESSION = "s-artifact-1";

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    // ---------------- 注册名对账 ----------------

    /** @Tool 注册名与 @P 参数名契约（与能力包登记件 artifact 包对账，名字漂移＝登记失配） */
    @Test
    public void toolRegistrationNameAndParamNamesAreContract() throws Exception {
        Method method = SessionArtifactGenerateTool.class.getDeclaredMethod("generateArtifact",
                String.class, String.class, String.class);
        Tool tool = method.getAnnotation(Tool.class);
        assertNotNull("generateArtifact 必须带 @Tool（否则按 camelCase 静默注册成 generateArtifact）", tool);
        assertEquals("generate_artifact", tool.name());

        // @P 是参数级注解（@Target PARAMETER），要从 getParameterAnnotations 取，不在 method 头上
        assertEquals("三个参数", 3, method.getParameterCount());
        String[] expectedNames = {"content", "format", "title"};
        for (int i = 0; i < method.getParameterCount(); i++) {
            P param = null;
            for (java.lang.annotation.Annotation annotation : method.getParameterAnnotations()[i]) {
                if (annotation instanceof P) {
                    param = (P) annotation;
                    break;
                }
            }
            assertNotNull("第 " + i + " 个参数必须带 @P 显式名", param);
            assertEquals(expectedNames[i], param.name());
        }
    }

    // ---------------- 降级面 ----------------

    /** 生成端口缺席（gotenberg 腿/BlobStore 未装配）＝能力未启用，回人话文本不抛 */
    @Test
    public void missingGeneratorDegradesToHumanText() {
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, null, null);
        String result = tool.generateArtifact("# 日志", "docx", null);
        assertTrue("端口缺席必须回「产物生成未启用」：" + result, result.contains("产物生成未启用"));
    }

    /** content 空白拒绝（正文是产物唯一事实源，模型须回填） */
    @Test
    public void blankContentIsRejected() {
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, null, mockGenerator());
        assertTrue(tool.generateArtifact("  ", "docx", null).contains("content 不能为空"));
        assertTrue(tool.generateArtifact(null, "docx", null).contains("content 不能为空"));
    }

    /** 非法格式：实现侧 IllegalArgumentException 以「拒绝：」前缀回给模型（可自行纠正） */
    @Test
    public void invalidFormatIsRejectedWithPrefix() {
        AgentArtifactGenerator generator = mock(AgentArtifactGenerator.class);
        when(generator.generate(anyString(), anyString(), eq("html"), any()))
                .thenThrow(new IllegalArgumentException("暂不支持的产物格式：html（支持 docx/pdf）"));
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, null, generator);
        String result = tool.generateArtifact("# 日志", "html", null);
        assertTrue("非法格式必须带「拒绝：」前缀：" + result, result.startsWith("拒绝："));
        assertTrue(result.contains("暂不支持的产物格式"));
    }

    /** 转换/存储异常：人话错误文本，不向模型暴露内部细节（key/栈/URL 拼接面） */
    @Test
    public void generatorFailureReturnsHumanText() {
        AgentArtifactGenerator generator = mock(AgentArtifactGenerator.class);
        when(generator.generate(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("agent-artifact/s-1/xx 之类内部细节"));
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, null, generator);
        String result = tool.generateArtifact("# 日志", "pdf", null);
        assertTrue("失败必须回「产物生成失败」：" + result, result.contains("产物生成失败"));
        assertTrue("不得把内部细节回给模型", !result.contains("agent-artifact"));
    }

    // ---------------- 成功面（事件契约） ----------------

    /** 成功：返回文本带文件名与下载链接；ARTIFACT 事件落事件流，payload 六键契约 */
    @Test
    public void successReturnsLinkAndEmitsArtifactEvent() {
        seedSession();
        AgentSessionContext ctx = new AgentSessionContext(store.getState(SESSION), store, Collections.emptyMap());
        AgentArtifactGenerator generator = mock(AgentArtifactGenerator.class);
        AgentArtifact artifact = new AgentArtifact("f1", "监理日志-2026-11-13.docx", 2048L,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx", "https://oss.example/download");
        when(generator.generate(eq(SESSION), eq("# 监理日志正文"), eq("docx"), eq("监理日志-2026-11-13")))
                .thenReturn(artifact);
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, ctx, generator);

        String result = tool.generateArtifact("# 监理日志正文", "docx", "监理日志-2026-11-13");

        assertTrue("返回文本必须带文件名：" + result, result.contains("监理日志-2026-11-13.docx"));
        assertTrue("返回文本必须带下载链接：" + result, result.contains("https://oss.example/download"));

        List<AgentEvent> events = store.events(SESSION, 0);
        AgentEvent artifactEvent = events.stream()
                .filter(e -> AgentEvent.ARTIFACT.equals(e.getType()))
                .findFirst().orElse(null);
        assertNotNull("成功必须追加 artifact 事件（前端下载卡片数据源）", artifactEvent);
        assertTrue(artifactEvent.getSeq() > 0);
        assertTrue("payload 必须是 Map（键契约腿）", artifactEvent.getPayload() instanceof Map);
        Map<?, ?> payload = (Map<?, ?>) artifactEvent.getPayload();
        assertEquals("f1", payload.get("file_id"));
        assertEquals("监理日志-2026-11-13.docx", payload.get("name"));
        assertEquals(2048L, payload.get("size"));
        assertEquals("docx", payload.get("format"));
        assertEquals("https://oss.example/download", payload.get("url"));
        assertNotNull("mime 键必须在场", payload.get("mime"));
    }

    /** format 缺省按 docx（工具描述与实现同口径，模型不传也能出件） */
    @Test
    public void blankFormatFallsBackToDocx() {
        AgentArtifactGenerator generator = mock(AgentArtifactGenerator.class);
        when(generator.generate(anyString(), anyString(), eq("docx"), any())).thenReturn(new AgentArtifact(
                "f2", "a.docx", 1L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx", "https://oss.example/a"));
        SessionArtifactGenerateTool tool = new SessionArtifactGenerateTool(SESSION, null, generator);
        String result = tool.generateArtifact("# 日志", " ", null);
        assertTrue(result.contains("文件已生成"));
        Mockito.verify(generator).generate(eq(SESSION), anyString(), eq("docx"), any());
    }

    // ---------------- fixture ----------------

    private void seedSession() {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(SESSION);
        state.setAgentKey("probe");
        state.setOperator("alice");
        state.setStatus(AgentSessionState.RUNNING);
        state.setStartedAt(System.currentTimeMillis());
        state.setHeartbeat(System.currentTimeMillis());
        store.saveState(state);
    }

    private static AgentArtifactGenerator mockGenerator() {
        return mock(AgentArtifactGenerator.class);
    }
}
