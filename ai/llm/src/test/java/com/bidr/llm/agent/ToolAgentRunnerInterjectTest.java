package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Title: ToolAgentRunnerInterjectTest
 * Description: 运行期插话注入契约测试（U81，2026-11-19）：interject 端点入队＋GUIDANCE 上屏
 * 早已通（d002a135），缺的是「下一轮循环头注入模型上下文」这一条腿——本测试锁死：
 * ① 监听器给出插话时，下一轮请求的消息里出现带【用户插话】前缀的用户消息（一次性消费）；
 * ② 默认监听器（无插话）请求消息不含插话前缀（零行为变化）。
 *
 * @author sharp
 * @since 2026/11/19
 */
public class ToolAgentRunnerInterjectTest {

    /** 第一轮要工具、第二轮直出结论的假模型，逐轮记录请求消息供断言 */
    private static final class CapturingModel implements ChatModel {

        private int round;
        final List<List<ChatMessage>> rounds = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            rounds.add(new ArrayList<>(request.messages()));
            round++;
            if (round == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("echo")
                                .arguments("{\"text\":\"hi\"}")
                                .build()))
                        .tokenUsage(new TokenUsage(10, 5))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("结论：已完成"))
                    .tokenUsage(new TokenUsage(3, 2))
                    .build();
        }
    }

    /** 被测工具集（方法名即工具名） */
    public static class EchoTools {

        @Tool("echo the given text")
        public String echo(String text) {
            return "echo:" + text;
        }
    }

    /** 第 1 个循环头给出插话、之后为 null 的监听器（模拟存储一次性消费） */
    private static final class OneShotInterjectionListener implements AgentLoopListener {

        private boolean consumed;

        @Override
        public void log(String line) {
            // 过程日志非本测试关注点
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public String pendingInterjection() {
            if (consumed) {
                return null;
            }
            consumed = true;
            return "标段改为 L35";
        }
    }

    /** 只实现既有方法的监听器：pendingInterjection 走 default null＝零行为变化 */
    private static final class LegacyListener implements AgentLoopListener {

        @Override
        public void log(String line) {
            // 非关注点
        }

        @Override
        public boolean shouldStop() {
            return false;
        }
    }

    private static String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m instanceof UserMessage) {
                return ((UserMessage) m).singleText();
            }
        }
        return "";
    }

    /** 统计一轮请求消息里带【用户插话】前缀的用户消息条数 */
    private static int countInterjections(List<ChatMessage> messages) {
        int n = 0;
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage
                    && ((UserMessage) m).singleText().startsWith("【用户插话】")) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void 插话应在循环头注入为带前缀的用户消息且只消费一次() {
        CapturingModel model = new CapturingModel();

        AgentLoopResult result = new ToolAgentRunner()
                .run(model, null, "请调用 echo", Collections.<Object>singletonList(new EchoTools()),
                        new AgentLoopOptions(5, 20), new OneShotInterjectionListener());

        Assert.assertNotNull(result);
        Assert.assertEquals("两轮跑完", 2, model.rounds.size());
        // 插话在它被给出后的第一个循环头注入（首轮头已排队即首轮注入；生产时序下首轮 LLM
        // 请求在途时用户插话，插入点＝第二次请求，同为此口）；带【用户插话】前缀
        String round1User = lastUserText(model.rounds.get(0));
        Assert.assertTrue("第一个循环头应出现【用户插话】前缀消息", round1User.contains("【用户插话】"));
        Assert.assertTrue("插话原文应完整进入上下文", round1User.contains("标段改为 L35"));
        // 一次性消费：注入一条后留在历史（对话记忆，正当），但不得出现第二条注入
        Assert.assertEquals("首轮注入恰一条", 1, countInterjections(model.rounds.get(0)));
        Assert.assertEquals("下一轮不再新增注入（历史留 1 条）", 1, countInterjections(model.rounds.get(1)));
    }

    @Test
    public void 无插话时请求消息不含插话前缀() {
        CapturingModel model = new CapturingModel();

        new ToolAgentRunner()
                .run(model, null, "请调用 echo", Collections.<Object>singletonList(new EchoTools()),
                        new AgentLoopOptions(5, 20), new LegacyListener());

        Assert.assertEquals(2, model.rounds.size());
        Assert.assertEquals("默认监听器零行为变化", 0, countInterjections(model.rounds.get(1)));
    }
}
