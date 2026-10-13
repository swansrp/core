package com.bidr.llm.usage;

import com.bidr.llm.model.RawSseStreamingChatModel;
import com.bidr.llm.model.RawSyncChatModel;
import com.bidr.llm.model.RawVisionChatModel;
import com.bidr.llm.model.VisionImage;
import com.bidr.llm.provider.ModelConfigProvider;
import com.bidr.kernel.utils.BeanUtil;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Title: ModelUsageCaptureTest
 * Description: 三条模型客户端各自把用量交出去了（M14 棒5 采集点＝模型包装层）。钉四件事：
 * ① <b>一次调用一行</b>——同步/流式/视觉各落一条事实，token、耗时、finish_reason 取网关原值；
 * ② <b>身份随调用走</b>——发起线程 set 的会话快照出现在事实上；
 * ③ 🔴 <b>事实里只有指纹、没有明文 key</b>；
 * ④ <b>失败腿不落行</b>（5xx 与流提前结束都没有可计的用量，落一行空读数会污染总量）。
 * 全程本地假网关（JDK HttpServer），不触外网、不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
public class ModelUsageCaptureTest {

    private static final String PLAIN_KEY = "capture-plain-key-should-never-appear";

    private final List<com.sun.net.httpserver.HttpServer> servers = new ArrayList<>();

