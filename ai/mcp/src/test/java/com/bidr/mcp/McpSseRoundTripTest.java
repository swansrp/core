package com.bidr.mcp;

import com.bidr.mcp.anno.McpServerEndpoint;
import com.bidr.mcp.anno.Param;
import com.bidr.mcp.anno.ToolMapping;
import com.bidr.mcp.config.IMcpServerEndpoint;
import com.bidr.mcp.config.McpResultConverter;
import com.bidr.mcp.context.McpCallContext;
import com.bidr.mcp.json.JacksonMcpJsonMapper;
import com.bidr.mcp.json.PermissiveJsonSchemaValidator;
import com.bidr.mcp.registry.McpEndpointDefinition;
import com.bidr.mcp.registry.McpEndpointScanner;
import com.bidr.mcp.registry.McpToolDefinition;
import com.bidr.mcp.registry.McpToolInvoker;
import com.bidr.mcp.transport.McpDispatcherServlet;
import com.bidr.mcp.transport.McpSseTransportProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: McpSseRoundTripTest
 * Description: 棒1 回环自测（方案 §6 验收闸）：不起 HTTP 服务，用 spring-test 的
 * Servlet mock 直驱总入口 Servlet，把「SSE 建连 → initialize → tools/list →
 * tools/call」的 JSON-RPC 全链在内存里走一遍，断言 wire 形态与调用端不变口径：
 * <ul>
 * <li>endpoint 事件 data 形态 {@code <path>/message?sessionId=…}</li>
 * <li>POST 应答 200 空体、JSON-RPC 应答走 SSE message 事件</li>
 * <li>tools/list schema：snake_case 参数、runtime_meta 可选 object</li>
 * <li>tools/call 返回 JSON 文本 content</li>
 * <li>请求头捕获：SSE 建连头在工具调用时仍可读（X-MCP-Key 场景）</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/11/14
 */
public class McpSseRoundTripTest {

    private McpDispatcherServlet servlet;

    private McpSseTransportProvider provider;

    @McpServerEndpoint(name = "回环测试", sseEndpoint = "/mcp/test")
    public static class TestEndpoint implements IMcpServerEndpoint {

        @ToolMapping(name = "echo_tool", description = "回声工具", resultConverter = McpResultConverter.class)
        public Map<String, Object> echo(
                @Param(value = "content", name = "content", description = "内容") String content,
                @Param(value = "runtime_meta", name = "runtime_meta", required = false,
                        description = "平台运行元数据") Map<String, Object> runtimeMeta) {
            Map<String, Object> result = new LinkedHashMap<>();
            McpCallContext context = McpCallContext.current();
            result.put("authorized", context != null && "test-key".equals(context.header("X-MCP-Key")));
            result.put("content", content);
            result.put("session_id", runtimeMeta == null ? null : String.valueOf(runtimeMeta.get("session_id")));
            result.put("saw_args", context != null && context.args().containsKey("content"));
            return result;
        }
    }

    @Before
    public void setUp() {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

        McpEndpointDefinition definition = McpEndpointScanner.scan(new TestEndpoint());
        assertNotNull(definition);
        assertEquals(1, definition.getTools().size());

        provider = new McpSseTransportProvider(jsonMapper, definition.getSseEndpoint(),
                definition.getSseEndpoint() + "/message", 0L);

        List<McpServerFeatures.SyncToolSpecification> specs = definition.getTools().stream()
                .map(tool -> McpToolInvoker.toSpecification(definition, tool)).toList();

        McpServer.sync(provider)
                .serverInfo(new McpSchema.Implementation(definition.getName(), null, "1.0.0", null, null, null))
                .capabilities(new McpSchema.ServerCapabilities(null, null, null, null, null,
                        new McpSchema.ServerCapabilities.ToolCapabilities(false), null))
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(new PermissiveJsonSchemaValidator())
                .validateToolInputs(false)
                .tools(specs)
                .build();

        servlet = new McpDispatcherServlet();
        servlet.register(provider);
    }

