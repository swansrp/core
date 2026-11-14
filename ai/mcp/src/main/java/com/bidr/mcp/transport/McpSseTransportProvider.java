package com.bidr.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpTransport;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.json.TypeRef;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: McpSseTransportProvider
 * Description: javax.servlet 版 MCP HTTP+SSE transport，实现 SDK
 * {@code McpServerTransportProvider}。按官方 mcp-core 2.1.0 的
 * {@code HttpServletSseServerTransportProvider}（jakarta，773 行）语义移植：
 * HTTP 层不再借道 Solon 第二上下文，而是回到宿主自己的 Servlet 容器。
 *
 * <p>wire 语义（与旧 Solon 实现同形态、与官方实现同语义）：</p>
 * <ul>
 * <li>GET {@code sseEndpoint} → {@code text/event-stream} 建流，先发
 *     {@code event: endpoint}，data = {@code <contextPath><messageEndpoint>?sessionId=<uuid>}（相对路径，
 *     由调用端按请求源解析，代理/端口无感）</li>
 * <li>POST {@code messageEndpoint?sessionId=…} → 200 OK 空体；JSON-RPC 应答一律走
 *     SSE 流上的 {@code event: message}——MCP SSE 的应答不走 POST 响应体，这是移植时
 *     最容易做错的一处</li>
 * <li>请求头捕获：SSE 建连头与会话绑定，message POST 头按次合并覆盖（POST 优先），
 *     经 {@code McpTransportContext} 直达工具 handler——兼容把 X-MCP-Key 只建连时带
 *     或只 POST 时带的两种调用端</li>
 * <li>可配周期写 {@code : ping} 注释行防网关闲置断流（不进协议层，客户端无感）</li>
 * <li>协议版本锁 {@code 2024-11-05}（SSE 档，同旧实现；官方 SSE provider 同款锁定）</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/11/14
 */
@Slf4j
public class McpSseTransportProvider implements McpServerTransportProvider {

    private static final int DEFAULT_REQUEST_MAX_SIZE = 16 * 1024 * 1024;

    public static final String UTF_8 = "UTF-8";

    public static final String APPLICATION_JSON = "application/json";

    public static final String MESSAGE_EVENT_TYPE = "message";

    public static final String ENDPOINT_EVENT_TYPE = "endpoint";

    public static final String SESSION_ID_PARAM = "sessionId";

    /**
     * transport context 里完整请求头映射（大小写不敏感 TreeMap）的存放键：
     * SDK 的 McpTransportContext 只暴露单键 get()，完整 header 映射经此键整体取出
     */
    public static final String HEADERS_CONTEXT_KEY = "com.bidr.mcp.HEADERS";

    private final McpJsonMapper jsonMapper;

    /**
     * SSE 建连路径（如 /mcp/supervision-log），须与注册进 dispatcher 的路径一致
     */
    private final String sseEndpoint;

    /**
     * message 端点路径，固定为 sseEndpoint + "/message"（与旧实现派生规则一致）
     */
    private final String messageEndpoint;

    private final int requestMaxSize;

    /**
     * 单条 message 处理超时秒数（block 上限；防未握手会话或异常 handler 挂死 HTTP 线程）
     */
    private final long callTimeoutSeconds;

    /**
     * ping 间隔秒数；<=0 关闭
     */
    private final long keepAliveSeconds;

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    private McpServerSession.Factory sessionFactory;

    private ScheduledExecutorService keepAliveScheduler;

    public McpSseTransportProvider(McpJsonMapper jsonMapper, String sseEndpoint) {
        this(jsonMapper, sseEndpoint, sseEndpoint + "/message", 0L);
    }

    public McpSseTransportProvider(McpJsonMapper jsonMapper, String sseEndpoint, String messageEndpoint,
                                   long keepAliveSeconds) {
        this(jsonMapper, sseEndpoint, messageEndpoint, keepAliveSeconds, DEFAULT_REQUEST_MAX_SIZE, 60L);
    }

