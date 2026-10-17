package com.bidr.llm.agent;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.usage.ModelCallContext;
import com.bidr.llm.usage.ModelCallContexts;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: ToolAgentRunnerSurfaceRestrictTest
 * Description: 工具面收窄（M14 棒6）的框架侧契约测试。钉四件事：
 * ① <b>没装过滤器＝零行为</b>（取不到 Bean 连同一条日志都不多发，与改前逐字符一致）；
 * ② <b>schema / 装配单 / 执行期准入名单是同一份数据</b>——收窄发生在上报之前，所以"发给模型的工具面"
 * 与"台账里的清单"与"能执行到的名字"三处一起变窄，不存在第二判据；
 * ③ 四条形态闸：空表合法、返回 null＝不过滤、抛异常退回完整面（fail open）、
 * 返回未入参的名字＝越权加工具，整次过滤作废；
 * ④ 🔴 模型幻觉出一只已被收窄的工具名时，<b>执行期真的调不到</b>（验收②的运行期那条腿）。
 * 全程假模型（{@link ScriptedModel}），不依赖任何外部模型服务；测试缝＝{@code BeanUtil.setContext(mock)}，
 * 不给共享类开测试后门。
 *
 * @author Sharp
 * @since 2026-10-13
 */
public class ToolAgentRunnerSurfaceRestrictTest {

    /** 三只工具：plan / ask-user 两族是登记过的面，echo、add 视作"目录外"的面件 */
    public static class ProbeTools {

        /** 实际执行到的工具名（验"收窄后真没执行到"） */
        private final List<String> invoked = Collections.synchronizedList(new ArrayList<String>());

        @Tool("echo the given text")
        public String echo(String text) {
            invoked.add("echo");
            return "echo:" + text;
        }

        @Tool("add two numbers")
        public int add(int a, int b) {
            invoked.add("add");
            return a + b;
        }

        @Tool("submit the plan")
        public String submitPlan(String items) {
            invoked.add("submitPlan");
            return "ok";
        }
    }

    /** 收窄桩：只按名单保留（只能删），并记录收到的 agentKey 与输入清单；四种异常形态各有开关 */
    private static final class StubFilter implements AgentToolSurfaceFilter {
        private final List<String> keep;
        private final List<String> invented;
        private final RuntimeException failure;
        private final boolean nullResult;
        private final boolean emptyResult;
        private String seenAgentKey;
        private List<String> seenResolvedNames;
        private int calls;

        private StubFilter(List<String> keep, List<String> invented, RuntimeException failure,
                           boolean nullResult, boolean emptyResult) {
            this.keep = keep;
            this.invented = invented;
            this.failure = failure;
            this.nullResult = nullResult;
            this.emptyResult = emptyResult;
        }

        /** 正常腿：只保留名单内的工具 */
        static StubFilter keep(String... names) {
            return new StubFilter(Arrays.asList(names), null, null, false, false);
        }

        /** 越权腿：交出的清单里有本次没收的工具名 */
        static StubFilter inventing(String... names) {
            return new StubFilter(null, Arrays.asList(names), null, false, false);
        }

        static StubFilter failing(RuntimeException failure) {
            return new StubFilter(null, null, failure, false, false);
        }

        static StubFilter returningNull() {
            return new StubFilter(null, null, null, true, false);
        }

        static StubFilter returningEmpty() {
            return new StubFilter(null, null, null, false, true);
        }

        @Override
        public List<ToolSpecification> restrict(String agentKey, List<ToolSpecification> resolved) {
            calls++;
            seenAgentKey = agentKey;
            seenResolvedNames = ToolAgentRunner.toolNamesOf(resolved);
            if (failure != null) {
                throw failure;
            }
            if (nullResult) {
                return null;
            }
            if (emptyResult) {
                return Collections.emptyList();
            }
            List<ToolSpecification> out = new ArrayList<>();
            if (invented != null) {
                for (String name : invented) {
                    out.add(spec(name));
                }
                return out;
            }
            for (ToolSpecification spec : resolved) {
                if (keep.contains(spec.name())) {
                    out.add(spec);
                }
            }
            return out;
        }
    }

