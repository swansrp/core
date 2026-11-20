package com.bidr.mcp.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: McpClientManagerTest
 * Description: 棒1 单测（不连真网）：覆盖配置判据、结果文本拼接、业务错误不可重试、
 * 传输错误重建重试、重试耗尽、isError 结果、SSE 绝对 URL 拼装、{@code my.mcp.clients.*}
 * 绑定（M-12）与日志不泄密。
 *
 * @author sharp
 * @since 2026/11/20
 */
public class McpClientManagerTest {

    private static final String SERVER_KEY = "inkhub-kb";

    private static final String TOOL_NAME = "search_project_knowledge";

    private static final String APP_KEY = "test-app-key";

    private static final String APP_SECRET = "test-app-secret";

    private Logger managerLogger;

    private ListAppender<ILoggingEvent> logAppender;

    @After
    public void tearDown() {
        if (managerLogger != null && logAppender != null) {
            managerLogger.detachAppender(logAppender);
        }
    }

    // ---------------------------------------------------------------- 配置判据

    @Test
    public void hasServerFalseWhenNoConfiguration() {
        TestableMcpClientManager manager = newManager(new McpClientProperties());

        assertFalse(manager.hasServer(SERVER_KEY));
        assertNotConfigured(manager);
    }

    @Test
    public void hasServerFalseWhenDisabled() {
        McpClientProperties.Server server = validServer();
        server.setEnabled(false);
        TestableMcpClientManager manager = newManager(properties(server));

        assertFalse(manager.hasServer(SERVER_KEY));
        assertNotConfigured(manager);
    }

    @Test
    public void hasServerFalseWhenBaseUrlAppKeyOrAppSecretMissing() {
        assertIncomplete(server -> server.setBaseUrl(" "));
        assertIncomplete(server -> server.setAppKey(null));
        assertIncomplete(server -> server.setAppSecret(""));
    }

    // ---------------------------------------------------------------- 成功调用

