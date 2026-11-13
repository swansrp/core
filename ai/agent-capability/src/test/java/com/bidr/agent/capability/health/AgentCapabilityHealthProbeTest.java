package com.bidr.agent.capability.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: AgentCapabilityHealthProbeTest
 * Description: 探活件契约钉桩（探针注入替身，不打外网）：
 * <ul>
 *     <li>未配置三态：base-url 空＝不发包（替身零调用）不计失败，日志位标「未配置」；</li>
 *     <li>URL 拼装：base 去尾斜杠后拼 {@code /docs}（mineru）与 {@code /health}（gotenberg）；</li>
 *     <li>状态变化：首轮必出一行，后续同状态不出（probeOnce 返回 false），变化再出一行；</li>
 *     <li>触发面：interval-seconds≤0 不建定时器只留启动一次；&gt;0 建 daemon 定时器，close() 即关；</li>
 *     <li>缺省探针语义（本地回环 {@link HttpServer}，非外网）：mineru 任意状态码（404 实证）＝可达、
 *         gotenberg 仅 200＝可达（503＝不可达且原因短语入行）、连接拒绝＝不可达。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/14
 */
class AgentCapabilityHealthProbeTest {

    /** 探针替身：按探针名返回固定结果并记录调用（入参=探针名+完整 URL） */
    private static final class StubProber implements BiFunction<String, String, AgentCapabilityHealthProbe.ProbeResult> {
        private final List<String> calls = new ArrayList<>();
        private AgentCapabilityHealthProbe.ProbeResult mineru = AgentCapabilityHealthProbe.ProbeResult.reachable();
        private AgentCapabilityHealthProbe.ProbeResult gotenberg = AgentCapabilityHealthProbe.ProbeResult.reachable();

        @Override
        public AgentCapabilityHealthProbe.ProbeResult apply(String kind, String url) {
            calls.add(kind + " " + url);
            return AgentCapabilityHealthProbe.KIND_GOTENBERG.equals(kind) ? gotenberg : mineru;
        }
    }

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("未配置三态：base-url 空＝替身零调用，两位标「未配置」")
    void blankConfigMeansNotConfiguredWithoutProbing() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("  ", null, 0, stub);
        assertThat(probe.probeOnce()).isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=未配置, artifacts(gotenberg)=未配置");
        assertThat(stub.calls).isEmpty();
    }

    @Test
    @DisplayName("URL 拼装：base 去尾斜杠＋路径（/docs、/health）传入探针")
    void probeUrlsAppendPathOnTrimmedBase() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("http://m:9100/",
                "http://g:13000", 0, stub);
        probe.probeOnce();
        assertThat(stub.calls).containsExactly("mineru http://m:9100/docs", "gotenberg http://g:13000/health");
    }

    @Test
    @DisplayName("状态变化：首轮出一行，同状态不出，降级/再变化各出一行（行内容含原因短语）")
    void logsOnlyOnStateChange() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("http://m", "http://g", 0, stub);

        assertThat(probe.probeOnce()).as("首轮必出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=可达");
        assertThat(probe.probeOnce()).as("同状态不出").isFalse();

        stub.gotenberg = AgentCapabilityHealthProbe.ProbeResult.unreachable("HTTP 503");
        assertThat(probe.probeOnce()).as("降级出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=不可达(HTTP 503)");
        assertThat(probe.probeOnce()).as("同状态仍不出").isFalse();

        stub.gotenberg = AgentCapabilityHealthProbe.ProbeResult.reachable();
        assertThat(probe.probeOnce()).as("恢复出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=可达");
    }

    @Test
    @DisplayName("interval-seconds≤0：只留启动探测不建定时器；>0 建 daemon 定时器，close() 即关")
    void schedulerFollowsIntervalConfig() {
        AgentCapabilityHealthProbe noSchedule = new AgentCapabilityHealthProbe(null, null, 0, new StubProber());
        noSchedule.run(null);
        assertThat(noSchedule.scheduler()).isNull();

        AgentCapabilityHealthProbe scheduled = new AgentCapabilityHealthProbe(null, null, 1, new StubProber());
        scheduled.run(null);
        assertThat(scheduled.scheduler()).isNotNull();
        assertThat(scheduled.scheduler().isShutdown()).isFalse();
        scheduled.close();
        assertThat(scheduled.scheduler().isShutdown()).isTrue();
    }

    @Test
    @DisplayName("缺省探针语义：mineru 404=可达、gotenberg 200=可达/503=不可达(HTTP 503)、连接拒绝=不可达")
    void defaultHttpProbeSemantics() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = "/health".equals(path) ? 200 : "/sick".equals(path) ? 503 : 404;
            byte[] body = "probe".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_MINERU, base + "/docs"))
                .as("mineru：任意 HTTP 状态码（含 404）＝服务在＝可达")
                .satisfies(result -> assertThat(result.status())
                        .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.REACHABLE));
        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_GOTENBERG, base + "/health"))
                .satisfies(result -> assertThat(result.status())
                        .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.REACHABLE));
        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_GOTENBERG, base + "/sick"))
                .as("gotenberg：非 200＝不可达且状态码入原因短语")
                .satisfies(result -> {
                    assertThat(result.status())
                            .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
                    assertThat(result.reason()).isEqualTo("HTTP 503");
                });

        int closedPort = closedPort();
        AgentCapabilityHealthProbe.ProbeResult refused = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_MINERU, "http://127.0.0.1:" + closedPort + "/docs");
        assertThat(refused.status())
                .as("连接拒绝＝不可达（原因短语入行）")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
        assertThat(refused.reason()).isNotBlank();
    }

    /** 取一个已释放的本机端口（连接即拒绝），避免写死端口撞环境 */
    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