    /** 收装配单清单与工具返回文本 */
    private static final class RecordingListener implements AgentLoopListener {
        private final List<List<String>> reports = new ArrayList<>();
        private final List<String> toolResults = new ArrayList<>();

        @Override
        public void log(String line) {
            // 过程日志非本测试关注点
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public void onToolsResolved(List<String> toolNames, String promptText) {
            reports.add(new ArrayList<>(toolNames));
        }

        @Override
        public void onToolResult(String toolCallId, String toolName, String resultText) {
            toolResults.add(toolName + "→" + resultText);
        }
    }

    @After
    public void clearSeams() {
        BeanUtil.setContext(null);
        ModelCallContexts.clear();
    }

    // ---------------- ① 没装过滤器＝零行为 ----------------

    @Test
    public void 未装过滤器bean时工具面逐字符不变() {
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = scripted();

        new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals("无过滤器时清单＝三只工具全在",
                names("echo", "add", "submitPlan"), sorted(specsOf(model)));
        Assert.assertEquals("装配单与发给模型的 schema 同序同集合", specsOf(model), listener.reports.get(0));
    }

    // ---------------- ② 三处同源 ----------------

    @Test
    public void 收窄后发给模型的schema装配单与准入名单同源() {
        stubFilter(StubFilter.keep("echo", "add"));
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = scripted();

        new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals("🔴 submitPlan 被收窄后，模型那一轮请求里就没有它",
                names("echo", "add"), sorted(specsOf(model)));
        Assert.assertEquals("装配单记的也是收窄后的那份（不是完整面）",
                names("echo", "add"), sorted(listener.reports.get(0)));
    }

    /** 引擎按上下文预算追加的回捞工具也在收窄输入里：平台勾得掉，勾掉后会话照跑完 */
    @Test
    public void 引擎追加的回捞工具也在收窄输入内并可被勾掉() {
        StubFilter filter = StubFilter.keep("echo");
        stubFilter(filter);
        ScriptedModel model = scripted();
        AgentLoopOptions budgeted = new AgentLoopOptions(5, 20);
        budgeted.setContextTokenBudget(24000);

        AgentLoopResult result = new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), budgeted, new RecordingListener());

