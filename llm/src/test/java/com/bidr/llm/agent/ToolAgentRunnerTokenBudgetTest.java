package com.bidr.llm.agent;

import com.bidr.llm.agent.ScriptedModel.Step;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Title: ToolAgentRunnerTokenBudgetTest
 * Description: token 预算裁窗测试（计划 §6.5）：最关键一条是「budget=0 且 offload=0 时行为与
 * 改造前逐条一致」（I9）——用测试内逐行照抄的改造前算法独立模拟，与真实 runner 逐轮消息签名比对。
 * 另锁死 I11（双约束取更严）、I12（驱逐单一出口共用 digest）、切点回退不拆对（I1）、
 * 步骤 D 兜底（尾部组超预算不裁空、软超不迭代）、计量日志（M4.5 验收数字来源）
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ToolAgentRunnerTokenBudgetTest {

    private static final String EXPECTED_DIGEST_PREFIX = "【探索记录摘要（窗口裁切压缩归档）】\n";

    private static AgentLoopOptions opts(int rounds, int window, Integer offload, Integer budget) {
        AgentLoopOptions o = new AgentLoopOptions(rounds, window);
        if (offload != null) {
            o.setToolResultOffloadChars(offload);
        }
        if (budget != null) {
            o.setContextTokenBudget(budget);
        }
        return o;
    }

    /** 只验协议不验会话链的轻量 listener */
    private static AgentLoopListener light(final List<String> logs) {
        return new AgentLoopListener() {
            @Override
            public void log(String line) {
                if (logs != null) {
                    logs.add(line);
                }
            }

            @Override
            public boolean shouldStop() {
                return false;
            }
        };
    }

    /** 探针工具集（echo + bigReport 组合，脚本确定） */
    private static List<Object> tools(int reportChars) {
        return Arrays.<Object>asList(new ToolAgentRunnerOffloadTest.EchoTool(),
                new ContextProbeTools(reportChars));
    }

    // ---------------- I9 关闭态逐条等价（全案最关键测试） ----------------

    /**
     * budget=0 且 offload=0 时，每轮进入模型的消息清单与「改造前算法」独立模拟结果逐条一致：
     * 模拟侧逐行照抄改造前 trimToolMemory/digestOf/trimDigest/pinnedBefore 组合同一脚本消息流，
     * 与 runner 实际每轮 generate 入窗快照比对全量签名（含 digest 文本、顺序、类型、工具结果全文）
     */
    @Test
    public void budget为0时行为与改造前逐条一致() {
        Step[] script = {
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "echo", "{\"text\":\"hi\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-4", "echo", "{\"text\":\"ho\"}"),
                Step.tool("call-5", "bigReport", "{\"topic\":\"C\"}"),
                Step.tool("call-6", "echo", "{\"text\":\"hx\"}"),
                Step.done("结论")};
        int window = 4;
        ScriptedModel model = new ScriptedModel(script);
        new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(10, window, null, null), light(null));

        // 模拟侧：完全按改造前逻辑推进同一消息流
        List<String> simulated = simulateOldRunner(script, window);
        Assert.assertEquals(model.views.size(), simulated.size());
        for (int i = 0; i < model.views.size(); i++) {
            Assert.assertEquals("第 " + (i + 1) + " 轮入窗逐条一致（I9）",
                    simulated.get(i), signature(model.views.get(i).messages));
        }
    }

    private static List<String> simulateOldRunner(Step[] script, int window) {
        List<String> snapshots = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("系统"));
        messages.add(UserMessage.from("任务"));
        for (Step step : script) {
            oldTrim(messages, window, Collections.<String>emptySet());
            snapshots.add(signature(messages));
            if (step.requests == null) {
                return snapshots;
            }
            AiMessage ai = AiMessage.from(step.requests);
            messages.add(ai);
            for (ToolExecutionRequest r : step.requests) {
                messages.add(ToolExecutionResultMessage.from(r, toolResultOf(r)));
            }
        }
        return snapshots;
    }

    /** 模拟侧工具执行复刻（须与真实 @Tool 返回逐字节一致，否则 I9 签名比对失真） */
    private static String toolResultOf(ToolExecutionRequest r) {
        String flat = r.arguments().replaceAll("[{}\"\\\\]", "");
        if ("echo".equals(r.name())) {
            int at = flat.indexOf("text:");
            return "echo:" + flat.substring(at + "text:".length());
        }
        int at = flat.indexOf("topic:");
        return ContextProbeTools.report(10000, flat.substring(at + "topic:".length()));
    }

    // ---- 改造前算法逐行照抄（git 基线 f 前版本 ToolAgentRunner.trimToolMemory 一族，仅供 I9 模拟） ----

    private static void oldTrim(List<ChatMessage> messages, int window, Set<String> pinnedTools) {
        int headEnd = (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) ? 2 : 1;
        String oldDigest = "";
        if (messages.size() > headEnd && messages.get(headEnd) instanceof UserMessage) {
            String t = ((UserMessage) messages.get(headEnd)).singleText();
            if (t != null && t.startsWith(EXPECTED_DIGEST_PREFIX)) {
                oldDigest = t.substring(EXPECTED_DIGEST_PREFIX.length());
                headEnd = headEnd + 1;
            }
        }
        if (messages.size() <= headEnd + window) {
            return;
        }
        int from = messages.size() - window;
        while (from > headEnd && messages.get(from) instanceof ToolExecutionResultMessage) {
            from--;
        }
        List<ChatMessage> pinned = oldPinnedBefore(messages, headEnd, from, pinnedTools);
        String digestNew = oldDigestOf(messages, headEnd, from, pinned);
        List<ChatMessage> tail = new ArrayList<>(messages.subList(from, messages.size()));
        messages.subList(headEnd, messages.size()).clear();
        String digest = oldTrimDigest(oldDigest + digestNew);
        if (!digest.isEmpty()) {
            messages.add(UserMessage.from(EXPECTED_DIGEST_PREFIX + digest));
        }
        messages.addAll(pinned);
        messages.addAll(tail);
    }

    private static String oldDigestOf(List<ChatMessage> messages, int headEnd, int from, List<ChatMessage> pinned) {
        Set<ChatMessage> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(pinned);
        StringBuilder sb = new StringBuilder();
        for (int i = headEnd; i < from; i++) {
            ChatMessage m = messages.get(i);
            if (kept.contains(m) || !(m instanceof AiMessage)) {
                continue;
            }
            AiMessage ai = (AiMessage) m;
            if (ai.hasToolExecutionRequests()) {
                int j = i + 1;
                for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                    sb.append("· ").append(r.name()).append("(").append(oldBrief(r.arguments(), 60)).append(")");
                    if (j < from && messages.get(j) instanceof ToolExecutionResultMessage
                            && !kept.contains(messages.get(j))) {
                        sb.append(" → ").append(oldBrief(((ToolExecutionResultMessage) messages.get(j)).text(), 100));
                        j++;
                    }
                    sb.append("\n");
                }
            } else if (ai.text() != null && !ai.text().trim().isEmpty()) {
                sb.append("· 轮结论：").append(oldBrief(ai.text(), 100)).append("\n");
            }
        }
        return sb.toString();
    }

    private static String oldTrimDigest(String d) {
        return d.length() <= 3000 ? d : "…" + d.substring(d.length() - 3000);
    }

    private static String oldBrief(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > max ? one.substring(0, max) + "..." : one;
    }

    private static List<ChatMessage> oldPinnedBefore(List<ChatMessage> messages, int headEnd, int from,
                                                     Set<String> pinnedTools) {
        List<ChatMessage> pinned = new ArrayList<>();
        if (pinnedTools == null || pinnedTools.isEmpty()) {
            return pinned;
        }
        Set<ChatMessage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = headEnd; i < from; i++) {
            ChatMessage m = messages.get(i);
            if (!(m instanceof AiMessage) || seen.contains(m)) {
                continue;
            }
            AiMessage ai = (AiMessage) m;
            if (!ai.hasToolExecutionRequests()) {
                continue;
            }
            boolean hit = false;
            for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                if (pinnedTools.contains(r.name())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            pinned.add(ai);
            seen.add(ai);
            for (int j = i + 1; j < from && messages.get(j) instanceof ToolExecutionResultMessage; j++) {
                pinned.add(messages.get(j));
                seen.add(messages.get(j));
            }
        }
        return pinned;
    }

    /** 全量消息签名：类型|工具id|正文（digest 文本一致性也在其中，I9 逐条比对的口径） */
    private static String signature(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m instanceof SystemMessage) {
                sb.append("SYS|").append(((SystemMessage) m).text()).append('\n');
            } else if (m instanceof UserMessage) {
                sb.append("USR|").append(((UserMessage) m).singleText()).append('\n');
            } else if (m instanceof AiMessage) {
                AiMessage ai = (AiMessage) m;
                sb.append("AI|").append(ai.text() == null ? "" : ai.text());
                if (ai.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                        sb.append('|').append(r.id()).append('#').append(r.name()).append('#').append(r.arguments());
                    }
                }
                sb.append('\n');
            } else if (m instanceof ToolExecutionResultMessage) {
                ToolExecutionResultMessage tr = (ToolExecutionResultMessage) m;
                sb.append("RES|").append(tr.id()).append('|').append(tr.text()).append('\n');
            } else {
                sb.append("OTHER|").append(m).append('\n');
            }
        }
        return sb.toString();
    }

    // ---------------- token 预算触发与双约束 ----------------

    /** 条数未超但 token 超触发驱逐（条数量纲两个方向都错的痛点，本机制补上 token 维度） */
    @Test
    public void 条数未超但token超触发驱逐() {
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"C\"}"),
                Step.done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(8, 100, null, 2000), light(null)); // window=100 条数维度永不触发
        ScriptedModel.RoundView last = model.views.get(model.views.size() - 1);
        Assert.assertTrue("token 触发应产出共享格式的探索摘要", hasDigest(last.messages));
        Assert.assertTrue("发生驱逐后条数必小于全量堆积", last.messageCount < 8);
    }

    /** I11 双约束取更严：token 开启后保留段只会更短，绝不超过仅条数约束的保留量 */
    @Test
    public void 双约束取更严者() {
        Step[] script = {
                Step.tool("call-1", "echo", "{\"text\":\"a1\"}"),
                Step.tool("call-2", "echo", "{\"text\":\"a2\"}"),
                Step.tool("call-3", "echo", "{\"text\":\"a3\"}"),
                Step.tool("call-4", "echo", "{\"text\":\"a4\"}"),
                Step.tool("call-5", "echo", "{\"text\":\"a5\"}"),
                Step.done("结论")};
        ScriptedModel countOnly = new ScriptedModel(script);
        new ToolAgentRunner().run(countOnly, "系统", "任务", tools(1000), opts(10, 6, null, null), light(null));
        ScriptedModel both = new ScriptedModel(script);
        // 预算刻意小于 6 条窗口的实际占用 → token 切点更严（I11 取 max：更靠后的切点）
        new ToolAgentRunner().run(both, "系统", "任务", tools(1000), opts(10, 6, null, 95), light(null));
        int lastCount = countOnly.views.get(countOnly.views.size() - 1).messageCount;
        int lastBoth = both.views.get(both.views.size() - 1).messageCount;
        Assert.assertTrue("双约束下保留段应严格不多于条数单约束: " + lastBoth + " vs " + lastCount,
                lastBoth <= lastCount);
        Assert.assertTrue("且 token 更严时确实多裁了", lastBoth < lastCount);
    }

    /** I1 配套：token 切点必须同样走「回退到非工具结果」防线——大结果作为组尾时不拆对 */
    @Test
    public void 切点不拆对() {
        ScriptedModel model = new ScriptedModel(
                Step.tools(
                        ScriptedModel.req("call-1a", "echo", "{\"text\":\"x1\"}"),
                        ScriptedModel.req("call-1b", "bigReport", "{\"topic\":\"A\"}"),
                        ScriptedModel.req("call-1c", "echo", "{\"text\":\"x2\"}")),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"C\"}"),
                Step.done("结论"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(8, 100, null, 2500), light(null));
        Assert.assertNotNull(r);
        // 走到这里即证每轮入窗配对完整（ScriptedModel.assertPairing 违规即 AssertionError）
        for (ScriptedModel.RoundView v : model.views) {
            ScriptedModel.assertPairing(v.messages);
        }
    }

    /** 步骤 D 兜底：尾部最近一组自身超预算时保留最近一组，不裁空、不抛、不发 digest */
    @Test
    public void 单条巨大结果超预算时保留最近一组不裁空() {
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.done("结论"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(5, 100, null, 500), light(null));
        Assert.assertNotNull(r);
        ScriptedModel.RoundView second = model.views.get(1);
        Assert.assertFalse("无可驱逐段时不得产出摘要", hasDigest(second.messages));
        Assert.assertTrue("最近一组必须完整在窗", second.messageCount >= 4);
    }

    /** I12：token 驱逐与条数驱逐共用同一条 digest 路径（同一前缀、头部唯一摘要槽） */
    @Test
    public void token驱逐与条数驱逐共用digest路径() {
        Step[] script = {
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"C\"}"),
                Step.done("结论")};
        ScriptedModel byCount = new ScriptedModel(script);
        new ToolAgentRunner().run(byCount, "系统", "任务", tools(10000), opts(8, 2, null, null), light(null));
        ScriptedModel byToken = new ScriptedModel(script);
        new ToolAgentRunner().run(byToken, "系统", "任务", tools(10000), opts(8, 100, null, 2000), light(null));
        ScriptedModel.RoundView lc = byCount.views.get(byCount.views.size() - 1);
        ScriptedModel.RoundView lt = byToken.views.get(byToken.views.size() - 1);
        String d1 = digestOf(lc.messages);
        String d2 = digestOf(lt.messages);
        Assert.assertNotNull("条数驱逐应有摘要", d1);
        Assert.assertNotNull("token 驱逐应有摘要", d2);
        Assert.assertTrue(d1.startsWith(EXPECTED_DIGEST_PREFIX.substring(0, 12)));
        Assert.assertTrue(d2.startsWith(EXPECTED_DIGEST_PREFIX.substring(0, 12)));
        // 共用出口核验：两维度各驱逐两轮后，头部摘要消息形态完全一致（既有吸槽+续写结构，
        // 不新增第二种摘要格式、不新增摘要槽位——I12）
        Assert.assertTrue("摘要应含被驱逐的 bigReport 调用行", d2.contains("bigReport("));
        Assert.assertEquals("两维度触发后头部摘要消息形态一致（同一条路径）",
                countDigest(lc.messages), countDigest(lt.messages));
    }

    /** I7 贯穿驱逐：被驱逐进摘要的指针仍带可回捞句柄，模型经句柄取回全文 */
    @Test
    public void 摘要内指针保留句柄可回捞() {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId("s-budget-recall");
        state.setAgentKey("probe");
        state.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.saveState(state);
        AgentSessionContext ctx = new AgentSessionContext(state, store, new HashMap<>());

        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"C\"}"),
                Step.tool("call-4", "recallToolResult", "{\"toolCallId\":\"call-1\"}"),
                Step.done("结论引用 KEY-A-8888"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(8, 100, 4000, 2000), ctx.loopListener());
        Assert.assertNotNull(r);
        ScriptedModel.RoundView preRecall = model.views.get(3);
        String digest = digestOf(preRecall.messages);
        Assert.assertNotNull("驱逐后应有摘要", digest);
        Assert.assertTrue("摘要内指针须保留 tool_call_id=call-1（I7）", digest.contains("tool_call_id=call-1"));
        String recalled = resultText(preRecall.messages, "call-4") == null
                ? resultText(model.views.get(4).messages, "call-4") : resultText(preRecall.messages, "call-4");
        Assert.assertNotNull(recalled);
        // 回捞结果 ≤ 20000 上限：10000 字报告应逐字符全文返回
        Assert.assertEquals(ContextProbeTools.report(10000, "A"), recalled);
    }

    // ---------------- A9：digest 句柄保留（I15 同源 + 端到端回捞） ----------------

    /** A9 指令行原文（独立照抄，与生产常量 DIGEST_RECALL_GUIDANCE 逐字符对齐——I9 基线同款纪律） */
    private static final String EXPECTED_RECALL_GUIDANCE =
            "被裁内容如需原文：凭行内「句柄=」调 recallToolResult，一次一个，受次数上限约束。\n";

    /** 会话链 listener 夹具（recallAvailable=true：事件流持全文，supportsToolResultRecall 为真） */
    private static AgentLoopListener sessionListener(String sessionId) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(sessionId);
        state.setAgentKey("probe");
        state.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.saveState(state);
        return new AgentSessionContext(state, store, new HashMap<>()).loopListener();
    }

    private static boolean specsContainRecall(ScriptedModel model) {
        for (ScriptedModel.RoundView v : model.views) {
            for (dev.langchain4j.agent.tool.ToolSpecification s : v.specs) {
                if (AgentToolRecall.TOOL_NAME.equals(s.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 首个含 digest 的入窗视图（window=2 时第 3 轮必有） */
    private static String firstDigest(ScriptedModel model) {
        for (ScriptedModel.RoundView v : model.views) {
            String d = digestOf(v.messages);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    /**
     * A9 测试 4：recallUsable 真值矩阵（offloadOn=T / offloadChars=0&&tokenBudget>0 / 两者皆0
     * × recallAvailable=T/F 共 6 格）——specs 是否含 recallToolResult 与 digest 是否带行内句柄
     * 两处必须同随判据（I15 同源不半开）；两者皆 0 时即使有回捞通道，specs 也不得多出本工具（I9）
     */
    @Test
    public void recallUsable真值矩阵specs与digest同源() {
        //      offload budget recallAvail expectUsable
        Object[][] cases = {
                {4000, null, true, true},    // 卸载开启（入场指针句柄路径）
                {4000, null, false, false},  // I5：无回捞通道 offloadOn 亦为假，budget=0 → 全关
                {null, 2000, true, true},    // A9 新形态：不卸载、被驱逐入 digest 带句柄
                {null, 2000, false, false},  // 驱逐照常但无句柄（无通道不写）
                {null, null, true, false},   // 默认态×会话链：specs 不多工具、digest 无句柄（I9）
                {null, null, false, false},  // 默认态×轻链路：同上
        };
        for (Object[] c : cases) {
            Integer offload = (Integer) c[0];
            Integer budget = (Integer) c[1];
            boolean recallAvail = (Boolean) c[2];
            boolean expected = (Boolean) c[3];
            AgentLoopListener l = recallAvail
                    ? sessionListener("s-a9-matrix-" + System.nanoTime()) : light(null);
            ScriptedModel model = new ScriptedModel(
                    Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                    Step.tool("call-2", "echo", "{\"text\":\"hi\"}"),
                    Step.done("结论"));
            // window=2 保证各格都发生驱逐（digest 必产出，句柄断言有牙齿）
            new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                    opts(6, 2, offload, budget), l);
            String digest = firstDigest(model);
            Assert.assertNotNull("window=2 必触发驱逐产出摘要", digest);
            Assert.assertEquals("specs 含回捞工具须符判据(offload=" + offload + ",budget=" + budget
                            + ",recallAvail=" + recallAvail + ")",
                    expected, specsContainRecall(model));
            Assert.assertEquals("digest 行内句柄与 specs 同源(offload=" + offload + ",budget=" + budget
                            + ",recallAvail=" + recallAvail + ")",
                    expected, digest.contains("（句柄=call-1）"));
            Assert.assertEquals("digest 指令行与 specs 同源(offload=" + offload + ",budget=" + budget
                            + ",recallAvail=" + recallAvail + ")",
                    expected, digest.startsWith(EXPECTED_DIGEST_PREFIX + EXPECTED_RECALL_GUIDANCE));
        }
    }

    /**
     * A9 测试 1 强化：多轮驱逐重拼 digest 时指令行幂等不重复（旧摘要回收先剥再拼）、
     * 每轮被驱逐的工具结果行各带自己的句柄
     */
    @Test
    public void 多轮驱逐指令行唯一且各行带句柄() {
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "bigReport", "{\"topic\":\"C\"}"),
                Step.done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(8, 100, null, 2000), sessionListener("s-a9-multi"));
        ScriptedModel.RoundView last = model.views.get(model.views.size() - 1);
        String digest = lastDigest(last.messages);
        Assert.assertNotNull(digest);
        Assert.assertEquals("指令行重拼后必须恰出现一次（幂等剥离）", 1,
                occurrences(digest, "受次数上限约束"));
        Assert.assertTrue("第一轮被驱逐行保留句柄", digest.contains("（句柄=call-1）"));
        Assert.assertTrue("第二轮被驱逐行保留句柄", digest.contains("（句柄=call-2）"));
    }

    /**
     * A9 测试 2（本次改动的存在理由，端到端）：大结果常规入窗（offloadChars=0，不触发卸载）→
     * 被 token 预算裁进 digest → 模型从行内「句柄=」读到句柄 → 调 recallToolResult →
     * 取回结果逐字符等于原文全文
     */
    @Test
    public void digest行内句柄端到端回捞逐字符等于原文() {
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}"),
                Step.tool("call-3", "recallToolResult", "{\"toolCallId\":\"call-1\"}"),
                Step.done("结论引用 KEY-A-8888"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务", tools(10000),
                opts(8, 100, null, 2000), sessionListener("s-a9-e2e"));
        Assert.assertNotNull(r);
        String digest = firstDigest(model);
        Assert.assertNotNull("token 触发驱逐应有摘要", digest);
        Assert.assertTrue("digest 头部须带指令行（开启态）",
                digest.startsWith(EXPECTED_DIGEST_PREFIX + EXPECTED_RECALL_GUIDANCE));
        Assert.assertTrue("被驱逐结果行尾须带行内句柄", digest.contains("（句柄=call-1）"));
        Assert.assertTrue("句柄须落进行尾（brief 截断之外）",
                digest.contains("（句柄=call-1）\n") || digest.endsWith("（句柄=call-1）"));
        Assert.assertEquals("原文未泄漏进摘要（回捞才有意义）", -1, digest.indexOf("KEY-A-8888"));
        System.out.println("[A9样例] 开启态 digest 实渲染 ↓\n" + digest + "[A9样例] ↑");
        String recalled = null;
        for (ScriptedModel.RoundView v : model.views) {
            String t = resultText(v.messages, "call-3");
            if (t != null) {
                recalled = t;
            }
        }
        Assert.assertNotNull(recalled);
        Assert.assertEquals("端到端：凭 digest 句柄回捞须逐字符等于原文全文",
                ContextProbeTools.report(10000, "A"), recalled);
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        int at = 0;
        while ((at = haystack.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }

    /** 步骤 D：钉住对不可驱逐致软超时只告警一次不迭代（跑完即证终止性），钉住对完整保留 */
    @Test
    public void 软超只告警不迭代() {
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}"),
                Step.tool("call-2", "echo", "{\"text\":\"x\"}"),
                Step.tool("call-3", "echo", "{\"text\":\"y\"}"),
                Step.tool("call-4", "echo", "{\"text\":\"z\"}"),
                Step.done("结论"));
        AgentLoopOptions opt = opts(10, 6, null, 800);
        opt.setPinnedTools(Collections.singleton("bigReport"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务", tools(10000), opt, light(null));
        Assert.assertNotNull("软超不得击穿循环（不迭代重算，仅告警）", r);
        Assert.assertEquals(model.views.size(), 5);
        ScriptedModel.RoundView last = model.views.get(model.views.size() - 1);
        String pinned = resultText(last.messages, "call-1");
        Assert.assertNotNull("钉住对必须永久保留", pinned);
        Assert.assertEquals(10000, pinned.length());
        String d = digestOf(last.messages);
        Assert.assertTrue("钉住对不入 digest", d == null || !d.contains("bigReport"));
    }

    /** M4.5：治理开启态每次 generate 前一条计量日志，含字符与估算 token 数字；关闭态零新增（I9） */
    @Test
    public void 计量日志每轮一条且含字符与估算token数字() {
        List<String> logs = new ArrayList<>();
        ScriptedModel model = new ScriptedModel(
                Step.tool("call-1", "echo", "{\"text\":\"a\"}"),
                Step.tool("call-2", "echo", "{\"text\":\"b\"}"),
                Step.done("结论"));
        new ToolAgentRunner().run(model, "系统", "任务", tools(100), opts(8, 100, null, 5000), light(logs));
        int meters = 0;
        for (String line : logs) {
            if (line.startsWith("上下文计量：")) {
                meters++;
                Assert.assertTrue(line.contains("条数="));
                Assert.assertTrue(line.contains("字符="));
                Assert.assertTrue(line.contains("估算token="));
                Assert.assertTrue(line.contains("（含工具定义 "));
                Assert.assertTrue(line.contains("本轮卸载="));
                Assert.assertTrue(line.contains("累计卸载="));
                Assert.assertTrue(line.contains("累计回捞="));
                Assert.assertTrue(line.contains("累计驱逐="));
            }
        }
        Assert.assertEquals("每次 generate 前恰一条", model.views.size(), meters);

        // 关闭态：无任何计量日志新增（事件流零变化，I9）
        List<String> offLogs = new ArrayList<>();
        ScriptedModel off = new ScriptedModel(
                Step.tool("call-1", "echo", "{\"text\":\"a\"}"),
                Step.done("结论"));
        new ToolAgentRunner().run(off, "系统", "任务", tools(100), opts(8, 100, null, null), light(offLogs));
        for (String line : offLogs) {
            Assert.assertFalse("关闭态不得输出计量日志: " + line, line.startsWith("上下文计量："));
        }
    }

    // ---------------- §7.1 层 1 对照数字表（合成夹具，零真模型） ----------------

    /**
     * 同一探索脚本跑两遍：A(offload=0,budget=0) 基线 vs D(offload=4000,budget=24000) 双开，
     * 逐轮+峰值透出「入窗消息条数/字符数/估算 token」并打印到 stdout（对照数字表取数口），
     * D 组锁死：三处 KEY-* 经 recallToolResult 全文回捞命中（回捞零损失）、askUser 口径经钉住存活（I6）、
     * 峰值字符与峰值估算 token 显著下降（判据 < 基线 40%）。
     * 真模型四组对照（§7.2 A/B/C/D + 端点实测 token）本轮不做，标 PENDING（见计划修订：后置 InkHub 实测轮）
     */
    @Test
    public void 层1对照数字表() {
        final String caliber = "按季度口径";
        Step call1 = Step.tool("call-1", "bigReport", "{\"topic\":\"A\"}");
        Step call2 = Step.tool("call-2", "bigReport", "{\"topic\":\"B\"}");
        Step call3 = Step.tool("call-3", "askUser", "{\"question\":\"按什么口径\",\"optionsJson\":\"[]\"}");
        Step call4 = Step.tool("call-4", "bigReport", "{\"topic\":\"A\"}");
        Step call5 = Step.tool("call-5", "bigReport", "{\"topic\":\"C\"}");

        // A 基线：全文入窗，30k×4 结果堆积即痛点形态
        ScriptedModel base = new ScriptedModel(call1, call2, call3, call4, call5, Step.done("结论"));
        new ToolAgentRunner().run(base, "系统", "任务",
                Arrays.<Object>asList(new ContextProbeTools(30000),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool(caliber)),
                opts(12, 20, null, null), light(null));

        // D 双开：同脚本 + 三步回捞（脚本化模型确定地按句柄取原文）
        AgentSessionState st = new AgentSessionState();
        st.setSessionId("s-l1-d");
        st.setAgentKey("probe");
        st.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.saveState(st);
        AgentSessionContext ctx = new AgentSessionContext(st, store, new HashMap<>());
        ScriptedModel dual = new ScriptedModel(call1, call2, call3, call4, call5,
                Step.tool("call-6", "recallToolResult", "{\"toolCallId\":\"call-1\"}"),
                Step.tool("call-7", "recallToolResult", "{\"toolCallId\":\"call-2\"}"),
                Step.tool("call-8", "recallToolResult", "{\"toolCallId\":\"call-5\"}"),
                Step.done("结论含 KEY-A-8888 KEY-B-8888 KEY-C-8888 与" + caliber));
        AgentLoopOptions dOpt = opts(12, 20, 4000, 24000);
        dOpt.setCachedTools(Collections.singleton("bigReport"));
        dOpt.setPinnedTools(Collections.singleton("askUser"));
        new ToolAgentRunner().run(dual, "系统", "任务",
                Arrays.<Object>asList(new ContextProbeTools(30000),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool(caliber)),
                dOpt, ctx.loopListener());

        int[] peakA = printMeterTable("A(offload=0,budget=0)", base.views);
        int[] peakD = printMeterTable("D(offload=4000,budget=24000)", dual.views);

        // 回捞零损失：三处深藏 KEY（60% 偏移，指针预览不可见）经句柄回捞逐字命中
        Assert.assertTrue("KEY-A 未随回捞返回", anyResultContains(dual.views, "call-6", "KEY-A-8888"));
        Assert.assertTrue("KEY-B 未随回捞返回", anyResultContains(dual.views, "call-7", "KEY-B-8888"));
        Assert.assertTrue("KEY-C 未随回捞返回", anyResultContains(dual.views, "call-8", "KEY-C-8888"));
        // I6：末轮入窗仍含 askUser 已确认口径（钉住对经驱逐搬移永久存活）
        Assert.assertTrue("D 组口径丢失", signature(
                dual.views.get(dual.views.size() - 1).messages).contains(caliber));
        // 收益锁死：D 组入窗峰值显著低于 A 组（判据 < 基线 40%，与 §6.4 峰值判据同口径）
        Assert.assertTrue("D 峰值字符未显著下降: " + peakD[1] + " vs " + peakA[1],
                peakD[1] * 10 < peakA[1] * 4);
        Assert.assertTrue("D 峰值估算 token 未显著下降: " + peakD[2] + " vs " + peakA[2],
                peakD[2] * 10 < peakA[2] * 4);
    }

    /** 透出逐轮+峰值计量行（stdout 打印即对照数字表原始数据） */
    private static int[] printMeterTable(String tag, List<ScriptedModel.RoundView> views) {
        StringBuilder sb = new StringBuilder();
        sb.append("[层1对照] ").append(tag)
                .append(" 逐轮(轮|条数|字符|估算token):\n");
        int peakMsgs = 0;
        int peakChars = 0;
        int peakEst = 0;
        for (ScriptedModel.RoundView v : views) {
            sb.append("  r").append(v.round).append('|').append(v.messageCount)
                    .append('|').append(v.chars).append('|').append(v.estTokens).append('\n');
            peakMsgs = Math.max(peakMsgs, v.messageCount);
            peakChars = Math.max(peakChars, v.chars);
            peakEst = Math.max(peakEst, v.estTokens);
        }
        sb.append("  峰值: 条数=").append(peakMsgs).append(" 字符=").append(peakChars)
                .append(" 估算token=").append(peakEst).append("（轮数=").append(views.size()).append("）\n");
        System.out.print(sb);
        return new int[]{peakMsgs, peakChars, peakEst};
    }

    private static boolean anyResultContains(List<ScriptedModel.RoundView> views, String callId, String needle) {
        for (ScriptedModel.RoundView v : views) {
            String t = resultText(v.messages, callId);
            if (t != null && t.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // ---------------- helpers ----------------

    private static boolean hasDigest(List<ChatMessage> messages) {
        return digestOf(messages) != null;
    }

    private static int countDigest(List<ChatMessage> messages) {
        int n = 0;
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage && ((UserMessage) m).singleText().startsWith(EXPECTED_DIGEST_PREFIX)) {
                n++;
            }
        }
        return n;
    }

    private static String digestOf(List<ChatMessage> messages) {
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage && ((UserMessage) m).singleText().startsWith(EXPECTED_DIGEST_PREFIX)) {
                return ((UserMessage) m).singleText();
            }
        }
        return null;
    }

    /** 末位摘要消息=最新累计版（第二次及以后的 trim 会残留首个旧摘要槽，HEAD 既有形态非 A9 引入，
     *  取末位即当前生效 digest——A9 多轮重拼案专用） */
    private static String lastDigest(List<ChatMessage> messages) {
        String found = null;
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage && ((UserMessage) m).singleText().startsWith(EXPECTED_DIGEST_PREFIX)) {
                found = ((UserMessage) m).singleText();
            }
        }
        return found;
    }

    private static String resultText(List<ChatMessage> messages, String callId) {
        for (ChatMessage m : messages) {
            if (m instanceof ToolExecutionResultMessage && callId.equals(((ToolExecutionResultMessage) m).id())) {
                return ((ToolExecutionResultMessage) m).text();
            }
        }
        return null;
    }
}
