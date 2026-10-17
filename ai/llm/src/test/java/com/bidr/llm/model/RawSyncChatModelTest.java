package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Title: RawSyncChatModelTest
 * Description: 同步 raw 客户端**离线回归**（CI 必跑）——断言请求体组装、消息/工具序列化与失败分类；
 * 选型与调用方式见 README「模型层客户端一览」与 7.3，本类只负责"代码没坏"。
 * <p>
 * 两类用例：
 * <ul>
 *   <li>请求体/响应解析断言（不触外网）——扩展参数透传与保留字段保护、max_tokens 两态、
 *       工具定义与消息四态序列化、工具 id 兜底、非文本消息显式拒绝；</li>
 *   <li>本地 HttpServer 端到端——真实收发一次（工具调用轮 + 思考 token 落 trace）、
 *       5xx 重试后抛原始错误体、4xx 立即失败不重试、空 content 报错不静默。</li>
 * </ul>
 * 用 JDK 自带 HttpServer 而非 MockWebServer：不引入新测试依赖，无需凭据即可在 CI 构造
 * "网关返回 500/400/空产出"这类真网关无法复现的场景。
 *
 * @author Sharp
 * @since 2026/9/17
 */
public class RawSyncChatModelTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final List<HttpServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
    }

    // ---------------- 请求体与解析（离线） ----------------

    /** 扩展参数透传 + 保留字段保护：extraBody 写顶层，但 model/messages 以核心组装为准不被覆盖 */
    @Test
    public void extraBodyIsTransparentExceptReservedFields() throws Exception {
        Map<String, Object> extra = new HashMap<>();
        extra.put("enable_thinking", false);
        extra.put("reasoning_effort", "low");
        extra.put("model", "hacked-model");
        extra.put("messages", "hacked-messages");
        RawSyncChatModel model = new RawSyncChatModel(stubProvider("http://stub", ""), "AGENT", null,
                extra, 8192, 1, null);

        JsonNode body = OM.readTree(model.buildRequestBody("qwen3.8-flash",
                Collections.singletonList(UserMessage.from("hi")), null));

        Assert.assertEquals("思考开关须原样透传", false, body.path("enable_thinking").asBoolean());
        Assert.assertEquals("任意扩展参数一并透传", "low", body.path("reasoning_effort").asText());
        Assert.assertEquals("保留字段 model 以核心组装为准", "qwen3.8-flash", body.path("model").asText());
        Assert.assertTrue("保留字段 messages 以核心组装为准", body.path("messages").isArray());
        Assert.assertEquals("max_tokens 须写入（思考型模型小上限会空产出）", 8192, body.path("max_tokens").asInt());
    }

    /** max_tokens 两态：null/非正不携带（模型默认），正值原样写入 */
    @Test
    public void maxTokensCarriedOnlyWhenPositive() throws Exception {
        JsonNode absent = bodyOf(null, null);
        Assert.assertFalse("null 不携带 max_tokens", absent.has("max_tokens"));
        JsonNode zero = bodyOf(null, 0);
        Assert.assertFalse("0 不携带 max_tokens", zero.has("max_tokens"));
        JsonNode set = bodyOf(null, 1024);
        Assert.assertEquals("正值原样写入", 1024, set.path("max_tokens").asInt());
    }

    /** 思考开关三态示例：不传 = 模型默认；传 false = 显式关思考（后台任务口径） */
    @Test
    public void thinkingSwitchIsCallerConcernOnly() throws Exception {
        Assert.assertFalse("不传扩展参数时请求体无思考开关", bodyOf(null, null).has("enable_thinking"));
        Map<String, Object> off = Collections.singletonMap("enable_thinking", Boolean.FALSE);
        Assert.assertFalse("业务传 false 时须显式携带", bodyOf(off, null).path("enable_thinking").asBoolean());
    }

    /** 工具定义 + 消息四态序列化（system/user/assistant工具轮/工具结果），中文原样往返 */
    @Test
    public void requestCarriesToolsAndFourMessageStates() throws Exception {
        ToolSpecification spec = ToolSpecifications.toolSpecificationsFrom(new ScopeTools()).get(0);
        ToolExecutionRequest call = ToolExecutionRequest.builder()
                .id("call_1").name("currentScope").arguments("{\"space\":\"IT空间\"}").build();
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("纪律：结论必须带引用"),
                UserMessage.from("当前范围是什么？"),
                AiMessage.from(Collections.singletonList(call)),
                ToolExecutionResultMessage.from(call, "SCOPE-OK|维度=档案/定期30年"));

        JsonNode body = bodyOf(null, 512, messages, Collections.singletonList(spec));

        JsonNode fn = body.path("tools").path(0).path("function");
        Assert.assertEquals("function", body.path("tools").path(0).path("type").asText());
        Assert.assertEquals("currentScope", fn.path("name").asText());
        Assert.assertTrue("工具描述须带上（模型据此决定调用时机）", fn.path("description").asText().contains("检索范围"));
        Assert.assertEquals("object", fn.path("parameters").path("type").asText());
        Assert.assertEquals("string", fn.path("parameters").path("properties").path("space").path("type").asText());
        Assert.assertEquals("space", fn.path("parameters").path("required").path(0).asText());

        Assert.assertEquals("system", body.path("messages").path(0).path("role").asText());
        Assert.assertEquals("assistant", body.path("messages").path(2).path("role").asText());
        Assert.assertEquals("call_1", body.path("messages").path(2).path("tool_calls").path(0).path("id").asText());
        Assert.assertEquals("tool", body.path("messages").path(3).path("role").asText());
        Assert.assertEquals("call_1", body.path("messages").path(3).path("tool_call_id").asText());
        // 中文往返：工具参数与结果都是证据载体，编码损坏会让模型读到乱码
        Assert.assertTrue(body.path("messages").path(2).path("tool_calls").path(0).path("function")
                .path("arguments").asText().contains("IT空间"));
        Assert.assertTrue(body.path("messages").path(3).path("content").asText().contains("定期30年"));
    }

    /** 网关未回 tool_calls[].id 时按序合成：回填工具结果需 id 对齐，缺失会触发端点协议报错 */
    @Test
    public void toolCallIdSynthesizedWhenAbsent() throws Exception {
        JsonNode message = OM.readTree("{\"role\":\"assistant\",\"tool_calls\":["
                + "{\"function\":{\"name\":\"listScopes\",\"arguments\":\"{}\"}},"
                + "{\"function\":{\"name\":\"searchDocs\",\"arguments\":\"{}\"}}]}");
        List<ToolExecutionRequest> calls = RawSyncChatModel.toolCallsOf(message);
        Assert.assertEquals(2, calls.size());
        Assert.assertEquals("call_0", calls.get(0).id());
        Assert.assertEquals("call_1", calls.get(1).id());
        Assert.assertEquals("searchDocs", calls.get(1).name());
    }

    /** finish_reason 映射：截断 length 是"返回失败"的最常见形态，必须映射为 LENGTH 供上层识别 */
    @Test
    public void finishReasonMappedToLangchainEnum() {
        Assert.assertEquals(dev.langchain4j.model.output.FinishReason.STOP,
                RawSyncChatModel.finishReasonOf("stop"));
        Assert.assertEquals(dev.langchain4j.model.output.FinishReason.LENGTH,
                RawSyncChatModel.finishReasonOf("length"));
        Assert.assertEquals(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION,
                RawSyncChatModel.finishReasonOf("tool_calls"));
        Assert.assertEquals(dev.langchain4j.model.output.FinishReason.CONTENT_FILTER,
                RawSyncChatModel.finishReasonOf("content_filter"));
        Assert.assertEquals(dev.langchain4j.model.output.FinishReason.OTHER,
                RawSyncChatModel.finishReasonOf("weird-gateway-value"));
        Assert.assertNull("缺失不臆造", RawSyncChatModel.finishReasonOf(null));
    }

    /** 截断端到端：finish=length 时须落 trace（finish=）并把 LENGTH 透出到 Response——
     *  思考挤爆 max_tokens 是最常见的"返回失败"，显式可观测才谈得上修（关思考/调预算） */
    @Test
    public void truncationSurfacesInTraceAndResponse() throws Exception {
        List<String> trace = new CopyOnWriteArrayList<>();
        int port = startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},"
                + "\"finish_reason\":\"length\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4096,\"total_tokens\":4106,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":3900}}}", new CopyOnWriteArrayList<>());

        RawSyncChatModel model = new RawSyncChatModel(
                stubProvider("http://127.0.0.1:" + port + "/v1", "k"), "AGENT", null, null, 4096, 1, trace::add);
        Response<AiMessage> resp = model.generate(Collections.singletonList(UserMessage.from("hi")));

        Assert.assertEquals("截断须映射为 LENGTH 透出", dev.langchain4j.model.output.FinishReason.LENGTH,
                resp.finishReason());
        Assert.assertEquals(1, trace.size());
        Assert.assertTrue("trace 须带 finish=length 供审计，实际: " + trace.get(0),
                trace.get(0).contains("finish=length"));
        Assert.assertTrue("trace 须带思考 token 数（截断常因思考挤爆），实际: " + trace.get(0),
                trace.get(0).contains("思考tokens=3900"));
    }

    /** 非文本消息显式拒绝：静默丢弃图片会产出"看起来正常的错答案" */
    @Test
    public void nonTextContentIsRejectedExplicitly() throws Exception {
        UserMessage multimodal = UserMessage.from(
                TextContent.from("看这张图"),
                ImageContent.from("aGVsbG8=", "image/png"));
        try {
            bodyOf(null, null, Collections.singletonList(multimodal));
            Assert.fail("多模态内容须显式抛异常");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue("异常须点明不支持原因，实际: " + e.getMessage(),
                    e.getMessage().contains("仅支持文本"));
        }
    }

    // ---------------- 端到端（本地 HttpServer，不触外网） ----------------

    /** 工具调用轮端到端：真实收发 + 思考 token 落 trace（审计"该关思考的任务确实没思考"） */
    @Test
    public void toolCallRoundTripAndThinkingTrace() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        List<String> trace = new CopyOnWriteArrayList<>();
        int port = startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_abc\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"currentScope\",\"arguments\":\"{\\\"space\\\":\\\"IT空间\\\"}\"}}]}}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":30,\"total_tokens\":42,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":0}}}", requests);

        RawSyncChatModel model = new RawSyncChatModel(
                stubProvider("http://127.0.0.1:" + port + "/v1", "test-key"), "AGENT", null,
                Collections.singletonMap("enable_thinking", Boolean.FALSE), 512, 1, trace::add);
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ScopeTools());
        Response<AiMessage> resp = model.generate(
                Collections.singletonList(UserMessage.from("当前范围？")), specs);

        Assert.assertTrue("模型须返回工具调用", resp.content().hasToolExecutionRequests());
        ToolExecutionRequest req = resp.content().toolExecutionRequests().get(0);
        Assert.assertEquals("currentScope", req.name());
        Assert.assertTrue("工具参数中文须完好", req.arguments().contains("IT空间"));
        Assert.assertNotNull("token 用量须透出", resp.tokenUsage());

        Assert.assertEquals(1, requests.size());
        JsonNode sent = OM.readTree(requests.get(0));
        Assert.assertEquals("关思考须真的发到网关", false, sent.path("enable_thinking").asBoolean());
        Assert.assertTrue("tools 须随请求发出", sent.path("tools").isArray());

        Assert.assertEquals(1, trace.size());
        Assert.assertTrue("trace 须带思考 token 数供审计，实际: " + trace.get(0),
                trace.get(0).contains("思考tokens=0"));
        Assert.assertTrue("trace 须带工具数", trace.get(0).contains("工具=1"));
    }

    /** 5xx 可重试：退避后重试，最终抛错须带网关原始响应体（不静默、不被库层包装吞掉） */
    @Test
    public void serverErrorRetriedThenThrownWithGatewayBody() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        int port = startServer(500, "{\"error\":\"upstream busy\"}", requests);
        RawSyncChatModel model = new RawSyncChatModel(
                stubProvider("http://127.0.0.1:" + port + "/v1", "k"), "AGENT", null, null, null, 2, null);
        try {
            model.generate(Collections.singletonList(UserMessage.from("hi")));
            Assert.fail("5xx 重试耗尽后须抛异常");
        } catch (RuntimeException e) {
            Assert.assertTrue("错误文案须含 HTTP 码，实际: " + e.getMessage(), e.getMessage().contains("HTTP 500"));
            Assert.assertTrue("错误文案须带网关原始体，实际: " + e.getMessage(),
                    e.getMessage().contains("upstream busy"));
        }
        Assert.assertEquals("须按 maxAttempts 重试", 2, requests.size());
    }

    /** 4xx 请求侧错误立即失败：重试无意义，只会放大成本 */
    @Test
    public void clientErrorFailsFastWithoutRetry() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        int port = startServer(400, "{\"error\":\"invalid tools\"}", requests);
        RawSyncChatModel model = new RawSyncChatModel(
                stubProvider("http://127.0.0.1:" + port + "/v1", "k"), "AGENT", null, null, null, 3, null);
        try {
            model.generate(Collections.singletonList(UserMessage.from("hi")));
            Assert.fail("4xx 须立即抛异常");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("请求侧错误，不重试"));
        }
        Assert.assertEquals("4xx 不得重试", 1, requests.size());
    }

    /** 空 content 报错不静默：思考型模型被思考耗尽 token 时会空产出，静默返回空答案最危险 */
    @Test
    public void emptyContentFailsLoudly() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        int port = startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":512,\"total_tokens\":522,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":512}}}", requests);
        RawSyncChatModel model = new RawSyncChatModel(
                stubProvider("http://127.0.0.1:" + port + "/v1", "k"), "AGENT", null, null, null, 1, null);
        try {
            model.generate(Collections.singletonList(UserMessage.from("hi")));
            Assert.fail("空产出须报错");
        } catch (IllegalStateException e) {
            Assert.assertTrue("错误文案须给出排查线索，实际: " + e.getMessage(),
                    e.getMessage().contains("空 content"));
        }
    }

    // ---------------- 脚手架 ----------------

    /** 工具样例：注解即 schema 来源（@Tool 描述 + @P 参数描述） */
    public static class ScopeTools {
        @dev.langchain4j.agent.tool.Tool("返回当前检索范围（空间与分类维度）。用户问“当前范围”时必须调用本工具。")
        public String currentScope(@dev.langchain4j.agent.tool.P("空间名称") String space) {
            return "SCOPE-OK";
        }
    }

    private static JsonNode bodyOf(Map<String, Object> extra, Integer maxTokens) throws Exception {
        return bodyOf(extra, maxTokens, Collections.singletonList(UserMessage.from("hi")));
    }

    private static JsonNode bodyOf(Map<String, Object> extra, Integer maxTokens, List<ChatMessage> messages)
            throws Exception {
        return bodyOf(extra, maxTokens, messages, null);
    }

    private static JsonNode bodyOf(Map<String, Object> extra, Integer maxTokens, List<ChatMessage> messages,
                                   List<ToolSpecification> specs) throws Exception {
        RawSyncChatModel model = new RawSyncChatModel(stubProvider("http://stub", "k"), "AGENT", null,
                extra, maxTokens, 1, null);
        return OM.readTree(model.buildRequestBody("test-model", messages, specs));
    }

    /** 桩配置：请求体组装不依赖取值；baseUrl 指向本地服务时即真实收发 */
    private static ModelConfigProvider stubProvider(String baseUrl, String apiKey) {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return baseUrl;
            }

            @Override
            public String getApiKey(String purposeType, Long userId) {
                return apiKey;
            }

            @Override
            public String getModelName(String purposeType) {
                return "test-model";
            }

            @Override
            public long getTimeoutSeconds(String purposeType) {
                return 30;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return baseUrl + "|stub";
            }
        };
    }

    /** 起一个只回固定响应的本地端点（记录收到的请求体）；不依赖外部服务，CI 可跑 */
    private int startServer(int status, String responseBody, List<String> requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(readAll(exchange.getRequestBody()));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return server.getAddress().getPort();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}