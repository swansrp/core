package com.bidr.mcp.client;

import com.bidr.mcp.json.JacksonMcpJsonMapper;
import com.bidr.mcp.json.PermissiveJsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: McpClientManager
 * Description: 通用 MCP SSE 客户端管理器。按 serverKey 懒建并复用官方 SDK 的
 * {@link McpSyncClient}，对外只暴露 {@link McpToolClient} 的两个方法。
 *
 * <p>协议机制（已实测，见方案 M-1～M-5）：</p>
 * <ul>
 * <li>传输走 {@link HttpClientSseClientTransport}，凭证放 {@code x-api-key} 头：
 * {@code Base64(appKey + ":" + appSecret)}；</li>
 * <li>🔴 {@code sseEndpoint} 必须传<b>绝对 URL</b>（{@code baseUrl} + 端点拼接）——SDK 的
 * {@code Utils.resolveUri} 在相对路径下会吃掉 baseUrl 的路径段（如 {@code /inkhub}）；</li>
 * <li>🔴 必须显式传 {@link PermissiveJsonSchemaValidator}，否则 ServiceLoader 找不到默认
 * 校验器（{@code ServiceConfigurationError}）。</li>
 * </ul>
 *
 * <p>错误归一化：</p>
 * <ul>
 * <li>{@link McpError}（JSON-RPC 业务错误，如 code -32603）→ 不可重试，取业务文案；</li>
 * <li>{@code CallToolResult.isError() == true} → 不可重试，取 TextContent 文本；</li>
 * <li>其他 RuntimeException（传输/超时/SSE 断流）→ 关闭并置空该 client，重建重试 1 次；
 * 仍失败抛可重试异常。</li>
 * </ul>
 *
 * <p>线程模型：同一 serverKey 单例、同步创建；{@link #close()} 幂等，且关闭后置关闭态——
 * 之后的 {@link #callTool} 直接抛不可重试异常、<b>不再建连</b>（关停窗口防连接泄漏）。
 * 日志只记 serverKey/toolName/耗时/异常类与截断后的 message，绝不打印 appSecret 或
 * {@code x-api-key}。</p>
 *
 * @author sharp
 * @since 2026/11/20
 */
@Slf4j
@Component
public class McpClientManager implements McpToolClient, AutoCloseable {

    /**
     * InkHub 侧约定的凭证头名
     */
    static final String API_KEY_HEADER = "x-api-key";

    private static final String DEFAULT_SSE_ENDPOINT = "/mcp";

    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 5000L;

    private static final long DEFAULT_INITIALIZATION_TIMEOUT_MS = 10000L;

    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 30000L;

    private static final int MAX_ATTEMPTS = 2;

    private static final int MAX_LOG_MESSAGE_LENGTH = 300;

    private static final String CLIENT_NAME = "epc-ai";

    private static final String CLIENT_VERSION = "1.0.0";

    private final McpClientProperties properties;

    /**
     * serverKey → 已初始化的同步客户端
     */
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /**
     * serverKey → 创建锁（保证同一 serverKey 只建一次）
     */
    private final Map<String, Object> creationLocks = new ConcurrentHashMap<>();

    /**
     * 关闭态（{@link #close()} 置位后不再建连/复用；关停窗口仍可能有 run 线程在调工具）
     */
    private volatile boolean closed = false;

    public McpClientManager(McpClientProperties properties) {
        this.properties = properties == null ? new McpClientProperties() : properties;
    }

    @Override
    public boolean hasServer(String serverKey) {
        return resolveServer(serverKey) != null;
    }

    @Override
    public String callTool(String serverKey, String toolName, Map<String, Object> arguments) {
        if (closed) {
            throw new McpToolException("MCP 客户端已关闭", false);
        }
        McpClientProperties.Server server = resolveServer(serverKey);
        if (server == null) {
            throw new McpToolException("MCP server 未配置: " + serverKey, false);
        }
        Map<String, Object> safeArguments = arguments == null ? Collections.emptyMap() : arguments;
        long startedAt = System.currentTimeMillis();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            McpSyncClient client = null;
            try {
                client = getOrCreateClient(serverKey, server);
                String text = invokeTool(client, server, serverKey, toolName, safeArguments);
                log.info("MCP 工具调用完成 serverKey={} tool={} 耗时={}ms",
                        serverKey, toolName, System.currentTimeMillis() - startedAt);
                return text;
            } catch (McpToolException e) {
                throw e;
            } catch (McpError e) {
                String message = redactSecrets(businessMessage(e), server);
                log.warn("MCP 工具业务错误 serverKey={} tool={} code={} message={}",
                        serverKey, toolName, errorCode(e), safeMessage(message, server));
                throw new McpToolException(message, false, e);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("MCP 工具调用异常{} serverKey={} tool={} error={}: {}",
                        attempt < MAX_ATTEMPTS ? "，重建客户端后重试" : "，重试仍失败",
                        serverKey, toolName, e.getClass().getName(), safeMessage(e.getMessage(), server));
                discardClient(serverKey, client, server);
            }
        }
        throw new McpToolException("MCP server 调用失败，请稍后重试: " + serverKey + "/" + toolName,
                true, lastFailure);
    }

    /**
     * 关闭全部连接并置关闭态；幂等（Spring 容器关闭经 {@link PreDestroy} 触发）。
     * 关闭后不得复用：{@link #callTool} 直接抛不可重试异常，不再建连（防关停窗口连接泄漏）。
     */
    @PreDestroy
    @Override
    public void close() {
        closed = true;
        int closedClients = 0;
        for (String serverKey : new ArrayList<>(clients.keySet())) {
            McpSyncClient client = clients.remove(serverKey);
            if (client != null) {
                closeQuietly(client, configuredServer(serverKey));
                closedClients++;
            }
        }
        if (closedClients > 0) {
            log.info("MCP 客户端已关闭 {} 个连接", closedClients);
        }
    }

    /**
     * 单次工具调用；isError 结果按用户可读错误抛出（不重试）
     */
    private String invokeTool(McpSyncClient client, McpClientProperties.Server server,
                              String serverKey, String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        String text = extractText(result);
        if (result != null && Boolean.TRUE.equals(result.isError())) {
            String message = redactSecrets(text.isEmpty() ? "MCP 工具返回错误: " + toolName : text, server);
            log.warn("MCP 工具返回 isError=true serverKey={} tool={} message={}",
                    serverKey, toolName, truncate(message));
            throw new McpToolException(message, false);
        }
        return text;
    }

    /**
     * 取或建客户端：双重检查 + 按 serverKey 同步创建
     */
    private McpSyncClient getOrCreateClient(String serverKey, McpClientProperties.Server server) {
        if (closed) {
            // 关停窗口双保险：callTool 已前置判定，这里防重试路径在 close() 之后重建连接
            throw new McpToolException("MCP 客户端已关闭", false);
        }
        McpSyncClient existing = clients.get(serverKey);
        if (existing != null) {
            return existing;
        }
        Object lock = creationLocks.computeIfAbsent(serverKey, key -> new Object());
        synchronized (lock) {
            if (closed) {
                // 锁内二次判定：close() 可能发生在持锁等待期间，避免关闭后把新连接塞回缓存
                throw new McpToolException("MCP 客户端已关闭", false);
            }
            existing = clients.get(serverKey);
            if (existing != null) {
                return existing;
            }
            McpSyncClient created = createClient(server);
            // 关停窗口双保险：createClient 含连接+initialize，期间 close() 可能已置位；
            // 关闭后不得把新连接塞回缓存（否则该连接再无机会关闭）
            if (closed) {
                closeQuietly(created, server);
                throw new McpToolException("MCP 客户端已关闭", false);
            }
            clients.put(serverKey, created);
            log.info("MCP 客户端连接成功 serverKey={} baseUrl={}", serverKey, server.getBaseUrl());
            return created;
        }
    }

    /**
     * 创建并初始化一个同步客户端。测试缝：子类可覆盖以注入 Mockito mock，避免连真网。
     *
     * @param server 连接配置
     * @return 已完成 initialize 的客户端
     */
    protected McpSyncClient createClient(McpClientProperties.Server server) {
        String baseUrl = normalizeBaseUrl(server.getBaseUrl());
        String sseEndpoint = resolveSseEndpoint(server);
        String credential = credentialOf(server);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .requestBuilder(HttpRequest.newBuilder().header(API_KEY_HEADER, credential))
                .connectTimeout(Duration.ofMillis(positiveOrDefault(server.getConnectTimeoutMs(),
                        DEFAULT_CONNECT_TIMEOUT_MS)))
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofMillis(positiveOrDefault(server.getRequestTimeoutMs(),
                        DEFAULT_REQUEST_TIMEOUT_MS)))
                .initializationTimeout(Duration.ofMillis(positiveOrDefault(server.getInitializationTimeoutMs(),
                        DEFAULT_INITIALIZATION_TIMEOUT_MS)))
                .jsonSchemaValidator(new PermissiveJsonSchemaValidator())
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .build();
        try {
            client.initialize();
        } catch (RuntimeException e) {
            closeQuietly(client, server);
            throw e;
        }
        return client;
    }

    /**
     * 解析 SSE 端点并拼成绝对 URL（SDK 对相对路径会做 URI.resolve，吃掉 baseUrl 的路径段）
     */
    String resolveSseEndpoint(McpClientProperties.Server server) {
        String baseUrl = normalizeBaseUrl(server.getBaseUrl());
        String endpoint = server.getSseEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = DEFAULT_SSE_ENDPOINT;
        } else {
            endpoint = endpoint.trim();
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
    }

    private McpClientProperties.Server resolveServer(String serverKey) {
        if (serverKey == null || serverKey.trim().isEmpty()
                || properties.getClients() == null) {
            return null;
        }
        McpClientProperties.Server server = properties.getClients().get(serverKey);
        if (server == null || !server.isEnabled()) {
            return null;
        }
        if (isBlank(server.getBaseUrl()) || isBlank(server.getAppKey()) || isBlank(server.getAppSecret())) {
            return null;
        }
        return server;
    }

    private void discardClient(String serverKey, McpSyncClient client, McpClientProperties.Server server) {
        if (client == null) {
            return;
        }
        clients.remove(serverKey, client);
        closeQuietly(client, server);
    }

    private McpClientProperties.Server configuredServer(String serverKey) {
        return properties.getClients() == null ? null : properties.getClients().get(serverKey);
    }

    private static void closeQuietly(McpSyncClient client, McpClientProperties.Server server) {
        try {
            client.close();
        } catch (RuntimeException e) {
            log.warn("MCP 客户端关闭异常 error={}: {}",
                    e.getClass().getName(), safeMessage(e.getMessage(), server));
        }
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent) {
                String value = ((McpSchema.TextContent) content).text();
                if (value != null) {
                    text.append(value);
                }
            }
        }
        return text.toString();
    }

    private static String businessMessage(McpError error) {
        McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError = error.getJsonRpcError();
        String message = jsonRpcError == null ? null : jsonRpcError.message();
        return isBlank(message) ? "MCP 工具业务错误" : message;
    }

    private static Integer errorCode(McpError error) {
        McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError = error.getJsonRpcError();
        return jsonRpcError == null ? null : jsonRpcError.code();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 日志文案脱敏：把配置里的 appSecret 与 Base64 凭证替换为 ***（防御上游异常文案夹带凭据）
     */
    private static String safeMessage(String message, McpClientProperties.Server server) {
        return truncate(redactSecrets(message, server));
    }

    private static String redactSecrets(String message, McpClientProperties.Server server) {
        if (message == null) {
            return null;
        }
        String safe = message;
        if (server != null) {
            if (!isBlank(server.getAppSecret())) {
                safe = safe.replace(server.getAppSecret(), "***");
            }
            safe = safe.replace(credentialOf(server), "***");
        }
        return safe;
    }

    private static String credentialOf(McpClientProperties.Server server) {
        return Base64.getEncoder().encodeToString(
                (server.getAppKey() + ":" + server.getAppSecret()).getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_LOG_MESSAGE_LENGTH
                ? message : message.substring(0, MAX_LOG_MESSAGE_LENGTH) + "...";
    }
}