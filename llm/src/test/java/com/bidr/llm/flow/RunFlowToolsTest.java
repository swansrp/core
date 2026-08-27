package com.bidr.llm.flow;

import com.bidr.llm.agent.tools.RunFlowTools;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Title: RunFlowToolsTest
 * Description: run_flow 工具守卫用例（计划 Test Plan 5）：递归拒绝（深度标记
 * 跨线程同步到执行线程，flow 节点内再嵌 run_flow 直接拒绝）/ 流式链拒绝 /
 * flowKey·入参·停止守卫。FlowEngine 以最小 stub 覆写 loadFlow/execute，不起容器；
 * 本包测试可直接构造包私有的 LoadedFlow
 *
 * @author Sharp
 * @since 2026/8/20
 */
public class RunFlowToolsTest {

    /** 最小引擎 stub：loadFlow 返回可控 graph，execute 模拟节点动作（action 在执行线程上回调） */
    private static FlowEngine stubEngine(BiConsumer<String, FlowContext> action, FlowGraph graph) {
        return new FlowEngine(null, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()) {
            @Override
            public LoadedFlow loadFlow(String flowKey) {
                return graph == null ? null : new LoadedFlow(graph, false, flowKey);
            }

            @Override
            public void execute(String flowKey, FlowContext context) {
                if (action != null) {
                    action.accept(flowKey, context);
                }
            }
        };
    }

    private static FlowGraph emptyGraph() {
        FlowGraph g = new FlowGraph();
        g.setNodes(new ArrayList<>());
        return g;
    }

    private static FlowGraph streamLlmGraph() {
        FlowGraph g = new FlowGraph();
        FlowGraph.FlowNode n = new FlowGraph.FlowNode();
        n.setType("llm");
        n.setName("ask");
        Map<String, Object> config = new HashMap<>();
        config.put("stream", true);
        n.setConfig(config);
        List<FlowGraph.FlowNode> nodes = new ArrayList<>();
        nodes.add(n);
        g.setNodes(nodes);
        return g;
    }

    /** 守卫②：flow 节点内再嵌 run_flow 必须被拒绝（执行线程深度标记），外层调用正常收口 */
    @Test
    public void nestedCallRejectedInsideFlow() {
        final RunFlowTools[] holder = new RunFlowTools[1];
        AtomicReference<String> nested = new AtomicReference<>();
        RunFlowTools tools = new RunFlowTools(stubEngine((key, ctx) ->
                // 模拟 flow 节点执行中调用 run_flow（发生在 agent-run-flow 执行线程上）
                nested.set(holder[0].runFlow(key, null)), emptyGraph()), null);
        holder[0] = tools;
        String outer = tools.runFlow("asset-gen", null);
        Assert.assertTrue("外层应正常完成: " + outer, outer.contains("\"ok\":true"));
        Assert.assertNotNull("嵌套调用应发生", nested.get());
        Assert.assertTrue("嵌套调用应被深度守卫拒绝: " + nested.get(),
                nested.get().contains("不允许嵌套调用"));
    }

    /** 深度标记用后即清：外层连续两次调用互不影响 */
    @Test
    public void depthClearedAfterCall() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, emptyGraph()), null);
        Assert.assertTrue(tools.runFlow("k", null).contains("\"ok\":true"));
        Assert.assertTrue("第二次外层调用不应被残留深度拒绝",
                tools.runFlow("k", null).contains("\"ok\":true"));
    }

    /** 守卫①：含流式 llm 结点的链拒绝同步调用 */
    @Test
    public void streamFlowRejected() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, streamLlmGraph()), null);
        String r = tools.runFlow("chat", null);
        Assert.assertTrue("流式链应被拒绝: " + r, r.contains("不支持 run_flow 同步调用"));
    }

    /** 流程不存在 */
    @Test
    public void missingFlowRejected() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, null), null);
        Assert.assertTrue(tools.runFlow("nope", null).contains("不存在"));
    }

    /** flowKey 空拒绝 */
    @Test
    public void blankFlowKeyRejected() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, emptyGraph()), null);
        Assert.assertTrue(tools.runFlow("   ", null).contains("flowKey 不能为空"));
    }

    /** inputJson 结构守卫：非对象 / 非法 JSON */
    @Test
    public void badInputJsonRejected() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, emptyGraph()), null);
        Assert.assertTrue(tools.runFlow("k", "[1,2]").contains("inputJson 必须是 JSON 对象"));
        Assert.assertTrue(tools.runFlow("k", "bad json").contains("inputJson 不是合法 JSON"));
    }

    /** 停止信号下拒绝执行 */
    @Test
    public void stopSignalRejected() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, emptyGraph()), () -> true);
        Assert.assertTrue(tools.runFlow("k", null).contains("任务已被用户停止"));
    }

    /** 正常同步链：ok=true + 流程标识回带 */
    @Test
    public void happyPath() {
        RunFlowTools tools = new RunFlowTools(stubEngine(null, emptyGraph()), null);
        String r = tools.runFlow("asset-gen", "{\"agentCode\":\"demo\",\"mode\":\"skeleton\"}");
        Assert.assertTrue("应成功: " + r, r.contains("\"ok\":true"));
        Assert.assertTrue(r.contains("\"flowKey\":\"asset-gen\""));
    }
}