    @Test
    public void fullRoundTrip() throws Exception {
        // ---- 1. SSE 建连：建连时带 X-MCP-Key（旧 Solon 语境：建连头在工具调用时仍可读）
        MockHttpServletResponse sse = sseConnect("/mcp/test", "test-key");
        String initial = sse.getContentAsString();
        assertTrue("SSE content-type", sse.getContentType().startsWith("text/event-stream"));
        assertTrue("endpoint 事件", initial.contains("event: endpoint"));
        assertTrue("endpoint data 形态", initial.contains("data: /mcp/test/message?sessionId="));
        String sessionId = sessionIdOf(initial);
        String messagePath = "/mcp/test/message";

        // ---- 2. initialize：应答走 SSE 流
        String afterInit = post(messagePath, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"roundtrip\",\"version\":\"0\"}}}", null, sse);
        assertTrue("initialize 应答在 SSE 流上（serverInfo）", afterInit.contains("serverInfo"));
        assertTrue("协议版本锁定 2024-11-05", afterInit.contains("2024-11-05"));

        // ---- 3. notifications/initialized（通知无应答）
        post(messagePath, sessionId, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", null, sse);

        // ---- 4. tools/list：snake_case 参数 + runtime_meta 可选 object
        String afterList = post(messagePath, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                null, sse);
        assertTrue("工具名", afterList.contains("echo_tool"));
        assertTrue("inputSchema type=object", afterList.contains("inputSchema"));
        assertTrue("runtime_meta 声明为 object", afterList.contains("\"runtime_meta\":{\"type\":\"object\""));
        assertTrue("content 必填", afterList.contains("\"required\":[\"content\"]"));

        // ---- 5. tools/call：POST 不带 key，靠建连头授权；runtime_meta 注入直收
        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo_tool\",\"arguments\":{\"content\":\"hello\","
                + "\"runtime_meta\":{\"session_id\":\"sess-42\"}}}}";
        String afterCall = post(messagePath, sessionId, callBody, null, sse);
        assertTrue("结果经 resultConverter 成 JSON 文本 content", afterCall.contains("\"type\":\"text\""));
        // text content 内的 JSON 引号在 wire 上被转义（\"authorized\":true）
        assertTrue("建连头在工具调用时仍可读（authorized）", afterCall.contains("\\\"authorized\\\":true"));
        assertTrue("参数绑定 snake_case", afterCall.contains("hello"));
        assertTrue("runtime_meta 直收", afterCall.contains("sess-42"));

        // ---- 6. 无密钥会话：拒绝（业务层返回 failure 而非协议错误；先走标准握手）
        MockHttpServletResponse sseNoKey = sseConnect("/mcp/test", null);
        String sid2 = sessionIdOf(sseNoKey.getContentAsString());
        post(messagePath, sid2, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"roundtrip\",\"version\":\"0\"}}}", null, sseNoKey);
        post(messagePath, sid2, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", null, sseNoKey);
        String afterCall2 = post(messagePath, sid2, callBody, null, sseNoKey);
        assertTrue("无密钥不授权", afterCall2.contains("\\\"authorized\\\":false"));

        // ---- 7. POST 头优先于建连头：建连带合法 key、POST 带错 key ⇒ 按本次 POST 头拒绝
        MockHttpServletResponse sseOverride = sseConnect("/mcp/test", "test-key");
        String sid3 = sessionIdOf(sseOverride.getContentAsString());
        post(messagePath, sid3, "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"roundtrip\",\"version\":\"0\"}}}", "wrong-key", sseOverride);
        post(messagePath, sid3, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "wrong-key",
                sseOverride);
        String afterOverride = post(messagePath, sid3, callBody, "wrong-key", sseOverride);
        assertTrue("POST 头优先于建连头（覆盖语义）", afterOverride.contains("\\\"authorized\\\":false"));

        // ---- 8. 必填缺失：JSON-RPC 错误信封（绑定层异常上抛，由协议层转信封）
        String badCall = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo_tool\",\"arguments\":{}}}";
        String afterBad = post(messagePath, sessionId, badCall, null, sse);
        assertTrue("必填缺失报错", afterBad.toLowerCase().contains("error")
                && afterBad.contains("Required parameter @content"));

        // ---- 9. 超限 body：声明超长 → 413（Content-Length 预检路；chunked 无声明长度时的
        // 流式计数分支为 F-1 的另一条路，mock setContent 会同步设 contentLength 走不到，
        // 该分支与官方 HttpServletRequestUtils.readBody 同语义，登记 change-log 潜伏缺口）
        MockHttpServletRequest huge = new MockHttpServletRequest("POST", messagePath);
        huge.setContentType("application/json");
        byte[] bigBody = new byte[16 * 1024 * 1024 + 1];
        java.util.Arrays.fill(bigBody, (byte) '{');
        huge.setContent(bigBody);
        huge.setParameter("sessionId", sessionId);
        MockHttpServletResponse hugeResp = new MockHttpServletResponse();
        servlet.service(huge, hugeResp);
        assertEquals("超限 body 413", 413, hugeResp.getStatus());

        // ---- 10. 未知会话：HTTP 404
        MockHttpServletRequest unknown = jsonPost(messagePath, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");
        unknown.setParameter("sessionId", "not-exist");
        MockHttpServletResponse unknownResp = new MockHttpServletResponse();
        servlet.service(unknown, unknownResp);
        assertEquals("未知会话 404", 404, unknownResp.getStatus());

        // ---- 11. 未注册路径：404
        MockHttpServletRequest stray = new MockHttpServletRequest("GET", "/mcp/other");
        MockHttpServletResponse strayResp = new MockHttpServletResponse();
        servlet.service(stray, strayResp);
        assertEquals("未注册路径 404", 404, strayResp.getStatus());
    }

    private MockHttpServletResponse sseConnect(String path, String accessKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAsyncSupported(true);
        if (accessKey != null) {
            request.addHeader("X-MCP-Key", accessKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        return response;
    }

    /**
     * POST 一条 JSON-RPC，返回 SSE 流上累积的全部内容（应答应出现在其中）
     */
    private String post(String path, String sessionId, String body, String extraHeader,
                        MockHttpServletResponse sse) throws Exception {
        MockHttpServletRequest request = jsonPost(path, body);
        request.setParameter("sessionId", sessionId);
        if (extraHeader != null) {
            request.addHeader("X-MCP-Key", extraHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        assertEquals("POST 应答 200 空体", 200, response.getStatus());
        assertEquals("POST 响应体必须为空（应答走 SSE 流）", 0, response.getContentAsByteArray().length);
        return sse.getContentAsString();
    }

    private MockHttpServletRequest jsonPost(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String sessionIdOf(String sseContent) {
        int at = sseContent.indexOf("sessionId=");
        assertNotNull("endpoint 事件含 sessionId", at >= 0 ? Boolean.TRUE : null);
        String rest = sseContent.substring(at + "sessionId=".length()).trim();
        StringBuilder id = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (c == '\n' || c == '\r' || c == ' ' || c == '\"') {
                break;
            }
            id.append(c);
        }
        assertTrue("sessionId 非空", id.length() > 0);
        return id.toString();
    }
}
