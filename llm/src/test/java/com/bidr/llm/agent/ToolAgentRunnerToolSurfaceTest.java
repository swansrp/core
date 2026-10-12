package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Title: ToolAgentRunnerToolSurfaceTest
 * Description: M14 棒 2「工具面定形上报」钩子契约测试。锁死三件事：① 装配单正源＝<b>真的发给模型的那份
 * specs</b>（不是业务侧自己推导的清单——上下文治理开启时引擎还会追加 {@code recallToolResult}，
 * 业务侧推导必然少报一只）；② 上报失败绝不影响会话（台账是旁证，不是链路）；③ 钩子是 default 方法，
 * 老实现者与无台账形态零行为变化。全部用假模型，不依赖任何外部模型服务。
 *
 * @author Sharp
 * @since 2026-10-12
 */
public class ToolAgentRunnerToolSurfaceTest {

    /** 两只被测工具（方法名即工具名，声明顺序即 specs 顺序） */
    public static class ProbeTools {

        @Tool("echo the given text")
        public String echo(String text) {
            return "echo:" + text;
        }

        @Tool("add two numbers")
        public int add(int a, int b) {
            return a + b;
        }
    }

    /** 记录上报的工具面清单，其余钩子按既有默认 */
    private static final class SurfaceListener implements AgentLoopListener {

        private final List<List<String>> reports = new ArrayList<>();
        private final boolean fail;

        SurfaceListener(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void log(String line) {
            // 过程日志非本测试关注点
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public void onToolsResolved(List<String> toolNames) {
            if (fail) {
                throw new IllegalStateException("台账写库炸了");
            }
            reports.add(new ArrayList<>(toolNames));
        }
    }

    private static ScriptedModel oneCallThenAnswer() {
        return new ScriptedModel(ScriptedModel.Step.tool("c1", "echo", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));
    }

    @Test
    public void 上报清单与发给模型的specs逐字一致() {
        SurfaceListener listener = new SurfaceListener(false);
        ScriptedModel model = oneCallThenAnswer();

        new ToolAgentRunner().run(model, null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals("工具面只在定形那一刻上报一次", 1, listener.reports.size());
        List<String> expected = new ArrayList<>();
        for (ToolSpecification spec : model.views.get(0).specs) {
            expected.add(spec.name());
        }
        Assert.assertEquals("装配单必须等于模型实际拿到的工具面（含顺序）", expected, listener.reports.get(0));
        Assert.assertTrue("业务侧两只工具都要在案", listener.reports.get(0).containsAll(Arrays.asList("echo", "add")));
    }

    /**
     * 🔴 本棒存在理由：token 预算一开（生产形态）引擎会往 specs 里追加回捞工具，
     * 业务侧 {@code tools(...)} 返回的对象清单里根本没有它。若装配单去业务侧自行推导就会系统性少报，
     * 这条腿钉住"清单必须来自 runner"。
     */
    @Test
    public void 开启上下文治理时引擎追加的回捞工具也在清单内() {
        SurfaceListener on = new SurfaceListener(false);
        AgentLoopOptions budgeted = new AgentLoopOptions(5, 20);
        budgeted.setContextTokenBudget(24000);
        new ToolAgentRunner().run(oneCallThenAnswer(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), budgeted, on);
        Assert.assertTrue("治理开启态须含引擎追加的 recallToolResult",
                on.reports.get(0).contains(AgentToolRecall.TOOL_NAME));

        SurfaceListener off = new SurfaceListener(false);
        new ToolAgentRunner().run(oneCallThenAnswer(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), off);
        Assert.assertFalse("关闭态零追加（I9：默认形态逐字不变）",
                off.reports.get(0).contains(AgentToolRecall.TOOL_NAME));
    }

    @Test
    public void 上报失败不影响会话跑完() {
        SurfaceListener failing = new SurfaceListener(true);
        AgentLoopResult result = new ToolAgentRunner().run(oneCallThenAnswer(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), failing);
        Assert.assertNotNull("台账异常不得掐断会话", result);
        Assert.assertEquals("结论：已完成", result.getText());
    }

    /** 会话链路：台账 Bean 缺席（离线单测无 Spring 上下文）时上报整段静默跳过、会话照跑 */
    @Test
    public void 会话监听器无台账bean时零行为() {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId("s-surface");
        state.setAgentKey("probe");
        state.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.saveState(state);
        AgentSessionContext ctx = new AgentSessionContext(state, store, new HashMap<String, Object>());

        AgentLoopResult result = new ToolAgentRunner().run(oneCallThenAnswer(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20), ctx.loopListener());

        Assert.assertNotNull(result);
        Assert.assertEquals("会话事件流不受上报钩子影响", 1, countType(store, "s-surface", AgentEvent.TOOL_CALL));
        Assert.assertEquals(1, countType(store, "s-surface", AgentEvent.TOOL_RESULT));
    }

    private static int countType(InMemoryAgentSessionStore store, String sessionId, String type) {
        int n = 0;
        for (AgentEvent ev : store.events(sessionId, 0)) {
            if (type.equals(ev.getType())) {
                n++;
            }
        }
        return n;
    }

    /** 老实现者零改动：只实现 log/shouldStop 的监听器不得因为新增钩子编译或运行失败 */
    @Test
    public void 老监听器不实现新钩子仍可运行() {
        AgentLoopResult result = new ToolAgentRunner().run(oneCallThenAnswer(), null, "请调用 echo",
                Collections.<Object>singletonList(new ProbeTools()), new AgentLoopOptions(5, 20),
                new AgentLoopListener() {
                    @Override
                    public void log(String line) {
                    }

                    @Override
                    public boolean shouldStop() {
                        return false;
                    }
                });
        Assert.assertNotNull(result);
    }

    @Test
    public void 清单提取保序去重跳过无名() {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.add(spec("beta"));
        specs.add(spec("alpha"));
        specs.add(spec("beta"));
        specs.add(null);
        Assert.assertEquals(Arrays.asList("beta", "alpha"), ToolAgentRunner.toolNamesOf(specs));
        Assert.assertTrue(ToolAgentRunner.toolNamesOf(null).isEmpty());
    }

    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder().name(name).build();
    }
}
