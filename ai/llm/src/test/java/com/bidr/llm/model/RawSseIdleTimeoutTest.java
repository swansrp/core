package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
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
 * Title: RawSseIdleTimeoutTest
 * Description: 自建 SSE 客户端空闲读超时护栏回归：网关半挂连接（建连返回 SSE 响应头后静默不吐数据）
 * 必须在空闲上限内以可读错误收口（onError 必达），不得陪跑满全量超时。
 * debug 背景：sessionId=7a22a030… 自主生成会话——网关挂起零产出，readLine 以 600s 全量超时阻塞，
 * 与门面层闩等待同时到期，最终只报「600s 未收口」泛化错误、思考/应答内容全丢且回落降级未触发。
 * 另附正常流收口对照用例，防护栏改动误伤健康链路。
 *
 * @author Sharp
 * @since 2026/8/24
 */
public class RawSseIdleTimeoutTest {

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

    /** 桩配置：超时给大值（模拟生产 600s），护栏生效值由客户端 idleCapSeconds 注入决定 */
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
                return 600;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return purposeType + "|" + baseUrl;
            }
        };
    }

    /** 等待回调收口的监听器：onError/onComplete 各持一闩与结果引用 */
    private static class Capture implements RawSseStreamingChatModel.Listener {
        final CountDownLatch error = new CountDownLatch(1);
        final CountDownLatch complete = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final AtomicReference<Response<AiMessage>> resp = new AtomicReference<>();
        final StringBuilder tokens = new StringBuilder();

        @Override
        public void onReasoning(String delta) {
        }

        @Override
        public void onToken(String delta) {
            tokens.append(delta);
        }

        @Override
        public void onComplete(Response<AiMessage> response) {
            resp.set(response);
            complete.countDown();
        }

        @Override
        public void onError(Throwable e) {
            err.set(e);
            error.countDown();
        }
    }

    /**
     * 挂起网关：返回 200 + SSE 响应头后静默不写任何数据——护栏须在空闲上限（注入 2s）
     * 附近触发读超时，onError 必达且文案可定位（不再静默陪跑 600s）
     */
    @Test
    public void hungGatewayFailsFastWithReadableError() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.flush(); // 头与首块已送达，之后静默挂起（不关闭不写数据）
        });
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(baseUrl), "AGENT", null);
        model.setIdleCapSeconds(2);
        Capture cap = new Capture();
        List<ChatMessage> msgs = Collections.singletonList(UserMessage.from("hi"));
        model.generate(msgs, null, cap);
        Assert.assertTrue("护栏应在空闲上限附近收口（给足余量，远小于全量 600s）",
                cap.error.await(15, TimeUnit.SECONDS));
        String msg = String.valueOf(cap.err.get().getMessage());
        Assert.assertTrue("错误文案须可定位到空闲读超时：" + msg, msg.contains("空闲读超时"));
        Assert.assertEquals("挂起场景不应有正常收口", 1, cap.complete.getCount());
    }

    /** 对照组：正常 SSE 流（data 行 + [DONE]）照常解析收口，护栏不误伤健康链路 */
    @Test
    public void healthyStreamStillCompletes() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}\n"
                    + "data: [DONE]\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(baseUrl), "AGENT", null);
        model.setIdleCapSeconds(2);
        Capture cap = new Capture();
        model.generate(Collections.singletonList(UserMessage.from("hi")), null, cap);
        Assert.assertTrue("正常流应正常收口", cap.complete.await(15, TimeUnit.SECONDS));
        Assert.assertEquals("应答内容完整拼装", "你好，世界", cap.tokens.toString());
        Assert.assertNull("正常流不应报错", cap.err.get());
    }
}