    public McpSseTransportProvider(McpJsonMapper jsonMapper, String sseEndpoint, String messageEndpoint,
                                   long keepAliveSeconds, int requestMaxSize, long callTimeoutSeconds) {
        if (jsonMapper == null || sseEndpoint == null || messageEndpoint == null) {
            throw new IllegalArgumentException("jsonMapper/sseEndpoint/messageEndpoint must not be null");
        }
        this.jsonMapper = jsonMapper;
        this.sseEndpoint = sseEndpoint;
        this.messageEndpoint = messageEndpoint;
        this.keepAliveSeconds = keepAliveSeconds;
        this.requestMaxSize = requestMaxSize;
        this.callTimeoutSeconds = callTimeoutSeconds;
        if (keepAliveSeconds > 0) {
            this.keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mcp-sse-keepalive-" + sseEndpoint);
                thread.setDaemon(true);
                return thread;
            });
            this.keepAliveScheduler.scheduleWithFixedDelay(this::pingAll, keepAliveSeconds, keepAliveSeconds,
                    TimeUnit.SECONDS);
        }
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public String getMessageEndpoint() {
        return messageEndpoint;
    }

    @Override
    public List<String> protocolVersions() {
        return List.of(ProtocolVersions.MCP_2024_11_05);
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(sessions.values())
                .flatMap(entry -> entry.session.sendNotification(method, params)
                        .doOnError(e -> log.error("Failed to send message to session {}: {}", entry.sessionId, e.getMessage()))
                        .onErrorComplete())
                .then();
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return Mono.defer(() -> {
            SessionEntry entry = sessions.get(sessionId);
            if (entry == null) {
                log.debug("Session {} not found", sessionId);
                return Mono.empty();
            }
            return entry.session.sendNotification(method, params);
        });
    }

    /**
     * SSE 建连（GET sseEndpoint）：起异步上下文、建会话、发 endpoint 事件
     */
    public void handleSseConnect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isClosing.get()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding(UTF_8);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        String sessionId = UUID.randomUUID().toString();
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        PrintWriter writer = response.getWriter();

        SessionEntry entry = new SessionEntry(sessionId, asyncContext, writer, headerMap(request));
        McpServerSession session = sessionFactory.create(new SessionTransport(entry));
        entry.session = session;
        sessions.put(sessionId, entry);

        try {
            // endpoint 事件：相对路径（含 contextPath 前缀），调用端按请求源解析
            sendEvent(entry, ENDPOINT_EVENT_TYPE,
                    request.getContextPath() + messageEndpoint + "?" + SESSION_ID_PARAM + "=" + sessionId);
        } catch (IOException e) {
            // 建连即断（客户端已走）：会话清理，防 entry 滞留泄漏（keepalive ping 周期前的兜底）
            disconnect(entry);
            throw e;
        }
        log.debug("MCP SSE session {} established on {}", sessionId, sseEndpoint);
    }

    /**
     * message 收发（POST messageEndpoint?sessionId=…）：应答走 SSE 流，POST 只回 200 空体
     */
    public void handleMessage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isClosing.get()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        if (request.getContentLengthLong() > requestMaxSize) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains(APPLICATION_JSON)) {
            responseError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, McpError
                    .builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                    .message("Unsupported Media Type: Content-Type must be application/json").build());
            return;
        }

        String sessionId = request.getParameter(SESSION_ID_PARAM);
        if (sessionId == null) {
            responseError(response, HttpServletResponse.SC_BAD_REQUEST, McpError
                    .builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
                    .message("Session ID missing in message endpoint").build());
            return;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            responseError(response, HttpServletResponse.SC_NOT_FOUND, McpError
                    .builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message("Session not found: " + sessionId).build());
            return;
        }

        try {
            String body = readBody(request, requestMaxSize);

            // 会话建连头 + 本次 POST 头合并（POST 优先），经 Reactor context 直达 handler；
            // 完整映射（大小写不敏感）另存一份于 HEADERS_CONTEXT_KEY 供调用器整体取出
            Map<String, Object> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            merged.putAll(entry.connectHeaders);
            merged.putAll(headerMap(request));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(HEADERS_CONTEXT_KEY, merged);
            McpTransportContext transportContext = McpTransportContext.create(metadata);

            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
            entry.session.handle(message)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
                    .block(Duration.ofSeconds(callTimeoutSeconds));

            response.setStatus(HttpServletResponse.SC_OK);
        } catch (BodyTooLargeException e) {
            // chunked 或声明超限统一 413（官方 doPost 同款状态码）
            log.warn("Request body too large on {}: {}", sseEndpoint, e.getMessage());
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            try {
                responseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, McpError
                        .builder(McpSchema.ErrorCodes.INTERNAL_ERROR).message(e.getMessage()).build());
            } catch (IOException ex) {
                log.error("Failed to send error response: {}", ex.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing message");
            }
        }
    }

    @Override
    public Mono<Void> closeGracefully() {
        isClosing.set(true);
        log.debug("Initiating graceful shutdown with {} active sessions on {}", sessions.size(), sseEndpoint);

        return Flux.fromIterable(sessions.values()).flatMap(entry -> entry.session.closeGracefully()).then()
                .doOnSuccess(v -> {
                    sessions.clear();
                    if (keepAliveScheduler != null) {
                        keepAliveScheduler.shutdownNow();
                    }
                    log.debug("Graceful shutdown completed on {}", sseEndpoint);
                });
    }

    @Override
    public void close() {
        isClosing.set(true);
        for (SessionEntry entry : sessions.values()) {
            entry.session.close();
        }
        sessions.clear();
        if (keepAliveScheduler != null) {
            keepAliveScheduler.shutdownNow();
        }
    }

    /**
     * 定期写 SSE 注释行心跳：不进协议层、客户端无感，只为保住中间层的闲置连接
     */
    private void pingAll() {
        for (SessionEntry entry : sessions.values()) {
            try {
                synchronized (entry.writeLock) {
                    entry.writer.write(": ping\n\n");
                    entry.writer.flush();
                    if (entry.writer.checkError()) {
                        throw new IOException("Client disconnected");
                    }
                }
            } catch (Exception e) {
                log.debug("Keep-alive ping failed for session {}: {}", entry.sessionId, e.getMessage());
                disconnect(entry);
            }
        }
    }

    private void sendEvent(SessionEntry entry, String eventType, String data) throws IOException {
        synchronized (entry.writeLock) {
            entry.writer.write("event: " + eventType + "\n");
            entry.writer.write("data: " + data + "\n\n");
            entry.writer.flush();
            if (entry.writer.checkError()) {
                throw new IOException("Client disconnected");
            }
        }
    }

    private void disconnect(SessionEntry entry) {
        sessions.remove(entry.sessionId);
        try {
            entry.asyncContext.complete();
        } catch (Exception e) {
            log.warn("Failed to complete async context for session {}: {}", entry.sessionId, e.getMessage());
        }
    }

    private void responseError(HttpServletResponse response, int httpCode, McpError mcpError) throws IOException {
        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8);
        response.setStatus(httpCode);
        PrintWriter writer = response.getWriter();
        writer.write(jsonMapper.writeValueAsString(mcpError));
        writer.flush();
    }

    /**
     * 有界读取 POST body：流式计数（chunked 声明长度缺失时同样生效），超限抛
     * {@link BodyTooLargeException}（官方 mcp-core 2.1.0 HttpServletRequestUtils.readBody
     * 语义移植；只按声明 Content-Length 预检会放行 chunked 无界请求）。默认 UTF-8，请求带
     * charset 时按其解码。
     */
    private static String readBody(HttpServletRequest request, int maxSize) throws IOException {
        java.io.InputStream input = request.getInputStream();
        java.io.ByteArrayOutputStream bodyBytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer, 0, buffer.length)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new BodyTooLargeException(
                        "Request body exceeds the maximum allowed size of " + maxSize + " bytes");
            }
            bodyBytes.write(buffer, 0, read);
        }
        String charset = request.getCharacterEncoding() != null ? request.getCharacterEncoding()
                : StandardCharsets.UTF_8.name();
        return bodyBytes.toString(charset);
    }

    /**
     * POST body 超限（→ HTTP 413，对齐官方 doPost 的 MaxSizeExceededException 处理）
     */
    private static class BodyTooLargeException extends RuntimeException {
        private BodyTooLargeException(String message) {
            super(message);
        }
    }

    /**
     * 复制请求头（大小写不敏感映射），与旧 Solon McpServerContext 的 headerMap 口径一致
     */
    private static Map<String, Object> headerMap(HttpServletRequest request) {
        Map<String, Object> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    /**
     * 会话条目：SSE 写出端 + 建连时的请求头快照
     */
    private static class SessionEntry {

        private final String sessionId;

        private final AsyncContext asyncContext;

        private final PrintWriter writer;

        /**
         * SSE 建连（GET）时的请求头，作为会话级兜底
         */
        private final Map<String, Object> connectHeaders;

        /**
         * SSE 写串行化锁：message 事件与 ping 注释行共用
         */
        private final Object writeLock = new Object();

        private volatile McpServerSession session;

        private SessionEntry(String sessionId, AsyncContext asyncContext, PrintWriter writer,
                             Map<String, Object> connectHeaders) {
            this.sessionId = sessionId;
            this.asyncContext = asyncContext;
            this.writer = writer;
            this.connectHeaders = Collections.unmodifiableMap(connectHeaders);
        }
    }

    /**
     * 单会话 transport：SDK 会话经它把 JSON-RPC 消息写回 SSE 流
     */
    private class SessionTransport implements McpServerTransport {

        private final SessionEntry entry;

        private SessionTransport(SessionEntry entry) {
            this.entry = entry;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                try {
                    String jsonText = jsonMapper.writeValueAsString(message);
                    sendEvent(entry, MESSAGE_EVENT_TYPE, jsonText);
                } catch (Exception e) {
                    log.error("Failed to send message to session {}: {}", entry.sessionId, e.getMessage());
                    disconnect(entry);
                }
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> disconnect(entry));
        }

        @Override
        public void close() {
            disconnect(entry);
        }
    }
}
