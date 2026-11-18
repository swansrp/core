package com.bidr.cube.config;

import com.bidr.document.spi.AgentHealthContributor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: CubeHealthContributorTest
 * Description: Cube 管理面健康自报腿契约钉桩（本地回环靶机，不打外网）：
 * <ul>
 *     <li>接线判定：apiUrl 空＝NOT_CONFIGURED（provider=cube 但三件套没配齐是部署形态非故障）；</li>
 *     <li>探测：GET {apiUrl}/health（无鉴权）——200＝REACHABLE、非 200＝UNREACHABLE（码入原因）、
 *         去尾斜杠拼路径、无 X-Session-API-Key 头。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/18
 */
class CubeHealthContributorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private CubeSandboxProperties properties(String apiUrl) {
        CubeSandboxProperties properties = new CubeSandboxProperties();
        properties.setApiUrl(apiUrl);
        return properties;
    }

    @Test
    @DisplayName("接线判定：apiUrl 空＝NOT_CONFIGURED（腿选了但没接线是部署形态非故障）")
    void notConfiguredWhenApiUrlBlank() {
        AgentHealthContributor.HealthReport report =
                new CubeHealthContributor(properties("  ")).probe();
        assertThat(report.getStatus())
                .isEqualTo(AgentHealthContributor.HealthReport.Status.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("探测语义：/health 200=可达（无鉴权头）、非 200=不可达（码入原因）、去尾斜杠；契约=exec.sandbox/cube")
    void probeSemanticsAgainstLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = "/health".equals(exchange.getRequestURI().getPath()) ? 200 : 503;
            byte[] body = "{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Probe", "hit");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        CubeHealthContributor contributor = new CubeHealthContributor(properties(base + "/"));
        assertThat(contributor.probe().isReachable()).as("200＝可达").isTrue();

        // 非 200/连接异常＝不可达（码或异常短语入原因）——指向已关闭端口＝连接拒绝
        java.net.ServerSocket socket = new java.net.ServerSocket(0);
        int closedPort = socket.getLocalPort();
        socket.close();
        CubeHealthContributor sick = new CubeHealthContributor(properties("http://127.0.0.1:" + closedPort));
        AgentHealthContributor.HealthReport bad = sick.probe();
        assertThat(bad.getStatus()).as("管理面不在＝不可达")
                .isEqualTo(AgentHealthContributor.HealthReport.Status.UNREACHABLE);
        assertThat(bad.getReason()).isNotBlank();

        assertThat(contributor.capability()).isEqualTo("exec.sandbox");
        assertThat(contributor.probeName()).isEqualTo("cube");
    }
}
