package com.bidr.agent.runtime.config;

import com.bidr.document.spi.AgentHealthContributor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: OpenHandsHealthContributorTest
 * Description: OpenHands 健康自报腿契约钉桩（本地回环靶机，不打外网）：
 * <ul>
 *     <li>接线判定：地址与密钥缺任一＝NOT_CONFIGURED 不发包；</li>
 *     <li>探测：GET {base}/api/agent-profiles 带 X-Session-API-Key 头——200＝REACHABLE、
 *         401＝UNREACHABLE（固定文案「密钥无效或未配置」，密钥不进原因）、其余码带码入原因；</li>
 *     <li>URL 拼装：base 去尾斜杠。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/18
 */
class OpenHandsHealthContributorTest {

    private HttpServer server;

    /** 靶机抓到的最近一次 X-Session-API-Key 头值 */
    private final List<String> capturedKeys = new java.util.concurrent.CopyOnWriteArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AgentRuntimeConfigProvider config(String baseUrl, String apiKey) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        return new AgentRuntimeConfigProvider(properties, null);
    }

    @Test
    @DisplayName("接线判定：地址或密钥缺任一＝NOT_CONFIGURED 不发包")
    void notConfiguredWhenIncomplete() {
        AgentHealthContributor.HealthReport missingKey =
                new OpenHandsHealthContributor(config("http://127.0.0.1:1", "  ")).probe();
        assertThat(missingKey.getStatus())
                .isEqualTo(AgentHealthContributor.HealthReport.Status.NOT_CONFIGURED);
        AgentHealthContributor.HealthReport missingUrl =
                new OpenHandsHealthContributor(config("", "sk-x")).probe();
        assertThat(missingUrl.getStatus())
                .isEqualTo(AgentHealthContributor.HealthReport.Status.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("探测语义：200=可达（密钥经头下发，不入原因）、401=不可达（密钥无效或未配置）、去尾斜杠拼路径")
    void probeSemanticsAgainstLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestHeaders().getFirst("X-Session-API-Key");
            capturedKeys.add(path + "|" + key);
            if ("/api/agent-profiles".equals(path) && "sk-good".equals(key)) {
                exchange.sendResponseHeaders(200, 2);
            } else if ("/api/agent-profiles".equals(path)) {
                exchange.sendResponseHeaders(401, 2);
            } else {
                exchange.sendResponseHeaders(404, 2);
            }
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        AgentHealthContributor.HealthReport ok =
                new OpenHandsHealthContributor(config(base + "/", "sk-good")).probe();
        assertThat(ok.isReachable()).as("200＝可达").isTrue();
        assertThat(capturedKeys).as("密钥经 X-Session-API-Key 头下发").contains("/api/agent-profiles|sk-good");

        AgentHealthContributor.HealthReport unauthorized =
                new OpenHandsHealthContributor(config(base, "sk-bad")).probe();
        assertThat(unauthorized.getStatus())
                .as("401＝不可达（排障方向与「服务不在」区分）")
                .isEqualTo(AgentHealthContributor.HealthReport.Status.UNREACHABLE);
        assertThat(unauthorized.getReason()).isEqualTo("密钥无效或未配置");
        assertThat(unauthorized.getReason()).as("密钥不得进原因").doesNotContain("sk-bad");

        // 元数据自证：capability/probeName 契约（聚合器行标签与快照键同源）
        OpenHandsHealthContributor contributor = new OpenHandsHealthContributor(config(base, "sk-good"));
        assertThat(contributor.capability()).isEqualTo("exec.sandbox");
        assertThat(contributor.probeName()).isEqualTo("openhands");
    }
}
