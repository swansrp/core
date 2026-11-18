package com.bidr.agent.runtime.provider.openhands;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.kernel.exception.ServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: OpenHandsClientBashTest
 * Description: {@code executeBash}（bash 同步端点）的契约钉桩——端点形状按 openhands-agent-server
 * 1.62.0 wheel 解包核对（M-5）：{@code POST /api/conversations/{cid}/bash/execute_bash_command}，
 * body={command, timeout(秒)}，响应=BashOutput{exit_code/stdout/stderr}。本地回环
 * {@link HttpServer} 造 fake（不打外网），钉四件事：路径/鉴权头/超时毫秒→秒折算、
 * BashOutput 字段映射、exit_code=-1/null=超时口径、非 2xx 走既有 mapError（404→40402）。
 * <p>
 * ⚠️ 本模块跑在 JUnit 5（surefire-junit-platform）上：写成 JUnit 4 会被静默跳过。
 *
 * @author Sharp
 * @since 2026/11/14
 */
class OpenHandsClientBashTest {

    private static final String CID = "11111111-2222-3333-4444-555555555555";

    private HttpServer server;
    private OpenHandsClient client;
    private String lastMethod;
    private String lastPath;
    private String lastAuthHeader;
    private String lastBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 起 fake 并建指向它的 client；bash 响应体/状态码可配 */
    private void startClient(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, status, responseBody));
        server.start();
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("sk-bash-test-key");
        client = new OpenHandsClient(new AgentRuntimeConfigProvider(properties, null));
    }

    private void handle(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        lastMethod = exchange.getRequestMethod();
        lastPath = exchange.getRequestURI().getPath();
        lastAuthHeader = exchange.getRequestHeaders().getFirst("X-Session-API-Key");
        lastBody = body.length == 0 ? "" : new String(body, StandardCharsets.UTF_8);
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    @Test
    @DisplayName("正常映射：路径/鉴权头/毫秒→秒向上取整 + BashOutput 三字段忠实回传")
    void mapsBashOutputFields() throws Exception {
        startClient(200, "{\"command_id\":\"c1\",\"order\":0,\"exit_code\":0,"
                + "\"stdout\":\"hello\",\"stderr\":\"warn\"}");
        OpenHandsClient.BashExecResult result = client.executeBash(CID, "echo hi", 123_456);
        assertEquals(0, result.exitCode());
        assertEquals("hello", result.stdout());
        assertEquals("warn", result.stderr());
        assertFalse(result.timedOut());
        assertEquals("POST", lastMethod);
        assertEquals("/api/conversations/" + CID + "/bash/execute_bash_command", lastPath);
        assertEquals("sk-bash-test-key", lastAuthHeader);
        assertTrue(lastBody.contains("\"command\":\"echo hi\""), "body 须带 command：[" + lastBody + "]");
        assertTrue(lastBody.contains("\"timeout\":124"), "123456ms 应折算 124 秒：[" + lastBody + "]");
    }

    @Test
    @DisplayName("超时口径：exit_code=-1 → timedOut=true；exit_code 缺失/null 同样归 -1")
    void timeoutMeansExitCodeMinusOne() throws Exception {
        startClient(200, "{\"command_id\":\"c1\",\"exit_code\":-1,\"stdout\":\"partial\"}");
        OpenHandsClient.BashExecResult timedOut = client.executeBash(CID, "sleep 999", 60_000);
        assertTrue(timedOut.timedOut());
        assertEquals(-1, timedOut.exitCode());
        assertEquals("partial", timedOut.stdout());

        // 上游「未取得退出码」在同步端点归一成 -1（1.62.0 bash_router._collect_bash_output）
        startClient(200, "{\"command_id\":\"c2\",\"stdout\":null,\"stderr\":null}");
        OpenHandsClient.BashExecResult nullExit = client.executeBash(CID, "cmd", 60_000);
        assertTrue(nullExit.timedOut());
        assertEquals(-1, nullExit.exitCode());
        assertEquals(null, nullExit.stdout());
        assertEquals(null, nullExit.stderr());
    }

    @Test
    @DisplayName("最小超时折算：亚秒时限至少按 1 秒发（上游 timeout 单位是秒）")
    void subSecondTimeoutClampsToOneSecond() throws Exception {
        startClient(200, "{\"command_id\":\"c1\",\"exit_code\":0}");
        client.executeBash(CID, "true", 500);
        assertTrue(lastBody.contains("\"timeout\":1"), "500ms 应折算 1 秒：[" + lastBody + "]");
    }

    @Test
    @DisplayName("非 2xx 走既有 mapError：404 → 平台码 40402（会话语义直通）")
    void mapsHttpErrorThroughExistingMapError() throws Exception {
        startClient(404, "{\"detail\":\"Conversation not found\"}");
        ServiceException e = assertThrows(ServiceException.class,
                () -> client.executeBash(CID, "echo hi", 60_000));
        assertEquals(40402, e.getErrCode().getErrCode());
    }

    @Test
    @DisplayName("分片已合并：stdout/stderr 为上游 join 后的单份值，客户端不做合并/截断")
    void clientDoesNotMergeOrTruncate() throws Exception {
        // 100KB stdout：客户端原样回传（64KB 截断是 OpenHandsSandboxProvider 的职责）
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 102_400; i++) {
            big.append('x');
        }
        startClient(200, "{\"exit_code\":0,\"stdout\":\"" + big + "\"}");
        OpenHandsClient.BashExecResult result = client.executeBash(CID, "cat big", 60_000);
        assertEquals(102_400, result.stdout().length());
    }
}
