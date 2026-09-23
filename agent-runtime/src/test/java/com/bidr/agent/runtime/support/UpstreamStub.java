package com.bidr.agent.runtime.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: UpstreamStub
 * Description: 上游仿真（JDK {@link HttpServer}，零新依赖）——为单测提供可编排的 JSON / SSE 响应，
 * 并记录请求路径、请求头与在途连接数（用于断言"断连收流"）。
 *
 * @author sharp
 * @since 2026/9/22
 */
public final class UpstreamStub implements Closeable {

    private final HttpServer server;
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();
    private final Map<String, String> requestHeaders = new ConcurrentHashMap<>();
    private final List<String> requestHeaderValues = new CopyOnWriteArrayList<>();
    private final Map<String, JsonRoute> jsonRoutes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sseRoutes = new ConcurrentHashMap<>();
    private final AtomicInteger activeStreams = new AtomicInteger();

    private static final class JsonRoute {
        private final String body;
        private final int status;

        private JsonRoute(String body, int status) {
            this.body = body;
            this.status = status;
        }
    }

    public UpstreamStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 注册 JSON 路由（信封原文）
     */
    public void onJson(String path, String envelopeJson) {
        onJson(path, envelopeJson, 200);
    }

    public void onJson(String path, String envelopeJson, int status) {
        jsonRoutes.put(path, new JsonRoute(envelopeJson, status));
    }

    /**
     * 注册 SSE 路由：frames 为逐帧 JSON 原文，stub 会在每帧前插入 `: ping` 注释（顺带验证注释被忽略）
     */
    public void onSse(String path, List<String> frames) {
        sseRoutes.put(path, frames);
    }

    public List<String> getRequestPaths() {
        return Collections.unmodifiableList(requestPaths);
    }

    public String header(String name) {
        return requestHeaders.get(name.toLowerCase());
    }

    /** 是否存在包含指定文本的请求头值（断言鉴权头等，避免头名大小写口径差异） */
    public boolean hasHeaderValueContaining(String text) {
        return requestHeaderValues.stream().anyMatch(value -> value != null && value.contains(text));
    }

    public int activeStreams() {
        return activeStreams.get();
    }

    /**
     * 等待条件成立（上限 timeoutMs），用于断言异步行为
     */
    public boolean await(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestPaths.add(path);
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                requestHeaders.put(name.toLowerCase(), values.get(0));
                requestHeaderValues.addAll(values);
            }
        });
        // 读掉请求体（避免连接悬挂）
        byte[] ignored = new byte[4096];
        while (exchange.getRequestBody().read(ignored) >= 0) {
            // discard
        }

        List<String> frames = sseRoutes.get(path);
        if (frames != null) {
            serveSse(exchange, frames);
            return;
        }
        JsonRoute route = jsonRoutes.get(path);
        if (route != null) {
            respond(exchange, route.status, route.body);
            return;
        }
        respond(exchange, 404, "{\"code\":40403,\"msg\":\"stub 未注册该路径\",\"data\":null}");
    }

    private void serveSse(HttpExchange exchange, List<String> frames) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        activeStreams.incrementAndGet();
        try (OutputStream out = exchange.getResponseBody()) {
            for (String frame : frames) {
                out.write((": ping\n\n").getBytes(StandardCharsets.UTF_8));
                out.write(("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException clientGone) {
            // 客户端（relay）断开：这里就是"上游被收流"的观测点
        } finally {
            activeStreams.decrementAndGet();
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** 便捷：构造信封 JSON */
    public static String envelope(int code, String msg, String dataJson) {
        return "{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":" + dataJson + "}";
    }

    /** 便捷：构造帧 JSON */
    public static String frame(String type, String sessionId, String messageId, String dataJson) {
        return "{\"type\":\"" + type + "\",\"session_id\":\"" + sessionId + "\",\"message_id\":\"" + messageId
                + "\",\"data\":" + dataJson + "}";
    }

    /** 便捷：帧列表副本 */
    public static List<String> listOf(String... frames) {
        return new ArrayList<>(java.util.Arrays.asList(frames));
    }
}