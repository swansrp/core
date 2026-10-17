package com.bidr.llm.agent;

import com.bidr.kernel.utils.BeanUtil;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ToolAgentRunnerFunctionalToolTest
 * Description: 函数式工具通道（档三前置实证）的框架侧契约测试。这条通道唯一的存在理由是"工具形态可以被
 * 纯数据描述"，所以七条腿全部围绕<b>它与反射面的等价性</b>来钉，而不是围绕它能不能跑：
 * ① 声明式 spec 与同签名反射 spec <b>逐字段相等</b>（type/properties/required 三件正是台账行存得下的形态）；
 * ② 声明件真被执行到（参数 JSON→Map、返回文本进工具结果消息与事件流），null 返回与"无返回值"同形；
 * ③④ 失败文本与反射腿<b>一字不差</b>（handler 抛错、参数不是 JSON 对象）——模型看到的自纠提示不许随通道变；
 * ⑤ 🔴 平台勾选对两条通道是同一把闸：声明件被勾掉后模型手里没有它、幻觉出名字也执行不到；
 * ⑥ 🔴 同名冲突＝代码优先：反射面已有该名字时声明件整份失效，不进面、也不进索引；
 * ⑦ 零漂移＝不传声明清单时 specs 与执行结果与改造前逐条一致（含 null 清单不 NPE）。
 * 全程 {@link ScriptedModel} 假模型，不连任何外部模型服务。
 *
 * @author Sharp
 * @since 2026-10-13
 */
public class ToolAgentRunnerFunctionalToolTest {

    /** 反射面对照件：一个 String 参、一个两参基本类型 */
    public static class ReflectedTools {

        @Tool("echo the given text")
        public String echo(String text) {
            return "echo:" + text;
        }

        @Tool("add two numbers")
        public int add(int a, int b) {
            return a + b;
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
    }

    // ---------------- ① 声明式 spec ＝ 反射 spec ----------------

    @Test
    public void 声明式spec与同签名反射spec逐字段相等() {
        // 反射面顺序不保证＝声明顺序（实测 get(0) 给的是 add），一律按名字取对照件
        ToolSpecification reflected = specNamed(
                ToolSpecifications.toolSpecificationsFrom(ReflectedTools.class), "echo");

        // 声明面按"数据行里会怎么写"手写一遍：type + properties + required，不借反射产出物反抄
        FunctionalTool declared = FunctionalTool.of("echo", "echo the given text",
                objectParams("text", "string"), args -> "echo:" + args.get("text"));

        Assert.assertEquals("🔴 声明三件套拼出的 spec 与反射面完全相等（相等才谈得上『配置化替换』）",
                reflected, declared.spec());
        Assert.assertEquals("参数 schema 单独也比一次（equals 之外的第二证：形态字段本身可搬运）",
                reflected.parameters(), declared.parameters());
    }

    // ---------------- ② 声明件真被执行到 ----------------

    @Test
    public void 声明式工具被模型调用并回填结果() {
        final List<Map<String, Object>> seenArgs = new ArrayList<>();
        FunctionalTool upper = FunctionalTool.of("upperCase", "uppercase the given text",
                objectParams("text", "string"),
                args -> {
                    seenArgs.add(new LinkedHashMap<String, Object>(args));
                    String text = (String) args.get("text");
                    return text == null ? null : text.toUpperCase();
                });
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "upperCase", "{\"text\":\"hi there\"}"),
                ScriptedModel.Step.done("结论：已完成"));
        RecordingListener listener = new RecordingListener();

        AgentLoopResult result = new ToolAgentRunner().run(model, "sys", "请调用 upperCase",
                Collections.emptyList(), Collections.singletonList(upper), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals("结论：已完成", result.getText());
        Assert.assertEquals("处理器收到的是 JSON 原值展开的扁平 Map",
                Collections.<String, Object>singletonMap("text", "hi there"), seenArgs.get(0));
        Assert.assertEquals("返回文本原样回填给模型",
                Collections.singletonList("upperCase→HI THERE"), listener.toolResults);
        Assert.assertEquals("装配单记的是同一份面（声明件与反射件不分两本账）",
                Collections.singletonList("upperCase"), listener.reports.get(0));
    }

