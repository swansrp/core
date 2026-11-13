package com.bidr.llm.agent.sandbox;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.session.AgentSessionContext;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: SessionSandboxRunToolTest
 * Description: sandbox_run 工具钉桩（棒3a，plain JUnit4 + Mockito，全 mock 端口无真沙箱）：
 * 端口缺席人话 / 空命令拒绝 / 成功回 exitCode+output / 超时文案 / ensure 传入种子 /
 * 超时限幅（null=120、超 600 封顶）/ 执行异常转人话。
 * <p>
 * 说明：本模块 surefire 走 JUnit4 provider（junit:junit 在场，未声明 platform provider），
 * 故本类用 JUnit4 断言；写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
public class SessionSandboxRunToolTest {

    private static final String SESSION = "s-sbx-1";

    @After
    public void clearBeanUtilSeam() {
        BeanUtil.setContext(null);
    }

    /** 端口缺席（宿主未装配沙箱底座）＝人话文本，不抛 */
    @Test
    public void portAbsentReturnsHumanText() {
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, null, null);
        String result = tool.runCommand("ls -la", null);
        assertEquals("沙箱未启用：当前宿主未装配沙箱服务", result);
    }

    /** 会话标识缺失（ctx 为 null 的任务作用域形态）＝人话文本 */
    @Test
    public void missingSessionIdReturnsHumanText() {
        SessionSandboxRunTool tool = new SessionSandboxRunTool(null, mock(AgentSandbox.class), null);
        assertEquals("沙箱未启用：当前会话标识缺失", tool.runCommand("ls", null));
    }

    /** 空命令拒绝（照「拒绝：」前缀模式，不触碰端口） */
    @Test
    public void blankCommandIsRejectedWithoutTouchingPort() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, sandbox, null);
        assertTrue(tool.runCommand("   ", null).startsWith("拒绝：command 不能为空"));
        verify(sandbox, Mockito.never()).ensure(anyString(), Mockito.anyList());
    }

    /** 成功链：ensure 传入种子清单 → exec → 回 exitCode＋output */
    @Test
    public void successReturnsExitCodeAndOutputWithSeedsEnsured() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(eq(SESSION), eq("python scripts/check.py"), eq(120_000L)))
                .thenReturn(new SandboxExecResult(0, "OK: 3 checks passed", false));
        List<SandboxSeedFile> seeds = Collections.singletonList(
                new SandboxSeedFile("scripts/check.py", "print(1)".getBytes(StandardCharsets.UTF_8)));
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, sandbox, seeds);

        String result = tool.runCommand("python scripts/check.py", null);

        // 种子经构造器原引用传给 ensure（首次建沙箱预置）
        verify(sandbox).ensure(eq(SESSION), same(seeds));
        assertTrue("回文本必须含 exitCode", result.contains("exitCode=0"));
        assertTrue("回文本必须含合并输出", result.contains("OK: 3 checks passed"));
    }

    /** 种子 null 构造形态＝ensure 收到空清单（不预置） */
    @Test
    public void nullSeedsNormalizeToEmptyList() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(anyString(), anyString(), Mockito.anyLong()))
                .thenReturn(new SandboxExecResult(0, "", false));
        new SessionSandboxRunTool(SESSION, sandbox, null).runCommand("ls", null);

        ArgumentCaptor<List<SandboxSeedFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(sandbox).ensure(eq(SESSION), captor.capture());
        assertTrue("null 种子按空清单传给 ensure", captor.getValue().isEmpty());
    }

    /** 超时：timedOut=true 回固定人话文案（带实际生效秒数），不带 output */
    @Test
    public void timedOutReturnsHumanTextWithAppliedSeconds() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(anyString(), anyString(), Mockito.anyLong()))
                .thenReturn(new SandboxExecResult(137, "partial output", true));
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, sandbox, null);

        assertEquals("命令超时（5 秒）已终止，请缩小任务或分步执行",
                tool.runCommand("sleep 600", 5));
    }

    /** 超时限幅：不传=120 秒；超 600 封顶 600 秒（毫秒值传给端口） */
    @Test
    public void timeoutIsClampedToBounds() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(anyString(), anyString(), Mockito.anyLong()))
                .thenReturn(new SandboxExecResult(0, "", false));
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, sandbox, null);

        tool.runCommand("a", null);
        verify(sandbox).exec(eq(SESSION), eq("a"), eq(120_000L));

        tool.runCommand("b", 0);
        verify(sandbox).exec(eq(SESSION), eq("b"), eq(120_000L));

        tool.runCommand("c", 900);
        verify(sandbox).exec(eq(SESSION), eq("c"), eq(600_000L));

        tool.runCommand("d", 30);
        verify(sandbox).exec(eq(SESSION), eq("d"), eq(30_000L));
    }

    /** ensure/exec 抛异常＝人话文本（不向模型抛栈） */
    @Test
    public void portFailureBecomesHumanText() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        Mockito.doThrow(new IllegalStateException("sandbox daemon down"))
                .when(sandbox).ensure(anyString(), Mockito.anyList());
        SessionSandboxRunTool tool = new SessionSandboxRunTool(SESSION, sandbox, null);

        assertEquals("沙箱执行失败（沙箱服务暂不可用），请稍后重试",
                tool.runCommand("ls", null));
    }

    /** 任务作用域构造经 BeanUtil 解析端口：上下文缺席（未设 Spring 上下文）＝端口缺席人话 */
    @Test
    public void publicConstructorWithoutContextYieldsAbsentPort() {
        BeanUtil.setContext(null);
        SessionSandboxRunTool tool = new SessionSandboxRunTool(null, null);
        assertEquals("沙箱未启用：当前宿主未装配沙箱服务", tool.runCommand("ls", null));
    }

    /** 任务作用域构造经 BeanUtil 解析端口：上下文在场＝取到端口、sessionId 取自 ctx */
    @Test
    public void publicConstructorResolvesPortFromBeanUtil() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(anyString(), anyString(), Mockito.anyLong()))
                .thenReturn(new SandboxExecResult(1, "boom", false));
        WebApplicationContext applicationContext = mock(WebApplicationContext.class);
        when(applicationContext.getBean(AgentSandbox.class)).thenReturn(sandbox);
        BeanUtil.setContext(applicationContext);
        AgentSessionContext ctx = mock(AgentSessionContext.class);
        when(ctx.getSessionId()).thenReturn(SESSION);

        SessionSandboxRunTool tool = new SessionSandboxRunTool(ctx, null);
        String result = tool.runCommand("make build", 10);

        assertTrue(result.contains("exitCode=1"));
        assertTrue(result.contains("boom"));
        verify(sandbox).exec(eq(SESSION), eq("make build"), eq(10_000L));
    }

    /** 非零退出码照常回传（模型据此自纠），不算工具失败 */
    @Test
    public void nonZeroExitIsAResultNotAnError() {
        AgentSandbox sandbox = mock(AgentSandbox.class);
        when(sandbox.exec(anyString(), anyString(), Mockito.anyLong()))
                .thenReturn(new SandboxExecResult(2, "usage: ...", false));
        String result = new SessionSandboxRunTool(SESSION, sandbox, null).runCommand("bad-cmd", null);
        assertTrue(result.contains("exitCode=2"));
        assertFalse(result.contains("沙箱执行失败"));
    }

    /** 常量契约：工具名/缺省超时/上限（console 能力包与 e2e 按此核对） */
    @Test
    public void toolNameAndTimeoutConstants() {
        assertEquals("sandbox_run", SessionSandboxRunTool.TOOL_NAME);
        assertEquals(120, SessionSandboxRunTool.DEFAULT_TIMEOUT_SECONDS);
        assertEquals(600, SessionSandboxRunTool.MAX_TIMEOUT_SECONDS);
    }
}
