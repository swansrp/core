package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.bidr.llm.agent.ScriptedModel.Step;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bidr.llm.agent.ScriptedModel.Step.done;
import static com.bidr.llm.agent.ScriptedModel.Step.tool;
import static com.bidr.llm.agent.ScriptedModel.Step.tools;

/**
 * Title: ToolAgentRunnerOffloadTest
 * Description: 入场卸载 + 句柄回捞端到端测试（计划 §6.4，层 1 合成夹具零外部依赖）：
 * ScriptedModel 假模型每轮对入窗消息执行 I1 配对自检（脚本含「一个 Ai 调 3 个工具、其中一个
 * 结果巨大」的并行形态），锁死卸载/回捞与 pinnedTools、cachedTools、failureBreaker、收口路径
 * 的全量交互矩阵
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ToolAgentRunnerOffloadTest {

    /** 会话链夹具：真实 AgentSessionContext + 内存 store（事件流持全文=回捞源，I4/I5 形态齐全） */
    private static final class Fix {
        final AgentSessionContext ctx;
        final InMemoryAgentSessionStore store;

        Fix(String sessionId) {
            AgentSessionState state = new AgentSessionState();
            state.setSessionId(sessionId);
            state.setAgentKey("probe");
            state.setStatus(AgentSessionState.RUNNING);
            store = new InMemoryAgentSessionStore();
            store.saveState(state);
            ctx = new AgentSessionContext(state, store, new HashMap<>());
        }
    }

    /** 快速 askUser 替身（不阻塞等人）：answer 可注入超长文本验 I6/I10 */
    public static class FakeAskUserTool {
        private final String answer;

        FakeAskUserTool(String answer) {
            this.answer = answer;
        }

        @Tool("向用户提问")
        public String askUser(String question, String optionsJson) {
            return answer;
        }
    }

    public static class EchoTool {
        @Tool("echo")
        public String echo(String text) {
            return "echo:" + text;
        }
    }

    private static AgentLoopOptions opts(int rounds, int window, Integer offload) {
        AgentLoopOptions o = new AgentLoopOptions(rounds, window);
        if (offload != null) {
            o.setToolResultOffloadChars(offload);
        }
        return o;
    }

    private static String resultTextOf(List<ChatMessage> messages, String callId) {
        for (ChatMessage m : messages) {
            if (m instanceof ToolExecutionResultMessage && callId.equals(((ToolExecutionResultMessage) m).id())) {
                return ((ToolExecutionResultMessage) m).text();
            }
        }
        return null;
    }

    private static ScriptedModel scripted(Step... steps) {
        return new ScriptedModel(Arrays.asList(steps));
    }

    private static List<Step> probeSteps(Step... steps) {
        return Arrays.asList(steps);
    }

    private static String bigArgs(String topic) {
        return "{\"topic\":\"" + topic + "\"}";
    }

    private static String recallArgs(String id) {
        return "{\"toolCallId\":\"" + id + "\"}";
    }

    /** 大结果入场为指针且原文不进窗（I2 只换文本；I4 全文进事件流） */
    @Test
    public void 大结果入场为指针且原文不进窗() {
        Fix f = new Fix("s-off-1");
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")), done("结论"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Collections.<Object>singletonList(new ContextProbeTools()), opts(5, 20, 4000), f.ctx.loopListener());
        Assert.assertNotNull(r);

        List<ChatMessage> round2 = model.views.get(1).messages;
        String entry = resultTextOf(round2, "call-1");
        Assert.assertNotNull(entry);
        Assert.assertTrue("超长结果入场应为指针", ToolResultOffloader.isPointer(entry));
        Assert.assertEquals("call-1", ToolResultOffloader.handleOf(entry));
        Assert.assertEquals("KEY-A-8888 只应存在于原文，不得泄漏进指针预览", -1, entry.indexOf("KEY-A-8888"));
        // I4：事件流仍持原文全文
        Assert.assertEquals(ContextProbeTools.DEFAULT_REPORT_CHARS, eventOutput(f, "call-1").length());
        Assert.assertTrue(eventOutput(f, "call-1").contains("KEY-A-8888"));
    }

    private static String eventOutput(Fix f, String callId) {
        String found = null;
        for (AgentEvent ev : f.store.events(f.ctx.getSessionId(), 0)) {
            if (AgentEvent.TOOL_RESULT.equals(ev.getType()) && ev.getPayload() instanceof Map) {
                Map<?, ?> p = (Map<?, ?>) ev.getPayload();
                if (callId.equals(p.get("tool_call_id"))) {
                    found = String.valueOf(p.get("output"));
                }
            }
        }
        Assert.assertNotNull("事件流应有 " + callId + " 的 TOOL_RESULT", found);
        return found;
    }

    /** 入窗总字符数改造后显著下降（峰值 < 基线 40%）——层 1 对照数字表核心判据 */
    @Test
    public void 入窗总字符数改造后显著下降() {
        List<Step> script = probeSteps(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "bigReport", bigArgs("B")),
                tool("call-3", "bigReport", bigArgs("C")),
                done("输出三处 KEY"));
        ScriptedModel base = new ScriptedModel(script);
        new ToolAgentRunner().run(base, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opts(6, 20, null), new Fix("s-off-2a").ctx.loopListener());
        ScriptedModel off = new ScriptedModel(script);
        new ToolAgentRunner().run(off, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opts(6, 20, 4000), new Fix("s-off-2b").ctx.loopListener());
        int peakBase = peakChars(base);
        int peakOff = peakChars(off);
        System.out.println("[层1对照] peakChars 基线=" + peakBase + " 卸载后=" + peakOff
                + " 比值=" + String.format("%.3f", peakOff * 1.0 / peakBase));
        Assert.assertTrue("入窗峰值应降至基线 40% 以下: " + peakOff + " vs " + peakBase,
                peakOff < peakBase * 0.4);
    }

    private static int peakChars(ScriptedModel m) {
        int peak = 0;
        for (ScriptedModel.RoundView v : m.views) {
            peak = Math.max(peak, v.chars);
        }
        return peak;
    }

    private static int peakEst(ScriptedModel m) {
        int peak = 0;
        for (ScriptedModel.RoundView v : m.views) {
            peak = Math.max(peak, v.estTokens);
        }
        return peak;
    }

    /** I6：钉住工具（pinnedTools 命中）结果不被卸载；askUser/recallToolResult 名单核验 */
    @Test
    public void 钉住工具结果不被卸载() {
        Assert.assertEquals("recallToolResult", AgentToolRecall.TOOL_NAME);
        Fix f = new Fix("s-off-3");
        AgentLoopOptions opt = opts(5, 20, 4000);
        opt.setPinnedTools(Collections.singleton("bigReport"));
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")), done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opt, f.ctx.loopListener());
        String entry = resultTextOf(model.views.get(1).messages, "call-1");
        Assert.assertFalse("钉住工具结果必须原文入窗", ToolResultOffloader.isPointer(entry));
        Assert.assertEquals(ContextProbeTools.DEFAULT_REPORT_CHARS, entry.length());

        // askUser 恒在永不卸载集：超长答复也原文入窗
        Fix f2 = new Fix("s-off-3b");
        String bigAnswer = ContextProbeTools.report(10000, "口径");
        ScriptedModel m2 = scripted(
                tool("call-1", "askUser", "{\"question\":\"口径?\",\"optionsJson\":\"[]\"}"), done("结论"));
        new ToolAgentRunner().run(m2, "系统", "任务",
                Arrays.<Object>asList(new FakeAskUserTool(bigAnswer), new ContextProbeTools()),
                opts(5, 20, 4000), f2.ctx.loopListener());
        Assert.assertEquals(bigAnswer, resultTextOf(m2.views.get(1).messages, "call-1"));
    }

    /** I6/I3：钉住对不被 token 驱逐（预算软超也只搬移保留，绝不拆对裁丢） */
    @Test
    public void 钉住对不被token驱逐() {
        Fix f = new Fix("s-off-4");
        AgentLoopOptions opt = opts(6, 100, 4000);
        opt.setPinnedTools(Collections.singleton("bigReport"));
        opt.setContextTokenBudget(3000);
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "echo", "{\"text\":\"hi\"}"),
                done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new ContextProbeTools(), new EchoTool()),
                opt, f.ctx.loopListener());
        // 末轮入窗仍含钉住全文结果（预算软超所致，I1 自检由 ScriptedModel 全程把关）
        List<ChatMessage> last = model.views.get(model.views.size() - 1).messages;
        String pinned = resultTextOf(last, "call-1");
        Assert.assertNotNull("钉住对必须仍在窗内", pinned);
        Assert.assertEquals(ContextProbeTools.DEFAULT_REPORT_CHARS, pinned.length());
    }

    /** 回捞闭环零损失：第 3 轮回捞结果逐字符等于原文全文，且不被二次卸载（I6） */
    @Test
    public void 回捞闭环零损失且结果不被二次卸载() {
        Fix f = new Fix("s-off-5");
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "recallToolResult", recallArgs("call-1")),
                done("结论引用 KEY-A-8888"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Collections.<Object>singletonList(new ContextProbeTools(10000)), opts(6, 20, 4000),
                f.ctx.loopListener());
        Assert.assertNotNull(r);
        String ptr = resultTextOf(model.views.get(1).messages, "call-1");
        Assert.assertTrue("指针应远短于原文: " + ptr.length(), ptr.length() < 1500);
        String recalled = resultTextOf(model.views.get(2).messages, "call-2");
        Assert.assertEquals(ContextProbeTools.report(10000, "A"), recalled);
        Assert.assertFalse("回捞结果永不二次卸载（I6）", ToolResultOffloader.isPointer(recalled));
        Assert.assertTrue(recalled.contains("KEY-A-8888"));
    }

    /** 三层闸①：单 run 回捞次数上限（Param 默认 3）生效，超限只回收口指令不击穿 */
    @Test
    public void 回捞次数上限生效() {
        Fix f = new Fix("s-off-6");
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "recallToolResult", recallArgs("call-1")),
                tool("call-3", "recallToolResult", recallArgs("call-1")),
                tool("call-4", "recallToolResult", recallArgs("call-1")),
                tool("call-5", "recallToolResult", recallArgs("call-1")),
                done("收口"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Collections.<Object>singletonList(new ContextProbeTools(10000)), opts(10, 20, 4000),
                f.ctx.loopListener());
        Assert.assertNotNull(r);
        List<ChatMessage> last = model.views.get(model.views.size() - 1).messages;
        Assert.assertTrue("第 4 次回捞应收到预算用尽指令", resultTextOf(last, "call-5").contains("回捞预算已用尽"));
        Assert.assertFalse("第 3 次仍在限额内", resultTextOf(last, "call-4").contains("回捞预算已用尽"));
    }

    /** 模型幻觉句柄：未命中返回指令式错误文本含本 run 可用句柄清单，不击穿循环 */
    @Test
    public void 回捞未知句柄返回可用清单不击穿循环() {
        Fix f = new Fix("s-off-7");
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "recallToolResult", recallArgs("call-hallucinated")),
                done("按预览保守作答"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Collections.<Object>singletonList(new ContextProbeTools(10000)), opts(6, 20, 4000),
                f.ctx.loopListener());
        Assert.assertNotNull("未命中不得击穿循环", r.getText());
        String miss = resultTextOf(model.views.get(2).messages, "call-2");
        Assert.assertTrue(miss.contains("未找到句柄"));
        Assert.assertTrue("应回本 run 已卸载句柄清单", miss.contains("call-1"));
    }

    /** 岔路 3 死循环锁死：cachedTools 同参重调零执行、仍是新句柄、按新句柄可取回全文 */
    @Test
    public void cachedTools同参重调仍是新句柄且可按新句柄取回全文() {
        Fix f = new Fix("s-off-8");
        ContextProbeTools probe = new ContextProbeTools(10000);
        AgentLoopOptions opt = opts(8, 20, 4000);
        opt.setCachedTools(Collections.singleton("bigReport"));
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "bigReport", bigArgs("A")),
                tool("call-3", "recallToolResult", recallArgs("call-2")),
                done("引用 KEY-A-8888"));
        new ToolAgentRunner().run(model, "系统", "任务", Collections.<Object>singletonList(probe),
                opt, f.ctx.loopListener());
        Assert.assertEquals("同参应命中缓存零重执行", 1, probe.bigReportCalls());
        String p1 = resultTextOf(model.views.get(2).messages, "call-1");
        String p2 = resultTextOf(model.views.get(2).messages, "call-2");
        Assert.assertTrue(ToolResultOffloader.isPointer(p1) && ToolResultOffloader.isPointer(p2));
        Assert.assertEquals("call-1", ToolResultOffloader.handleOf(p1));
        Assert.assertEquals("call-2 必须是本次新 id 的指针", "call-2", ToolResultOffloader.handleOf(p2));
        // 新句柄可回捞：缓存命中路径照样发 onToolResult(新 id, 全文)
        Assert.assertEquals(ContextProbeTools.report(10000, "A"), eventOutput(f, "call-2"));
        String recalled = resultTextOf(model.views.get(3).messages, "call-3");
        Assert.assertEquals(ContextProbeTools.report(10000, "A"), recalled);
    }

    /** I5 + 默认零副作用：无回捞通道时不注册 recallToolResult、一律原文入窗 */
    @Test
    public void 无回捞通道时不注册回捞工具且一律原文入窗() {
        AgentLoopListener legacy = new AgentLoopListener() {
            @Override
            public void log(String line) {
            }

            @Override
            public boolean shouldStop() {
                return false;
            }
        };
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")), done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opts(5, 20, 4000), legacy);
        for (ScriptedModel.RoundView v : model.views) {
            for (ToolSpecification s : v.specs) {
                Assert.assertNotEquals(AgentToolRecall.TOOL_NAME, s.name());
            }
        }
        String entry = resultTextOf(model.views.get(1).messages, "call-1");
        Assert.assertFalse("I5：无回捞通道一律原文入窗", ToolResultOffloader.isPointer(entry));
    }

    /** 开启态才注册回捞工具进 specs（有回捞通道且阈值正值） */
    @Test
    public void 开启态注册回捞工具进specs() {
        Fix f = new Fix("s-off-9");
        ScriptedModel model = scripted(done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opts(3, 20, 4000), f.ctx.loopListener());
        boolean found = false;
        for (ToolSpecification s : model.views.get(0).specs) {
            found |= AgentToolRecall.TOOL_NAME.equals(s.name());
        }
        Assert.assertTrue(found);
    }

    /** I10：收口例外路径（最后一次 generate 前追加的消息）一律原文不卸载 */
    @Test
    public void 收口路径不卸载() {
        Fix f = new Fix("s-off-10");
        String bigAnswer = ContextProbeTools.report(10000, "口径");
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "askUser", "{\"question\":\"口径?\",\"optionsJson\":\"[]\"}"),
                done("按用户口径收口"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new FakeAskUserTool(bigAnswer), new ContextProbeTools()),
                opts(1, 20, 4000), f.ctx.loopListener());
        Assert.assertNotNull(r);
        // 收口后最后一次 generate 的入窗：call-2 结果必须全文（allowOffload=false，I10 双保险）
        List<ChatMessage> last = model.views.get(model.views.size() - 1).messages;
        Assert.assertEquals(bigAnswer, resultTextOf(last, "call-2"));
        // 常规轮（call-1）同场景下确应被卸载——证明卸载通路本身是开的
        Assert.assertTrue(ToolResultOffloader.isPointer(resultTextOf(last, "call-1")));
    }

    /** 交互矩阵：failureBreaker 必须吃原文（防把指针当新失败模式计数） */
    @Test
    public void failureBreaker收到的是原文而非指针() {
        Fix f = new Fix("s-off-11");
        final List<String> seen = new ArrayList<>();
        AgentFailureBreaker spy = new AgentFailureBreaker(Collections.<AgentFailureBreaker.Rule>emptyList()) {
            @Override
            public String onToolResult(String result) {
                seen.add(result);
                return null;
            }
        };
        AgentLoopOptions opt = opts(5, 20, 4000);
        opt.setFailureBreaker(spy);
        ScriptedModel model = scripted(
                tool("call-1", "bigReport", bigArgs("A")), done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", Collections.<Object>singletonList(new ContextProbeTools()),
                opt, f.ctx.loopListener());
        Assert.assertEquals(1, seen.size());
        Assert.assertEquals(ContextProbeTools.DEFAULT_REPORT_CHARS, seen.get(0).length());
        Assert.assertFalse(seen.get(0).contains("已卸载"));
    }

    /** I1 全程成立（ScriptedModel 每轮自检即失败即抛）：一个 Ai 调 3 个工具、其中一个结果巨大的并行形态 */
    @Test
    public void 配对不变式全程成立含并行多工具形态() {
        Fix f = new Fix("s-off-12");
        ScriptedModel model = scripted(
                tools(
                        ScriptedModel.req("call-1", "echo", "{\"text\":\"hi\"}"),
                        ScriptedModel.req("call-2", "bigReport", bigArgs("A")),
                        ScriptedModel.req("call-3", "echo", "{\"text\":\"ho\"}")),
                tool("call-4", "recallToolResult", recallArgs("call-2")),
                done("结论"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new EchoTool(), new ContextProbeTools(10000)), opts(8, 20, 4000),
                f.ctx.loopListener());
        Assert.assertNotNull(r);
        String ptr = resultTextOf(model.views.get(1).messages, "call-2");
        Assert.assertTrue(ToolResultOffloader.isPointer(ptr));
        // 三结果紧随同序（I1 自检通过即证），echo 短结果不卸载
        Assert.assertEquals("echo:hi", resultTextOf(model.views.get(1).messages, "call-1"));
    }

    /** I2 只改文本不改拓扑：开关两跑的序列「类型+id」签名逐条一致 */
    @Test
    public void 顺序不变只改文本不改拓扑() {
        List<Step> script = probeSteps(
                tool("call-1", "bigReport", bigArgs("A")),
                tool("call-2", "echo", "{\"text\":\"hi\"}"),
                done("结论"));
        ScriptedModel base = new ScriptedModel(script);
        new ToolAgentRunner().run(base, "系统", "任务",
                Arrays.<Object>asList(new ContextProbeTools(10000), new EchoTool()), opts(6, 20, null),
                new Fix("s-off-13a").ctx.loopListener());
        ScriptedModel off = new ScriptedModel(script);
        new ToolAgentRunner().run(off, "系统", "任务",
                Arrays.<Object>asList(new ContextProbeTools(10000), new EchoTool()), opts(6, 20, 4000),
                new Fix("s-off-13b").ctx.loopListener());
        Assert.assertEquals(base.views.size(), off.views.size());
        for (int i = 0; i < base.views.size(); i++) {
            Assert.assertEquals("第 " + (i + 1) + " 轮拓扑签名",
                    topology(base.views.get(i).messages), topology(off.views.get(i).messages));
        }
    }

    private static String topology(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m instanceof AiMessage && ((AiMessage) m).hasToolExecutionRequests()) {
                sb.append("AI[");
                for (ToolExecutionRequest r : ((AiMessage) m).toolExecutionRequests()) {
                    sb.append(r.id()).append(',');
                }
                sb.append("];");
            } else if (m instanceof ToolExecutionResultMessage) {
                sb.append("RES[").append(((ToolExecutionResultMessage) m).id()).append("];");
            } else {
                sb.append(m.type()).append(';');
            }
        }
        return sb.toString();
    }
}