    @After
    public void tearDown() {
        for (com.sun.net.httpserver.HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
        BeanUtil.setContext(null);
        ModelCallContexts.clear();
    }

    // ---------------- 同步链路 ----------------

    /** 工具调用轮：一条事实，token 三栏取网关原值、思考 token 有值、工具只数＝1、身份＝发起线程快照 */
    @Test
    public void 同步工具轮落一行且token与身份齐全() throws Exception {
        RecordingSink sink = stubSink();
        String baseUrl = startJsonServer("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"hi\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":30,\"total_tokens\":42,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":7}}}");
        ModelCallContexts.set(new ModelCallContext("s-sync", "inkhub-check", "bidrAdmin"));

        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ProbeTools());
        Response<AiMessage> resp = new RawSyncChatModel(stubProvider(baseUrl, PLAIN_KEY), "AGENT", null,
                null, null, 1, null)
                .generate(Collections.<ChatMessage>singletonList(UserMessage.from("当前范围？")), specs);
        Assert.assertTrue("假网关的调用应正常返回", resp.content().hasToolExecutionRequests());

        ModelUsageFacts facts = sink.only();
        Assert.assertEquals(ModelUsageFacts.VIA_SYNC, facts.getVia());
        Assert.assertEquals("AGENT", facts.getPurposeType());
        Assert.assertEquals("test-model", facts.getModelName());
        Assert.assertEquals("12", Integer.valueOf(12), facts.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(30), facts.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(42), facts.getTotalTokens());
        Assert.assertEquals("思考 token 走 completion_tokens_details", Integer.valueOf(7),
                facts.getReasoningTokens());
        Assert.assertEquals(Integer.valueOf(1), facts.getToolCount());
        Assert.assertEquals("tool_calls", facts.getFinishReason());
        Assert.assertEquals("s-sync", facts.getSessionId());
        Assert.assertEquals("inkhub-check", facts.getAgentKey());
        Assert.assertEquals("bidrAdmin", facts.getAskedBy());
        assertFingerprintOnly(facts);
    }

    /** 5xx 重试耗尽：抛错照抛，但不落用量行（失败调用没有可计的读数） */
    @Test
    public void 同步失败腿不落行() throws Exception {
        RecordingSink sink = stubSink();
        String baseUrl = startStatusServer(500, "{\"error\":\"upstream busy\"}");
        try {
            new RawSyncChatModel(stubProvider(baseUrl, PLAIN_KEY), "AGENT", null, null, null, 1, null)
                    .generate(Collections.<ChatMessage>singletonList(UserMessage.from("hi")));
            Assert.fail("5xx 应抛错");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains("HTTP 500"));
        }
        Assert.assertTrue("失败调用不得落用量行: " + sink.received, sink.received.isEmpty());
    }

    // ---------------- 流式链路 ----------------

    /** 文本流收口：一行，finish_reason 取末片，toolCount＝0；网关没回 usage 时 token 留 NULL（不填 0） */
    @Test
    public void 流式正常收口落一行无usage则token留null() throws Exception {
        RecordingSink sink = stubSink();
        String baseUrl = startSseServer("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"},\"finish_reason\":\"stop\"}]}\n"
                + "data: [DONE]\n");
        ModelCallContexts.set(new ModelCallContext("s-sse", "writer", "zhangsan"));

        CompletionCapture cap = new CompletionCapture();
        new RawSseStreamingChatModel(stubProvider(baseUrl, PLAIN_KEY), "AGENT", null)
                .generate(Collections.<ChatMessage>singletonList(UserMessage.from("hi")), null, cap);
        Assert.assertTrue("流应正常收口", cap.await());

        ModelUsageFacts facts = sink.only();
        Assert.assertEquals(ModelUsageFacts.VIA_SSE, facts.getVia());
        Assert.assertEquals("AGENT", facts.getPurposeType());
        Assert.assertEquals("test-model", facts.getModelName());
        Assert.assertNull("本客户端不在请求体追加 include_usage，网关不回吐时如实留空",
                facts.getPromptTokens());
        Assert.assertNull(facts.getCompletionTokens());
        Assert.assertNull(facts.getTotalTokens());
        Assert.assertEquals("文本流没有工具调用，这里是 0 而不是 NULL", Integer.valueOf(0), facts.getToolCount());
        Assert.assertEquals("stop", facts.getFinishReason());
        Assert.assertEquals("s-sse", facts.getSessionId());
        Assert.assertEquals("writer", facts.getAgentKey());
        Assert.assertEquals("zhangsan", facts.getAskedBy());
        Assert.assertNotNull("耗时必得（流式为等待首字＋生成全程）", facts.getCostMs());
        assertFingerprintOnly(facts);
    }

    /** 网关主动回吐 usage 的收尾片（部分网关会带）：token 照收，不相加、不猜 */
    @Test
    public void 流式收尾片带usage时照收() throws Exception {
        RecordingSink sink = stubSink();
        String baseUrl = startSseServer("data: {\"choices\":[{\"delta\":{\"content\":\"答\"},"
                + "\"finish_reason\":\"stop\"}]}\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":4}}\n"
                + "data: [DONE]\n");

        CompletionCapture cap = new CompletionCapture();
        new RawSseStreamingChatModel(stubProvider(baseUrl, PLAIN_KEY), "AGENT", null)
                .generate(Collections.<ChatMessage>singletonList(UserMessage.from("hi")), null, cap);
        Assert.assertTrue(cap.await());

        ModelUsageFacts facts = sink.only();
        Assert.assertEquals(Integer.valueOf(8), facts.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(4), facts.getCompletionTokens());
        Assert.assertNull("total 网关没给就不自行相加", facts.getTotalTokens());
        Assert.assertEquals("usage-only 的收尾片 choices 为空数组，finish 仍取前一片的值", "stop",
                facts.getFinishReason());
    }

    /** 流提前结束（200 但零产出）：onError 收口，不落用量行 */
    @Test
    public void 流提前结束不落行() throws Exception {
        RecordingSink sink = stubSink();
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        servers.add(server);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        CompletionCapture cap = new CompletionCapture();
        new RawSseStreamingChatModel(stubProvider(baseUrl, PLAIN_KEY), "AGENT", null)
                .generate(Collections.<ChatMessage>singletonList(UserMessage.from("hi")), null, cap);
        Assert.assertTrue("零产出应答也须收口（onError 或 onComplete 之一）", cap.await());

        Assert.assertTrue("失败/无产出收口不得落用量行: " + sink.received, sink.received.isEmpty());
    }

    // ---------------- 视觉链路 ----------------

    /** 视觉一次调用一行：token 取网关原值，toolCount 留 NULL（视觉链路不发工具＝不适用，不是 0） */
    @Test
    public void 视觉链路落一行且toolCount不适用为null() throws Exception {
        RecordingSink sink = stubSink();
        String baseUrl = startJsonServer("{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"红色矩形\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":6,\"total_tokens\":11}}");
        ModelCallContexts.set(new ModelCallContext("s-vision", null, "bidrAdmin"));

        String caption = new RawVisionChatModel(stubProvider(baseUrl, PLAIN_KEY), "VISION", null,
                null, null, 1, null)
                .complete("你是图片标注助手。", "为这张图写一句图注。",
                        Collections.singletonList(new VisionImage("p.jpg",
                                "JPEG".getBytes(StandardCharsets.UTF_8))));
        Assert.assertEquals("红色矩形", caption);

        ModelUsageFacts facts = sink.only();
        Assert.assertEquals(ModelUsageFacts.VIA_VISION, facts.getVia());
        Assert.assertEquals("VISION", facts.getPurposeType());
        Assert.assertEquals("test-model", facts.getModelName());
        Assert.assertEquals(Integer.valueOf(5), facts.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(6), facts.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(11), facts.getTotalTokens());
        Assert.assertNull("视觉链路不决定工具，NULL＝不适用", facts.getToolCount());
        Assert.assertEquals("stop", facts.getFinishReason());
        Assert.assertEquals("s-vision", facts.getSessionId());
        Assert.assertNull("会话状态给不出 agentKey 时留空", facts.getAgentKey());
        assertFingerprintOnly(facts);
    }

    // ---------------- 断言与脚手架 ----------------

    /** 🔴 计量面只见指纹：值等于该 key 的指纹、长度 16，且整串事实里找不到明文 */
    private static void assertFingerprintOnly(ModelUsageFacts facts) {
        Assert.assertEquals(ModelUsageReporter.keyFingerprint(PLAIN_KEY), facts.getKeyFingerprint());
        Assert.assertEquals(16, facts.getKeyFingerprint().length());
        Assert.assertFalse("事实整串（含 toString 全部字段）都不得出现明文 key",
                facts.toString().contains(PLAIN_KEY));
    }

    /** 把记录型 sink 桩进 BeanUtil 取 bean 路径（模型层只认 Spring，不给它开测试后门） */
    private RecordingSink stubSink() {
        RecordingSink sink = new RecordingSink();
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean(ModelUsageSink.class)).thenReturn(sink);
        BeanUtil.setContext(ctx);
        return sink;
    }

    /** 收集模型层交出的事实 */
    private static final class RecordingSink implements ModelUsageSink {
        private final List<ModelUsageFacts> received = new CopyOnWriteArrayList<>();

        @Override
        public void report(ModelUsageFacts facts) {
            received.add(facts);
        }

        ModelUsageFacts only() {
            Assert.assertEquals("一次模型调用应且只应落一行: " + received, 1, received.size());
            return received.get(0);
        }
    }

    /** 流式收口监听器：只关心 onComplete/onError 到没到 */
    private static final class CompletionCapture implements RawSseStreamingChatModel.Listener {
        private final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();

        @Override
        public void onReasoning(String delta) {
        }

        @Override
        public void onToken(String delta) {
        }

        @Override
        public void onComplete(Response<AiMessage> response) {
            done.countDown();
        }

        @Override
        public void onError(Throwable e) {
            error.set(e);
            done.countDown();
        }

        boolean await() throws InterruptedException {
            return done.await(20, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    /** 工具样例：让同步链路真的走"模型决定调用工具"那一支 */
    public static class ProbeTools {
        @Tool("echo the given text")
        public String echo(String text) {
            return "echo:" + text;
        }
    }

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
                return purposeType + "|stub";
            }
        };
    }

    /** 固定 JSON 应答的假网关 */
    private String startJsonServer(String body) throws IOException {
        return startServer("application/json", 200, body);
    }

    /** 固定状态码的假网关（失败腿） */
    private String startStatusServer(int status, String body) throws IOException {
        return startServer("application/json", status, body);
    }

    /** SSE 应答的假网关 */
    private String startSseServer(String sseBody) throws IOException {
        return startServer("text/event-stream", 200, sseBody);
    }

    private String startServer(String contentType, int status, String body) throws IOException {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            readAll(exchange.getRequestBody());
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void readAll(InputStream in) {
        try {
            byte[] buf = new byte[4096];
            while (in.read(buf) != -1) {
                // 请求体读空即可：本测试只关心模型层交出的用量，不校验请求内容
            }
        } catch (IOException ignored) {
            // 读请求体失败不影响应答
        }
    }
}
