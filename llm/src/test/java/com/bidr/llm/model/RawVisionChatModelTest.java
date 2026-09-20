package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * RawVisionChatModel 单测（离线，本地 HttpServer 假网关）：钉住多模态请求形态
 * （content 数组 + data URI）、extraBody 透传与保留字段保护、4xx 不重试/5xx 退避重试。
 * 本测试留在 llm 内当接入参考写法（同 {@link RawSyncChatModelTest} 口径）。
 *
 * @author Sharp
 */
public class RawVisionChatModelTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final java.util.List<HttpServer> servers = new java.util.ArrayList<>();

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

    private final AtomicReference<Integer> callCount = new AtomicReference<>(0);

    @After
    public void stopServers() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
    }

    /** 带图请求：VISION purpose 取模型名、content 为数组、图片编码 data URI、system 消息在前 */
    @Test
    public void visionRequestBuildsContentArrayWithDataUri() throws Exception {
        String baseUrl = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"红色矩形\"}}]}");
        RawVisionChatModel model = model(baseUrl, "");

        byte[] jpeg = "JPEG 字节".getBytes(StandardCharsets.UTF_8);
        String caption = model.complete("你是图片标注助手。", "为这张图写一句图注。",
                Collections.singletonList(new VisionImage("pic.jpg", jpeg)));

        assertEquals("红色矩形", caption);
        JsonNode body = OM.readTree(requestBody.get());
        assertEquals("test-model", body.path("model").asText());
        assertEquals(8192, body.path("max_tokens").asInt());
        assertEquals("你是图片标注助手。", body.path("messages").get(0).path("content").asText());
        JsonNode userContent = body.path("messages").get(1).path("content");
        assertTrue("带图请求的 user content 须为数组", userContent.isArray());
        assertEquals("text", userContent.get(0).path("type").asText());
        String expected = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        assertEquals(expected, userContent.get(1).path("image_url").path("url").asText());
    }

    /** 无图退化纯文本；extraBody 透传但 model/messages 保留字段以核心组装为准 */
    @Test
    public void textOnlyFallsBackToPlainContentAndExtraBodyTransparent() throws Exception {
        String baseUrl = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        Map<String, Object> extra = new HashMap<>();
        extra.put("enable_thinking", false);
        extra.put("model", "hacked-model");
        RawVisionChatModel model = new RawVisionChatModel(stubProvider(baseUrl, ""), "VISION", null,
                extra, 512, 1, null);

        assertEquals("ok", model.complete(null, "描述这张图。", null));

        JsonNode body = OM.readTree(requestBody.get());
        assertEquals("extraBody 逐键透传", false, body.path("enable_thinking").asBoolean());
        assertEquals("保留字段不被 extraBody 覆盖", "test-model", body.path("model").asText());
        assertTrue("无图退化为纯文本 content", body.path("messages").get(0).path("content").isTextual());
    }

    /** 5xx 退避重试后成功；4xx 立即失败不重试 */
    @Test
    public void serverErrorRetriesAndClientErrorFailsFast() throws Exception {
        String baseUrl = startServer(500, "boom");
        RawVisionChatModel model = new RawVisionChatModel(stubProvider(baseUrl, ""), "VISION", null,
                null, null, 2, null);
        try {
            model.complete(null, "描述", null);
            fail("5xx 耗尽重试应抛出");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 500"));
        }
        assertEquals("5xx 应重试到 maxAttempts", 2, callCount.get().intValue());

        String retry = startServer(400, "bad request");
        RawVisionChatModel strict = model(retry, "");
        try {
            strict.complete(null, "描述", Collections.singletonList(new VisionImage("a.jpg", new byte[]{1})));
            fail("4xx 应立即失败");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 400"));
        }
        assertEquals("4xx 不重试", 1, callCount.get().intValue());
    }

    // ---------------- 夹具 ----------------

    private RawVisionChatModel model(String baseUrl, String apiKey) {
        return new RawVisionChatModel(stubProvider(baseUrl, apiKey), "VISION", null,
                null, 8192, 1, null);
    }

    private String startServer(int status, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        responseStatus.set(status);
        callCount.set(0);
        server.createContext("/chat/completions", exchange -> {
            callCount.set(callCount.get() + 1);
            requestBody.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            respond(exchange, responseStatus.get(), responseBody);
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
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
                return baseUrl + "|test-model|30";
            }
        };
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
