package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Title: ToolAgentRunnerToolEventTest
 * Description: 工具循环**结构化过程事件**上报契约测试（2026-09-24 补）：
 * 此前 {@code AgentEvent.TOOL_CALL/TOOL_RESULT} 只有常量、全仓零发射点，前端按该契约写的
 * 过程树拿不到工具步。本测试锁死"每次工具执行必发一对 call/result、且 id 可配对"，
 * 并验证 {@link AgentLoopListener} 新增钩子为 default 方法（老实现者不受影响）。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class ToolAgentRunnerToolEventTest {

    /** 第一轮要工具、第二轮直出结论的假模型（不依赖任何外部服务） */
    private static final class ScriptedModel implements ChatLanguageModel {

        private int round;

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return generate(messages, Collections.<ToolSpecification>emptyList());
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            round++;
            if (round == 1) {
                return Response.from(AiMessage.from(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("echo")
                        .arguments("{\"text\":\"hi\"}")
                        .build()), new TokenUsage(10, 5));
            }
            return Response.from(AiMessage.from("结论：已完成"), new TokenUsage(3, 2));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
            return generate(messages, Collections.singletonList(toolSpecification));
        }
    }

    /** 被测工具集（方法名即工具名） */
    public static class EchoTools {

        @Tool("echo the given text")
        public String echo(String text) {
            return "echo:" + text;
        }
    }

    /** 记录结构化事件的监听器 */
    private static final class RecordingListener implements AgentLoopListener {

        private final List<String[]> calls = new ArrayList<>();
        private final List<String[]> results = new ArrayList<>();

        @Override
        public void log(String line) {
            // 过程日志非本测试关注点
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public void onToolCall(String toolCallId, String toolName, String argumentsJson) {
            calls.add(new String[]{toolCallId, toolName, argumentsJson});
        }

        @Override
        public void onToolResult(String toolCallId, String toolName, String resultText) {
            results.add(new String[]{toolCallId, toolName, resultText});
        }
    }

    @Test
    public void 每次工具执行应成对上报调用与返回() {
        RecordingListener listener = new RecordingListener();

        AgentLoopResult result = new ToolAgentRunner()
                .run(new ScriptedModel(), null, "请调用 echo", Collections.<Object>singletonList(new EchoTools()),
                        new AgentLoopOptions(5, 20), listener);

        Assert.assertNotNull(result);
        Assert.assertEquals("应上报 1 次工具调用", 1, listener.calls.size());
        Assert.assertEquals("应上报 1 次工具返回", 1, listener.results.size());

        String[] call = listener.calls.get(0);
        Assert.assertEquals("call-1", call[0]);
        Assert.assertEquals("echo", call[1]);
        Assert.assertEquals("{\"text\":\"hi\"}", call[2]);

        String[] ret = listener.results.get(0);
        Assert.assertEquals("返回须与调用同 id 以便前端配对", "call-1", ret[0]);
        Assert.assertEquals("echo", ret[1]);
        Assert.assertEquals("echo:hi", ret[2]);
    }

    @Test
    public void 老监听器无新钩子实现仍可运行() {
        // AgentLoopListener 的 onToolCall/onToolResult 为 default：只实现 log/shouldStop 的既有链路不得编译或运行失败
        AgentLoopListener legacy = new AgentLoopListener() {
            @Override
            public void log(String line) {
            }

            @Override
            public boolean shouldStop() {
                return false;
            }
        };
        RecordingListener probe = new RecordingListener();
        probe.onToolCall("x", "y", "{}");
        Assert.assertEquals(1, probe.calls.size());

        AgentLoopResult result = new ToolAgentRunner()
                .run(new ScriptedModel(), null, "请调用 echo", Collections.<Object>singletonList(new EchoTools()),
                        new AgentLoopOptions(5, 20), legacy);
        Assert.assertNotNull("仅实现旧两个方法的监听器应能跑完整个循环", result);
    }
}