    /** handler 返回 null 与反射腿"方法无返回值"同形 */
    @Test
    public void 处理器返回null时与反射腿无返回值同文本() {
        FunctionalTool silent = FunctionalTool.of("silent", "no return",
                objectParams(), args -> null);
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "silent", "{}"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "调一下 silent", Collections.emptyList(),
                Collections.singletonList(silent), new AgentLoopOptions(5, 20), listener);

        Assert.assertEquals("silent→（工具无返回值）", listener.toolResults.get(0));
    }

    // ---------------- ③④ 失败文本同形 ----------------

    @Test
    public void 处理器抛错时错误文案与反射腿同形() {
        FunctionalTool boom = FunctionalTool.of("boom", "always fails",
                objectParams("text", "string"),
                args -> {
                    throw new IllegalStateException("业务闸口拒绝：草稿已被锁定");
                });
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "boom", "{\"text\":\"x\"}"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "调一下 boom", Collections.emptyList(),
                Collections.singletonList(boom), new AgentLoopOptions(5, 20), listener);

        String text = listener.toolResults.get(0);
        Assert.assertTrue("异常类型与 message 都要交给模型（否则它无从自纠）: " + text,
                text.contains("【工具调用失败】boom 执行异常：IllegalStateException：业务闸口拒绝：草稿已被锁定"));
        Assert.assertTrue("收尾建议语与反射腿同一条: " + text,
                text.contains("请根据错误信息调整参数后重试一次"));
    }

    @Test
    public void 参数不是JSON对象时与反射腿同条解析失败文案() {
        FunctionalTool strict = FunctionalTool.of("strict", "needs an object",
                objectParams("text", "string"), args -> "不该被执行:" + args);
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "strict", "[1,2]"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "调一下 strict", Collections.emptyList(),
                Collections.singletonList(strict), new AgentLoopOptions(5, 20), listener);

        String text = listener.toolResults.get(0);
        Assert.assertTrue("非对象参数＝参数解析失败，不许静默当空参跑过去: " + text,
                text.contains("【工具调用失败】工具 'strict' 参数解析失败："));
        Assert.assertTrue(text.contains("请修正参数（合法 JSON 且类型匹配）后重试"));
    }

    // ---------------- ⑤ 勾选对两条通道同一把闸 ----------------

    @Test
    public void 平台勾选同样能关掉声明式工具且执行期调不到() {
        final boolean[] handlerRan = {false};
        FunctionalTool declaredOnly = FunctionalTool.of("declaredOnly", "只来自声明的工具",
                objectParams("text", "string"),
                args -> {
                    handlerRan[0] = true;
                    return "declared-ran";
                });
        // 反射面正常在跑，收窄只留 echo：声明件既不在模型手里，也不该被执行到
        stubFilterKeeping("echo");
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "declaredOnly", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "调一下 declaredOnly",
                Collections.<Object>singletonList(new ReflectedTools()),
                Collections.singletonList(declaredOnly), new AgentLoopOptions(5, 20), listener);

        Assert.assertFalse("声明件被勾掉后，发给模型的 schema 里就没有它",
                namesOf(model).contains("declaredOnly"));
        Assert.assertFalse("而且执行期真的调不到（不是只藏了 schema）", handlerRan[0]);
        Assert.assertTrue("失败文本走的是同一条『未启用』话术（不给出口模型会原地重试）: "
                        + listener.toolResults.get(0),
                listener.toolResults.get(0).contains("【工具调用失败】")
                        && listener.toolResults.get(0).contains("未启用"));
    }

    // ---------------- ⑥ 同名冲突＝代码优先 ----------------

    @Test
    public void 反射面已有同名时声明件整份失效() {
        final boolean[] handlerRan = {false};
        FunctionalTool shadowing = FunctionalTool.of("echo", "配置里改写的说明（不该生效）",
                objectParams("text", "string"),
                args -> {
                    handlerRan[0] = true;
                    return "declared";
                });
        RecordingListener listener = new RecordingListener();
        ScriptedModel model = new ScriptedModel(
                ScriptedModel.Step.tool("c1", "echo", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));

        new ToolAgentRunner().run(model, null, "调一下 echo",
                Collections.<Object>singletonList(new ReflectedTools()),
                Collections.singletonList(shadowing), new AgentLoopOptions(5, 20), listener);

        List<ToolSpecification> sent = model.views.get(0).specs;
        Assert.assertEquals("面里不许出现两条同名（声明件整份不进面，不是覆盖也不是并存）: "
                        + namesOf(model),
                2, sent.size());
        Assert.assertEquals("echo 那份的说明仍是代码里那份（配置不得顶掉已上线行为）",
                "echo the given text", specNamed(sent, "echo").description());
        Assert.assertFalse("执行走反射腿，handler 一次都没被调到", handlerRan[0]);
        Assert.assertEquals("echo→echo:hi", listener.toolResults.get(0));
    }

    // ---------------- ⑦ 零漂移 ----------------

    @Test
    public void 不传声明清单时与改造前逐条一致() {
        ReflectedTools tools = new ReflectedTools();

        ScriptedModel emptyList = scriptedWithEcho();
        RecordingListener emptyListener = new RecordingListener();
        AgentLoopResult r1 = new ToolAgentRunner().run(emptyList, "sys", "请调用 echo",
                Collections.<Object>singletonList(tools), Collections.<FunctionalTool>emptyList(),
                new AgentLoopOptions(5, 20), emptyListener);

        ScriptedModel nullList = scriptedWithEcho();
        RecordingListener nullListener = new RecordingListener();
        AgentLoopResult r2 = new ToolAgentRunner().run(nullList, "sys", "请调用 echo",
                Collections.<Object>singletonList(tools), null, new AgentLoopOptions(5, 20), nullListener);

        ScriptedModel legacy = scriptedWithEcho();
        RecordingListener legacyListener = new RecordingListener();
        AgentLoopResult r3 = new ToolAgentRunner().run(legacy, "sys", "请调用 echo",
                Collections.<Object>singletonList(tools), new AgentLoopOptions(5, 20), legacyListener);

        Assert.assertEquals("空清单／null／旧重载三份 specs 逐条一致（null 不 NPE）",
                namesOf(legacy), namesOf(emptyList));
        Assert.assertEquals(namesOf(legacy), namesOf(nullList));
        Assert.assertEquals("三条链路的工具返回文本一致", legacyListener.toolResults, emptyListener.toolResults);
        Assert.assertEquals(legacyListener.toolResults, nullListener.toolResults);
        Assert.assertEquals(r3.getText(), r1.getText());
        Assert.assertEquals(r3.getRounds(), r2.getRounds());
    }

    // ---------------- 脚手架 ----------------

    private static ScriptedModel scriptedWithEcho() {
        return new ScriptedModel(ScriptedModel.Step.tool("c1", "echo", "{\"text\":\"hi\"}"),
                ScriptedModel.Step.done("结论：已完成"));
    }

    /** 参数 schema 的简写工厂：name/type 成对给出，required＝全部（与反射面默认一致） */
    private static ToolParameters objectParams(String... nameAndTypePairs) {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (int i = 0; i + 1 < nameAndTypePairs.length; i += 2) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", nameAndTypePairs[i + 1]);
            properties.put(nameAndTypePairs[i], prop);
            required.add(nameAndTypePairs[i]);
        }
        return ToolParameters.builder().type("object").properties(properties).required(required).build();
    }

    private static void stubFilterKeeping(String... names) {
        final List<String> keep = Arrays.asList(names);
        AgentToolSurfaceFilter filter = new AgentToolSurfaceFilter() {
            @Override
            public List<ToolSpecification> restrict(String agentKey, List<ToolSpecification> resolved) {
                List<ToolSpecification> out = new ArrayList<>();
                for (ToolSpecification spec : resolved) {
                    if (keep.contains(spec.name())) {
                        out.add(spec);
                    }
                }
                return out;
            }
        };
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean(AgentToolSurfaceFilter.class)).thenReturn(filter);
        BeanUtil.setContext(ctx);
    }

    private static ToolSpecification specNamed(List<ToolSpecification> specs, String name) {
        for (ToolSpecification spec : specs) {
            if (name.equals(spec.name())) {
                return spec;
            }
        }
        return null;
    }

    private static List<String> namesOf(ScriptedModel model) {
        return ToolAgentRunner.toolNamesOf(model.views.get(0).specs);
    }
}