    @Test
    public void callToolReturnsConcatenatedTextAndReusesSingleClient() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any())).thenReturn(result(Boolean.FALSE, "{\"code\":", "\"ok\"}"));
        TestableMcpClientManager manager = newManager(properties(validServer()), clientFactory(client));

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("project_code", "P1");
        arguments.put("query", "进度");

        assertEquals("{\"code\":\"ok\"}", manager.callTool(SERVER_KEY, TOOL_NAME, arguments));
        assertEquals("{\"code\":\"ok\"}", manager.callTool(SERVER_KEY, TOOL_NAME, arguments));

        ArgumentCaptor<McpSchema.CallToolRequest> captor =
                ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client, times(2)).callTool(captor.capture());
        McpSchema.CallToolRequest request = captor.getAllValues().get(0);
        assertEquals(TOOL_NAME, request.name());
        assertSame(arguments, request.arguments());
        assertEquals("同一 serverKey 只建一次客户端", 1, manager.getCreateCount());
    }

    @Test
    public void callToolThrowsNonRetryableWhenResultIsError() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any())).thenReturn(result(Boolean.TRUE, "知识库为空"));
        TestableMcpClientManager manager = newManager(properties(validServer()), clientFactory(client));

        try {
            manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("isError 结果不可重试", e.isRetryable());
            assertEquals("知识库为空", e.getUserMessage());
        }
        assertEquals("isError 不触发重试", 1, manager.getCreateCount());
    }

    // ---------------------------------------------------------------- 业务错误

    @Test
    public void callToolWrapsMcpErrorAsNonRetryableWithBusinessMessage() {
        McpError businessError = new McpError(
                new McpSchema.JSONRPCResponse.JSONRPCError(-32603, "项目不存在或已删除: P1"));
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any())).thenThrow(businessError);
        TestableMcpClientManager manager = newManager(properties(validServer()), clientFactory(client));

        try {
            manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("业务错误不可重试", e.isRetryable());
            assertEquals("项目不存在或已删除: P1", e.getUserMessage());
            assertEquals("项目不存在或已删除: P1", e.getMessage());
            assertSame(businessError, e.getCause());
        }
        assertEquals("业务错误不重建客户端", 1, manager.getCreateCount());
    }

    // ---------------------------------------------------------------- 传输重试

    @Test
    public void callToolRebuildsClientAndRetriesOnceAfterTransportFailure() {
        McpSyncClient broken = mock(McpSyncClient.class);
        when(broken.callTool(any())).thenThrow(new RuntimeException("connection reset"));
        McpSyncClient healthy = mock(McpSyncClient.class);
        when(healthy.callTool(any())).thenReturn(result(Boolean.FALSE, "ok"));
        TestableMcpClientManager manager = newManager(properties(validServer()),
                clientFactory(broken), clientFactory(healthy));

        assertEquals("ok", manager.callTool(SERVER_KEY, TOOL_NAME, null));
        assertEquals("传输失败后重建一次", 2, manager.getCreateCount());
        verify(broken).close();
        verify(healthy, never()).close();
    }

    @Test
    public void callToolThrowsRetryableWhenRetryAlsoFails() {
        RuntimeException first = new RuntimeException("read timeout");
        RuntimeException second = new RuntimeException("sse stream closed");
        McpSyncClient brokenOnce = mock(McpSyncClient.class);
        when(brokenOnce.callTool(any())).thenThrow(first);
        McpSyncClient brokenTwice = mock(McpSyncClient.class);
        when(brokenTwice.callTool(any())).thenThrow(second);
        TestableMcpClientManager manager = newManager(properties(validServer()),
                clientFactory(brokenOnce), clientFactory(brokenTwice));

        try {
            manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertTrue("重试耗尽可重试", e.isRetryable());
            assertSame(second, e.getCause());
            assertTrue(e.getUserMessage().contains("稍后重试"));
        }
        assertEquals(2, manager.getCreateCount());
        verify(brokenOnce).close();
        verify(brokenTwice).close();
    }

    @Test
    public void callToolRebuildsWhenClientCreationFailsOnce() {
        McpSyncClient healthy = mock(McpSyncClient.class);
        when(healthy.callTool(any())).thenReturn(result(Boolean.FALSE, "ok"));
        TestableMcpClientManager manager = newManager(properties(validServer()),
                () -> {
                    throw new RuntimeException("initialize timeout");
                },
                clientFactory(healthy));

        assertEquals("ok", manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap()));
        assertEquals(2, manager.getCreateCount());
    }

    // ---------------------------------------------------------------- 关闭幂等

    @Test
    public void closeIsIdempotentAndBlocksFurtherCalls() {
        McpSyncClient first = mock(McpSyncClient.class);
        when(first.callTool(any())).thenReturn(result(Boolean.FALSE, "ok"));
        McpSyncClient second = mock(McpSyncClient.class);
        when(second.callTool(any())).thenReturn(result(Boolean.FALSE, "ok"));
        McpClientProperties properties = new McpClientProperties();
        properties.getClients().put("server-a", validServer());
        properties.getClients().put("server-b", validServer());
        TestableMcpClientManager manager = newManager(properties,
                clientFactory(first), clientFactory(second));

        assertEquals("ok", manager.callTool("server-a", TOOL_NAME, Collections.emptyMap()));
        assertEquals("ok", manager.callTool("server-b", TOOL_NAME, Collections.emptyMap()));
        assertEquals(2, manager.getCreateCount());

        manager.close();
        manager.close(); // 幂等：第二次调用不抛

        verify(first, times(1)).close();
        verify(second, times(1)).close();
        assertEquals("关闭后不得再建连", 2, manager.getCreateCount());

        try {
            manager.callTool("server-a", TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("关闭态不可重试", e.isRetryable());
            assertTrue("关闭态文案可读", e.getUserMessage().contains("已关闭"));
        }
        assertEquals("关闭后 callTool 不得重建客户端", 2, manager.getCreateCount());
    }

    // ---------------------------------------------------------------- SSE 端点

    @Test
    public void sseEndpointIsAlwaysResolvedToAbsoluteUrl() {
        TestableMcpClientManager manager = newManager(new McpClientProperties());

        McpClientProperties.Server server = validServer();
        assertEquals("http://10.3.6.60/inkhub/mcp/inkhub", manager.resolveSseEndpoint(server));

        server.setBaseUrl("http://10.3.6.60/inkhub/");
        server.setSseEndpoint("mcp/inkhub");
        assertEquals("http://10.3.6.60/inkhub/mcp/inkhub", manager.resolveSseEndpoint(server));

        server.setSseEndpoint("http://other-host:8080/mcp/inkhub");
        assertEquals("http://other-host:8080/mcp/inkhub", manager.resolveSseEndpoint(server));

        server.setSseEndpoint(null);
        assertEquals("http://10.3.6.60/inkhub/mcp", manager.resolveSseEndpoint(server));
    }

    // ---------------------------------------------------------------- 配置绑定（M-12）

    @Test
    public void bindsMyMcpClientsFromConfigurationProperties() {
        Map<String, Object> source = new LinkedHashMap<>();
        // 既有标量键共存，不干扰 clients 子树绑定
        source.put("my.mcp.path", "/mcp");
        source.put("my.mcp.keepalive-seconds", "15");
        source.put("my.mcp.clients.inkhub-kb.enabled", "true");
        source.put("my.mcp.clients.inkhub-kb.base-url", "http://10.3.6.60/inkhub");
        source.put("my.mcp.clients.inkhub-kb.sse-endpoint", "/mcp/inkhub");
        source.put("my.mcp.clients.inkhub-kb.app-key", APP_KEY);
        source.put("my.mcp.clients.inkhub-kb.app-secret", APP_SECRET);
        source.put("my.mcp.clients.inkhub-kb.connect-timeout-ms", "1234");
        source.put("my.mcp.clients.inkhub-kb.initialization-timeout-ms", "2345");
        source.put("my.mcp.clients.inkhub-kb.request-timeout-ms", "3456");
        // 最小配置：验证默认值
        source.put("my.mcp.clients.minimal.base-url", "http://minimal-host");
        source.put("my.mcp.clients.minimal.app-key", "k");
        source.put("my.mcp.clients.minimal.app-secret", "s");

        McpClientProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("my.mcp", Bindable.of(McpClientProperties.class))
                .get();

        McpClientProperties.Server inkhub = properties.getClients().get("inkhub-kb");
        assertNotNull("my.mcp.clients.inkhub-kb 应可绑定", inkhub);
        assertTrue(inkhub.isEnabled());
        assertEquals("http://10.3.6.60/inkhub", inkhub.getBaseUrl());
        assertEquals("/mcp/inkhub", inkhub.getSseEndpoint());
        assertEquals(APP_KEY, inkhub.getAppKey());
        assertEquals(APP_SECRET, inkhub.getAppSecret());
        assertEquals(1234L, inkhub.getConnectTimeoutMs());
        assertEquals(2345L, inkhub.getInitializationTimeoutMs());
        assertEquals(3456L, inkhub.getRequestTimeoutMs());

        McpClientProperties.Server minimal = properties.getClients().get("minimal");
        assertNotNull(minimal);
        assertEquals("默认 enabled", true, minimal.isEnabled());
        assertEquals("默认 sseEndpoint", "/mcp", minimal.getSseEndpoint());
        assertEquals("默认 connect", 5000L, minimal.getConnectTimeoutMs());
        assertEquals("默认 initialization", 10000L, minimal.getInitializationTimeoutMs());
        assertEquals("默认 request", 30000L, minimal.getRequestTimeoutMs());
    }

    // ---------------------------------------------------------------- 日志不泄密

    @Test
    public void logsNeverContainSecretOrApiKeyHeader() {
        managerLogger = (Logger) LoggerFactory.getLogger(McpClientManager.class);
        Level previousLevel = managerLogger.getLevel();
        managerLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        managerLogger.addAppender(logAppender);
        try {
            TestableMcpClientManager manager = newManager(properties(validServer()),
                    () -> {
                        throw new RuntimeException("connection refused: appSecret=" + APP_SECRET);
                    },
                    () -> {
                        throw new RuntimeException("still refused");
                    });

            try {
                manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
                fail("expected McpToolException");
            } catch (McpToolException e) {
                assertFalse("异常文案不含 secret", e.getUserMessage().contains(APP_SECRET));
            }

            String allLogs = logAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertFalse("日志不应出现 appSecret", allLogs.contains(APP_SECRET));
            String credential = Base64.getEncoder().encodeToString(
                    (APP_KEY + ":" + APP_SECRET).getBytes(StandardCharsets.UTF_8));
            assertFalse("日志不应出现 x-api-key 值", allLogs.contains(credential));
            assertTrue("应记录 serverKey 便于排查", allLogs.contains(SERVER_KEY));
        } finally {
            managerLogger.setLevel(previousLevel);
        }
    }

    @Test
    public void mcpErrorMessageIsRedactedBeforeUserMessage() {
        String credential = Base64.getEncoder().encodeToString(
                (APP_KEY + ":" + APP_SECRET).getBytes(StandardCharsets.UTF_8));
        McpError businessError = new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(
                -32603, "credential invalid: appSecret=" + APP_SECRET + ", x-api-key=" + credential));
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any())).thenThrow(businessError);
        TestableMcpClientManager manager = newManager(properties(validServer()), clientFactory(client));

        try {
            manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("McpError 文案不得含 appSecret", e.getUserMessage().contains(APP_SECRET));
            assertFalse("McpError 文案不得含 x-api-key 值", e.getUserMessage().contains(credential));
            assertTrue("凭证片段应替换为 ***", e.getUserMessage().contains("***"));
        }
    }

    @Test
    public void isErrorTextIsRedactedBeforeUserMessage() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any())).thenReturn(result(Boolean.TRUE,
                "upstream auth failed: appSecret=" + APP_SECRET));
        TestableMcpClientManager manager = newManager(properties(validServer()), clientFactory(client));

        try {
            manager.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("isError 文案不得含 appSecret", e.getUserMessage().contains(APP_SECRET));
            assertTrue("凭证片段应替换为 ***", e.getUserMessage().contains("***"));
        }
    }

    // ---------------------------------------------------------------- helpers

    private void assertIncomplete(Consumer<McpClientProperties.Server> breaker) {
        McpClientProperties.Server server = validServer();
        breaker.accept(server);
        TestableMcpClientManager manager = newManager(properties(server));

        assertFalse(manager.hasServer(SERVER_KEY));
        assertNotConfigured(manager);
    }

    private static void assertNotConfigured(McpToolClient client) {
        try {
            client.callTool(SERVER_KEY, TOOL_NAME, Collections.emptyMap());
            fail("expected McpToolException");
        } catch (McpToolException e) {
            assertFalse("未配置不可重试", e.isRetryable());
            assertTrue("未配置文案可读", e.getUserMessage().contains("未配置"));
        }
    }

    private static McpClientProperties properties(McpClientProperties.Server server) {
        McpClientProperties properties = new McpClientProperties();
        properties.getClients().put(SERVER_KEY, server);
        return properties;
    }

    private static McpClientProperties.Server validServer() {
        McpClientProperties.Server server = new McpClientProperties.Server();
        server.setBaseUrl("http://10.3.6.60/inkhub");
        server.setSseEndpoint("/mcp/inkhub");
        server.setAppKey(APP_KEY);
        server.setAppSecret(APP_SECRET);
        return server;
    }

    private static McpSchema.CallToolResult result(Boolean isError, String... texts) {
        List<McpSchema.Content> contents = new ArrayList<>();
        for (String text : texts) {
            contents.add(new McpSchema.TextContent(text));
        }
        return new McpSchema.CallToolResult(contents, isError, null, null);
    }

    private static Supplier<McpSyncClient> clientFactory(McpSyncClient client) {
        return () -> client;
    }

    @SafeVarargs
    private static TestableMcpClientManager newManager(McpClientProperties properties,
                                                       Supplier<McpSyncClient>... factories) {
        return new TestableMcpClientManager(properties, Arrays.asList(factories));
    }

    /**
     * 测试用管理器：覆盖 {@code createClient} 注入 mock，杜绝连真网；记录创建次数。
     */
    private static final class TestableMcpClientManager extends McpClientManager {

        private final List<Supplier<McpSyncClient>> factories;

        private int createCount;

        private TestableMcpClientManager(McpClientProperties properties,
                                         List<Supplier<McpSyncClient>> factories) {
            super(properties);
            this.factories = new ArrayList<>(factories);
        }

        @Override
        protected McpSyncClient createClient(McpClientProperties.Server server) {
            createCount++;
            if (factories.isEmpty()) {
                throw new IllegalStateException("unexpected createClient call #" + createCount);
            }
            return factories.remove(0).get();
        }

        private int getCreateCount() {
            return createCount;
        }
    }
}