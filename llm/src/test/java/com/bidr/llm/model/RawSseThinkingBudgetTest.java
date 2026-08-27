package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Title: RawSseThinkingBudgetTest
 * Description: 思考强度旋钮请求体回归：thinking_budget 为思考型模型（Qwen3 系兼容模式顶层参数）
 * 的思考 token 上限——业务侧按 Agent 配置经 LiveModelFactory 传入，null/非正=最强（不携带参数，
 * 模型默认全功率思考），正值须原样写入请求体。
 * debug 背景：评审/生成会话思考归档单轮 3-4 万字（合计 20-30 万），提示词纪律管不住思考预算，
 * 须模型层截断；旋钮上线须保证「缺省不携带」（默认最强不改变存量行为）与「设置即生效」两态。
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class RawSseThinkingBudgetTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 桩配置：仅满足构造，请求体组装不依赖其取值 */
    private static ModelConfigProvider stubProvider() {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return "http://stub";
            }

            @Override
            public String getApiKey(String purposeType, Long userId) {
                return "test-key";
            }

            @Override
            public String getModelName(String purposeType) {
                return "test-model";
            }

            @Override
            public long getTimeoutSeconds(String purposeType) {
                return 600;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return purposeType + "|stub";
            }
        };
    }

    private static JsonNode requestBodyOf(Integer thinkingBudget) throws Exception {
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(), "AGENT", null,
                thinkingBudget);
        List<ChatMessage> msgs = Collections.singletonList(UserMessage.from("hi"));
        return OM.readTree(model.buildRequestBody("qwen3.8-max", msgs, null));
    }

    /** 设置正值：请求体须携带 thinking_budget 且值原样透传 */
    @Test
    public void budgetSetWritesThinkingBudget() throws Exception {
        JsonNode body = requestBodyOf(8192);
        Assert.assertTrue("设置思考预算时请求体须携带 thinking_budget", body.has("thinking_budget"));
        Assert.assertEquals("预算值原样透传", 8192, body.path("thinking_budget").asInt());
        Assert.assertEquals("模型名不受旋钮影响", "qwen3.8-max", body.path("model").asText());
        Assert.assertTrue("stream 标记不受旋钮影响", body.path("stream").asBoolean());
    }

    /** 缺省（null）=最强：不携带 thinking_budget（存量行为不变，模型默认全功率思考） */
    @Test
    public void nullBudgetOmitsThinkingBudget() throws Exception {
        Assert.assertFalse("null=最强不携带参数", requestBodyOf(null).has("thinking_budget"));
    }

    /** 非正值视同最强：0/负数不携带（防业务侧误配 0/负数把请求打脏） */
    @Test
    public void nonPositiveBudgetOmitsThinkingBudget() throws Exception {
        Assert.assertFalse("0 视同最强不携带参数", requestBodyOf(0).has("thinking_budget"));
        Assert.assertFalse("负数视同最强不携带参数", requestBodyOf(-1).has("thinking_budget"));
    }

    /** 三参构造兼容旧调用：等价 null（最强） */
    @Test
    public void legacyConstructorDefaultsToStrongest() throws Exception {
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(), "AGENT", null);
        JsonNode body = OM.readTree(model.buildRequestBody("m",
                Collections.singletonList(UserMessage.from("hi")), null));
        Assert.assertFalse("旧构造路径默认最强不携带参数", body.has("thinking_budget"));
    }
}
