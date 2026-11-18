package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: RawSseToolCallTextTest
 * Description: 流式工具轮「边说边调」正文保留回归：思考型模型（qwen3.8-flash 实测）会在同一轮
 * 先流 content（"我先列一下目录…"）再流 tool_calls。自建 SSE 客户端若在收口时只构造
 * {@code AiMessage.from(reqs)}，这段正文会被整段丢弃 ⇒ ToolAgentRunner 的 ai.text() 恒空 ⇒
 * llm_output 进度事件永不发射、静默轮计数空转，用户侧只剩工具卡（2026-11-18「模型不说话」根因）。
 * <p>
 * 断言两件事：工具请求与前言正文同时保留；服务端 content 增量仍照常上抛（live 帧不受影响）。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class RawSseToolCallTextTest {

    private HttpServer server;
    private String baseUrl;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 桩配置：仅满足构造，请求体组装不依赖其取值 */
    private static ModelConfigProvider stubProvider(String baseUrl) {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return baseUrl;
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
                return 60;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return purposeType + "|" + baseUrl;
            }
        };
    }

    /** 收口结果捕获 */
    private static class Capture implements RawSseStreamingChatModel.Listener {
        final CountDownLatch complete = new CountDownLatch(1);
        final AtomicReference<ChatResponse> resp = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final StringBuilder tokens = new StringBuilder();

        @Override
        public void onReasoning(String delta) {
        }

        @Override
        public void onToken(String delta) {
            tokens.append(delta);
        }

        @Override
        public void onComplete(ChatResponse response) {
            resp.set(response);
            complete.countDown();
        }

        @Override
        public void onError(Throwable e) {
            err.set(e);
            complete.countDown();
        }
    }

    /** content 增量 + tool_calls 增量同流：收口后的 AiMessage 必须两者都带 */
    @Test
    public void toolCallKeepsStreamedPreambleText() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"我先看一下\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"当前范围。\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_p\","
                    + "\"type\":\"function\",\"function\":{\"name\":\"currentScope\","
                    + "\"arguments\":\"{\\\"space\\\":\\\"IT空间\\\"}\"}}]}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n"
                    + "data: [DONE]\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(baseUrl), "AGENT", null);
        Capture cap = new Capture();
        List<ChatMessage> msgs = Collections.singletonList(UserMessage.from("当前范围？"));
        model.generate(msgs, null, cap);

        Assert.assertTrue("流应正常收口", cap.complete.await(15, TimeUnit.SECONDS));
        Assert.assertNull("正常流不应报错", cap.err.get());
        Assert.assertEquals("content 增量仍照常上抛（live 帧依赖它）", "我先看一下当前范围。", cap.tokens.toString());

        ChatResponse resp = cap.resp.get();
        Assert.assertNotNull(resp);
        Assert.assertTrue("工具请求须保留", resp.aiMessage().hasToolExecutionRequests());
        Assert.assertEquals("call_p", resp.aiMessage().toolExecutionRequests().get(0).id());
        Assert.assertTrue("工具参数中文须完好",
                resp.aiMessage().toolExecutionRequests().get(0).arguments().contains("IT空间"));
        Assert.assertEquals("流式工具轮前言正文不得丢弃（进度可见依赖 ai.text()）",
                "我先看一下当前范围。", resp.aiMessage().text());
    }
}