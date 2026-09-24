package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: ScriptedModel
 * Description: 层 1 断言层假模型（沿用 ToolAgentRunnerToolEventTest.ScriptedModel 风格，零外部依赖）：
 * 按脚本步骤产出「工具调用轮 / 结论轮」；每次 generate 前对入窗消息执行 I1 配对自检
 * （每个带 toolExecutionRequests 的 AiMessage 之后必须紧随、同序、同数量、id 一致地跟着
 * ToolExecutionResultMessage，且无孤立结果——违反即 AssertionError 使测试失败，模拟端点 400），
 * 并记录逐轮计量视图（条数/字符/估算 token/specs），供对照数字表与断言取数
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ScriptedModel implements ChatLanguageModel {

    /** 脚本一步：要么发起一组工具调用（可多个=并行形态），要么给结论文本 */
    public static final class Step {
        final List<ToolExecutionRequest> requests;
        final String finalText;

        private Step(List<ToolExecutionRequest> requests, String finalText) {
            this.requests = requests;
            this.finalText = finalText;
        }

        public static Step tool(String id, String name, String argsJson) {
            return new Step(Collections.singletonList(req(id, name, argsJson)), null);
        }

        public static Step tools(ToolExecutionRequest... reqs) {
            return new Step(Arrays.asList(reqs), null);
        }

        public static Step done(String text) {
            return new Step(null, text);
        }
    }

    public static ToolExecutionRequest req(String id, String name, String argsJson) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(argsJson).build();
    }

    /** 一轮 generate 的入窗快照（I8 校准与对照表取数入口） */
    public static final class RoundView {
        public int round;
        public int messageCount;
        public int chars;
        public int estTokens;
        public List<ChatMessage> messages;
        public List<ToolSpecification> specs;
    }

    private final List<Step> steps;
    /** I1 自检开关（默认开：全程协议不变式哨兵） */
    public boolean checkPairing = true;
    public final List<RoundView> views = new ArrayList<>();

    public ScriptedModel(List<Step> steps) {
        this.steps = steps;
    }

    public ScriptedModel(Step... steps) {
        this.steps = Arrays.asList(steps);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, Collections.<ToolSpecification>emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        if (checkPairing) {
            assertPairing(messages);
        }
        RoundView v = new RoundView();
        v.round = views.size() + 1;
        v.messageCount = messages.size();
        v.chars = ContextTokenEstimator.countChars(messages);
        v.estTokens = ContextTokenEstimator.estimateMessages(messages)
                + ContextTokenEstimator.estimateSpecs(toolSpecifications);
        v.messages = new ArrayList<>(messages);
        v.specs = toolSpecifications == null ? Collections.<ToolSpecification>emptyList()
                : new ArrayList<>(toolSpecifications);
        views.add(v);
        Step s = steps.get(Math.min(v.round - 1, steps.size() - 1));
        Response<AiMessage> resp = s.requests == null
                ? Response.from(AiMessage.from(s.finalText), new TokenUsage(10, 5))
                : Response.from(AiMessage.from(s.requests), new TokenUsage(10, 5));
        return resp;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, Collections.singletonList(toolSpecification));
    }

    /** I1 配对自检：Ai(带调用) 之后必须紧随同序同数量 id 一致的结果；不存在孤立结果 */
    static void assertPairing(List<ChatMessage> messages) {
        boolean[] consumed = new boolean[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (!(m instanceof AiMessage) || !((AiMessage) m).hasToolExecutionRequests()) {
                continue;
            }
            List<ToolExecutionRequest> reqs = ((AiMessage) m).toolExecutionRequests();
            for (int j = 0; j < reqs.size(); j++) {
                int at = i + 1 + j;
                if (at >= messages.size() || !(messages.get(at) instanceof ToolExecutionResultMessage)) {
                    throw new AssertionError("I1 违反：第 " + (i + 1) + " 条 Ai 调用（" + reqs.get(j).name()
                            + " id=" + reqs.get(j).id() + "）之后第 " + (j + 1) + " 个结果缺失/非紧邻");
                }
                ToolExecutionResultMessage r = (ToolExecutionResultMessage) messages.get(at);
                if (!reqs.get(j).id().equals(r.id())) {
                    throw new AssertionError("I1 违反：结果 id 不同序，期望 " + reqs.get(j).id() + " 实为 " + r.id());
                }
                consumed[at] = true;
            }
        }
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolExecutionResultMessage && !consumed[i]) {
                throw new AssertionError("I1 违反：第 " + (i + 1) + " 条为孤立工具结果（无对应 Ai 调用）");
            }
        }
    }
}