        Assert.assertTrue("过滤器收到的输入含引擎追加件（否则平台勾不到它）",
                filter.seenResolvedNames.contains(AgentToolRecall.TOOL_NAME));
        Assert.assertFalse("回捞工具被勾掉后，模型手里真的没有",
                specsOf(model).contains(AgentToolRecall.TOOL_NAME));
        Assert.assertEquals("结论：已完成", result.getText());
    }

    // ---------------- ③ 四条形态闸 ----------------

    /** 空表＝"这一档一个都不勾"（三态第二态），照用；与"返回 null＝不过滤"严格区分 */
    @Test
    public void 收窄到空表是合法结果() {
        stubFilter(StubFilter.returningEmpty());
        ScriptedModel model = new ScriptedModel(ScriptedModel.Step.done("结论：一个工具都没给"));
        RecordingListener listener = new RecordingListener();

        AgentLoopResult result = new ToolAgentRunner().run(model, null, "直接给结论",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), listener);

        Assert.assertTrue("发给模型的 schema 是空的", model.views.get(0).specs.isEmpty());
        Assert.assertTrue("装配单也是空清单（不是没记这一笔）", listener.reports.get(0).isEmpty());
        Assert.assertEquals("结论：一个工具都没给", result.getText());
    }

    @Test
    public void 过滤器返回null表示不过滤() {
        stubFilter(StubFilter.returningNull());
        ScriptedModel model = scripted();

        new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20),
                new RecordingListener());

        Assert.assertEquals("null＝不过滤，与空表（＝全关）是两件事",
                names("echo", "add", "submitPlan"), sorted(specsOf(model)));
    }

    /**
     * 🔴 fail open：收窄层读不到数据时退回完整工具面——结构性上限在代码类型上，
     * 这里损失的是"额外收紧"，不该把会话做成"手里没工具"。
     */
    @Test
    public void 过滤器抛异常退回完整工具面且会话照跑() {
        stubFilter(StubFilter.failing(new IllegalStateException("定义表读不到：台账库连接失败")));
        ScriptedModel model = scripted();
        RecordingListener listener = new RecordingListener();

        AgentLoopResult result = new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals(names("echo", "add", "submitPlan"), sorted(specsOf(model)));
        Assert.assertEquals("结论：已完成", result.getText());
        Assert.assertFalse("会话里的工具照样执行得到", listener.toolResults.isEmpty());
    }

    @Test
    public void 过滤器发明工具被拒绝整次过滤作废() {
        stubFilter(StubFilter.inventing("recallToolResult", "totallyNewTool"));
        ScriptedModel model = scripted();

        new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20),
                new RecordingListener());

        List<String> kept = specsOf(model);
        Assert.assertEquals("勾选只能减：出现未入参的名字＝越权加工具，整次过滤退回原清单",
                names("echo", "add", "submitPlan"), sorted(kept));
        Assert.assertFalse(kept.contains("totallyNewTool"));
    }

    // ---------------- ④ 执行期准入 ----------------

    /**
     * 🔴 验收②的运行期那条腿：模型幻觉出一只已被收窄的工具名，反射面找得到但准入名单没有＝执行不到，
     * 且失败文本指回可用清单（不给错误文本，模型会原地反复重试同一只）。
     */
    @Test
    public void 幻觉出被收窄的工具名时执行期被拒() {
        ProbeTools tools = new ProbeTools();
        stubFilter(StubFilter.keep("echo"));
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "submitPlan", "{\"items\":\"a\"}"),
                ScriptedModel.Step.tool("c2", "echo", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "先调 submitPlan",
                Collections.<Object>singletonList(tools), new AgentLoopOptions(5, 20), listener);

        Assert.assertFalse("被收窄的工具一次都没真执行", tools.invoked.contains("submitPlan"));
        Assert.assertTrue(tools.invoked.contains("echo"));
        Assert.assertEquals(2, listener.toolResults.size());
        Assert.assertTrue("失败文本要指回可用清单：" + listener.toolResults.get(0),
                listener.toolResults.get(0).contains("【工具调用失败】")
                        && listener.toolResults.get(0).contains("未启用"));
        Assert.assertEquals("echo→echo:hi", listener.toolResults.get(1));
    }

    // ---------------- 会话身份 ----------------

    @Test
    public void 过滤器收到会话身份里的agentKey() {
        StubFilter filter = StubFilter.keep("echo");
        stubFilter(filter);
        ModelCallContexts.set(new ModelCallContext("s-1", "inkhub-check", "bidrAdmin"));

        new ToolAgentRunner().run(scripted(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20),
                new RecordingListener());

        Assert.assertEquals("收窄按 agent 判，身份从会话上下文显式交给过滤器",
                "inkhub-check", filter.seenAgentKey);
        Assert.assertEquals("工具面只在定形那一刻收窄一次", 1, filter.calls);
    }

    /** 直连小请求/无上下文：agentKey 为 null 也要把过滤器走一遍（"无身份＝放行"由实现方判，框架不替它判） */
    @Test
    public void 无会话身份时agentKey为null仍走过滤器() {
        StubFilter filter = StubFilter.keep("echo");
        stubFilter(filter);

        new ToolAgentRunner().run(scripted(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20),
                new RecordingListener());

        Assert.assertNull(filter.seenAgentKey);
        Assert.assertEquals(1, filter.calls);
    }

    // ---------------- 脚手架 ----------------

    private static ScriptedModel scripted() {
        return new ScriptedModel(ScriptedModel.Step.tool("c1", "echo", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));
    }

    private static void stubFilter(AgentToolSurfaceFilter filter) {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean(AgentToolSurfaceFilter.class)).thenReturn(filter);
        BeanUtil.setContext(ctx);
    }

    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder().name(name).build();
    }

    /** 首轮发给模型的工具名清单（收窄后的真值） */
    private static List<String> specsOf(ScriptedModel model) {
        return ToolAgentRunner.toolNamesOf(model.views.get(0).specs);
    }

    /** 反射拿到的方法顺序不保证＝声明顺序，所以清单比对一律先排序（这里钉的是"有什么"，不是"谁在前"） */
    private static List<String> names(String... values) {
        List<String> list = new ArrayList<>(Arrays.asList(values));
        Collections.sort(list);
        return list;
    }

    private static List<String> sorted(List<String> values) {
        List<String> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }
}
