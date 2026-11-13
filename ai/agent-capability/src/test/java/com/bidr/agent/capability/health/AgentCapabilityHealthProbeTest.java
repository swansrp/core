package com.bidr.agent.capability.health;

import com.bidr.llm.agent.file.AgentCapabilityHealthProvider;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Title: AgentCapabilityHealthProbeTest
 * Description: 探活件契约钉桩（探针注入替身，不打外网）：
 * <ul>
 *     <li>未配置三态：base-url 空＝不发包（替身零调用）不计失败，日志位标「未配置」；</li>
 *     <li>URL 拼装：base 去尾斜杠后拼 {@code /docs}（mineru）、{@code /health}（gotenberg）、
 *         {@code /api/agent-profiles}（openhands）；openhands base-url 或密钥缺任一＝不发包；</li>
 *     <li>状态变化：首轮必出一行，后续同状态不出（probeOnce 返回 false），变化再出一行；</li>
 *     <li>触发面：interval-seconds≤0 不建定时器只留启动一次；&gt;0 建 daemon 定时器，close() 即关；</li>
 *     <li>缺省探针语义（本地回环 {@link HttpServer}，非外网）：mineru 任意状态码（404 实证）＝可达、
 *         gotenberg 仅 200＝可达（503＝不可达且原因短语入行）、openhands 200＝可达（密钥经
 *         X-Session-API-Key 头下发且不入原因短语）、401＝不可达（密钥无效或未配置）、
 *         连接拒绝＝不可达；</li>
 *     <li>健康快照：探测前三位全 unconfigured，探测后按腿映射刷新（key=能力位名，value 小写），</li>
 *     <li>snapshot() 不可变副本（put 抛 UnsupportedOperationException）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/14
 */
class AgentCapabilityHealthProbeTest {

    /** 探针替身：按探针名返回固定结果并记录调用（入参=探针名+完整 URL+会话密钥） */
    private static final class StubProber implements AgentCapabilityHealthProbe.ProbeFunction {
        private final List<String> calls = new ArrayList<>();
        private AgentCapabilityHealthProbe.ProbeResult mineru = AgentCapabilityHealthProbe.ProbeResult.reachable();
        private AgentCapabilityHealthProbe.ProbeResult gotenberg = AgentCapabilityHealthProbe.ProbeResult.reachable();
        private AgentCapabilityHealthProbe.ProbeResult openhands = AgentCapabilityHealthProbe.ProbeResult.reachable();

        @Override
        public AgentCapabilityHealthProbe.ProbeResult probe(String kind, String url, String apiKey) {
            calls.add(kind + " " + url);
            if (AgentCapabilityHealthProbe.KIND_GOTENBERG.equals(kind)) {
                return gotenberg;
            }
            if (AgentCapabilityHealthProbe.KIND_OPENHANDS.equals(kind)) {
                return openhands;
            }
            return mineru;
        }
    }

    private HttpServer server;

    /** 最近一轮被靶机抓到的 X-Session-API-Key 头值（openhands 腿密钥下发断言用；建靶机时重置） */
    private List<String> capturedKeys = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("未配置三态：base-url 空＝替身零调用，三位标「未配置」")
    void blankConfigMeansNotConfiguredWithoutProbing() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("  ", null, 0, "", "", stub);
        assertThat(probe.probeOnce()).isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=未配置, artifacts(gotenberg)=未配置, exec.sandbox(openhands)=未配置");
        assertThat(stub.calls).isEmpty();
    }

    @Test
    @DisplayName("URL 拼装：base 去尾斜杠＋路径（/docs、/health）传入探针；openhands 缺密钥＝不发包")
    void probeUrlsAppendPathOnTrimmedBase() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("http://m:9100/",
                "http://g:13000", 0, "", "", stub);
        probe.probeOnce();
        assertThat(stub.calls).containsExactly("mineru http://m:9100/docs", "gotenberg http://g:13000/health");

        // openhands：base-url 在而密钥空＝同样不发包（齐备才探测）
        StubProber keyed = new StubProber();
        AgentCapabilityHealthProbe halfConfigured = new AgentCapabilityHealthProbe(null, null, 0,
                "http://o:8080", "  ", keyed);
        halfConfigured.probeOnce();
        assertThat(keyed.calls).as("密钥空＝不发包").isEmpty();
        assertThat(halfConfigured.snapshot()).containsEntry("exec.sandbox", "unconfigured");
    }

    @Test
    @DisplayName("状态变化：首轮出一行，同状态不出，降级/再变化各出一行（行内容含原因短语，第三位 exec.sandbox）")
    void logsOnlyOnStateChange() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe("http://m", "http://g", 0,
                "http://o", "sk", stub);

        assertThat(probe.probeOnce()).as("首轮必出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=可达, exec.sandbox(openhands)=可达");
        assertThat(probe.probeOnce()).as("同状态不出").isFalse();

        stub.gotenberg = AgentCapabilityHealthProbe.ProbeResult.unreachable("HTTP 503");
        assertThat(probe.probeOnce()).as("降级出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=不可达(HTTP 503), exec.sandbox(openhands)=可达");
        assertThat(probe.probeOnce()).as("同状态仍不出").isFalse();

        stub.gotenberg = AgentCapabilityHealthProbe.ProbeResult.reachable();
        assertThat(probe.probeOnce()).as("恢复出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts(gotenberg)=可达, exec.sandbox(openhands)=可达");
    }

    @Test
    @DisplayName("interval-seconds≤0：只留启动探测不建定时器；>0 建 daemon 定时器，close() 即关")
    void schedulerFollowsIntervalConfig() {
        AgentCapabilityHealthProbe noSchedule = new AgentCapabilityHealthProbe(null, null, 0, "", "", new StubProber());
        noSchedule.run(null);
        assertThat(noSchedule.scheduler()).isNull();

        AgentCapabilityHealthProbe scheduled = new AgentCapabilityHealthProbe(null, null, 1, "", "", new StubProber());
        scheduled.run(null);
        assertThat(scheduled.scheduler()).isNotNull();
        assertThat(scheduled.scheduler().isShutdown()).isFalse();
        scheduled.close();
        assertThat(scheduled.scheduler().isShutdown()).isTrue();
    }

    @Test
    @DisplayName("快照：探测前全 unconfigured；探测后按腿映射刷新；snapshot() 不可变副本")
    void snapshotLifecycleAndImmutability() {
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(null, null, 0, "", "", new StubProber());

        Map<String, String> initial = probe.snapshot();
        assertThat(initial)
                .as("探测前三位全 unconfigured")
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UNCONFIGURED)
                .containsEntry(AgentFileCapabilityResolver.ARTIFACTS_DOC, AgentCapabilityHealthProvider.UNCONFIGURED)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UNCONFIGURED);
        assertThatThrownBy(() -> initial.put("ocr", "up"))
                .as("snapshot() 必须是不可变副本")
                .isInstanceOf(UnsupportedOperationException.class);

        StubProber stub = new StubProber();
        stub.gotenberg = AgentCapabilityHealthProbe.ProbeResult.unreachable("HTTP 503");
        AgentCapabilityHealthProbe probed = new AgentCapabilityHealthProbe("http://m", "http://g", 0,
                "http://o", "sk", stub);
        probed.probeOnce();

        Map<String, String> snapshot = probed.snapshot();
        assertThat(snapshot)
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UP)
                .containsEntry(AgentFileCapabilityResolver.ARTIFACTS_DOC, AgentCapabilityHealthProvider.DOWN)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UP);
        assertThatThrownBy(() -> snapshot.put("ocr", "down"))
                .as("探测后的快照同样不可变")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("缺省探针语义：mineru 404=可达、gotenberg 200=可达/503=不可达(HTTP 503)、连接拒绝=不可达")
    void defaultHttpProbeSemantics() throws Exception {
        server = openhandsCaptureServer();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_MINERU, base + "/docs", null))
                .as("mineru：任意 HTTP 状态码（含 404）＝服务在＝可达")
                .satisfies(result -> assertThat(result.status())
                        .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.REACHABLE));
        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_GOTENBERG, base + "/health", null))
                .satisfies(result -> assertThat(result.status())
                        .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.REACHABLE));
        assertThat(AgentCapabilityHealthProbe.httpProbe(AgentCapabilityHealthProbe.KIND_GOTENBERG, base + "/sick", null))
                .as("gotenberg：非 200＝不可达且状态码入原因短语")
                .satisfies(result -> {
                    assertThat(result.status())
                            .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
                    assertThat(result.reason()).isEqualTo("HTTP 503");
                });

        int closedPort = closedPort();
        AgentCapabilityHealthProbe.ProbeResult refused = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_MINERU, "http://127.0.0.1:" + closedPort + "/docs", null);
        assertThat(refused.status())
                .as("连接拒绝＝不可达（原因短语入行）")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
        assertThat(refused.reason()).isNotBlank();
    }

    @Test
    @DisplayName("openhands 腿缺省探针：200=可达（密钥经 X-Session-API-Key 头）、401=不可达（密钥无效或未配置，key 不入原因）")
    void openhandsLegDefaultProbeSemantics() throws Exception {
        server = openhandsCaptureServer();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        AgentCapabilityHealthProbe.ProbeResult ok = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_OPENHANDS, base + "/api/agent-profiles", "sk-secret-1");
        assertThat(ok.status())
                .as("200＝可达")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.REACHABLE);
        assertThat(capturedKeys).as("密钥经 X-Session-API-Key 头下发").containsExactly("sk-secret-1");

        AgentCapabilityHealthProbe.ProbeResult unauthorized = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_OPENHANDS, base + "/unauthorized", "sk-secret-2");
        assertThat(unauthorized.status())
                .as("401＝不可达（排障方向与「服务不在」区分）")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
        assertThat(unauthorized.reason()).isEqualTo("密钥无效或未配置");
        assertThat(String.valueOf(unauthorized.reason()))
                .as("⚠ 密钥不得进原因短语/日志链路")
                .doesNotContain("sk-secret-2");

        AgentCapabilityHealthProbe.ProbeResult unexpected = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_OPENHANDS, base + "/sick", "sk-secret-3");
        assertThat(unexpected.status())
                .as("其余非 200 状态码＝不可达且带码入原因")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
        assertThat(unexpected.reason()).isEqualTo("HTTP 503");

        // review I-1（2026-11-14）：坏 key（含非 Latin-1 字符）触发 JDK header 校验异常——
        // 其异常消息含密钥原文，必须原地转固定短语，原消息不得进入原因短语/日志链路
        String badKey = "sk-\u3000bad-key\u3000"; // 全角空格＝非法头值，httpProbe 在发包前即抛
        AgentCapabilityHealthProbe.ProbeResult malformed = AgentCapabilityHealthProbe.httpProbe(
                AgentCapabilityHealthProbe.KIND_OPENHANDS, base + "/api/agent-profiles", badKey);
        assertThat(malformed.status())
                .as("密钥格式非法＝不可达（不发包）")
                .isEqualTo(AgentCapabilityHealthProbe.ProbeResult.Status.UNREACHABLE);
        assertThat(malformed.reason()).isEqualTo("密钥格式非法（含控制字符或非 ASCII 字符）");
        assertThat(String.valueOf(malformed.reason()))
                .as("⚠ 原消息（含密钥原文）不得进入原因短语")
                .doesNotContain("bad-key");
    }

    @Test
    @DisplayName("openhands 腿在位：齐备才发包，URL 拼装 /api/agent-profiles；快照同步三位")
    void openhandsLegProbesWhenFullyConfigured() {
        StubProber stub = new StubProber();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(null, null, 0,
                "http://o:8080/", "sk-x", stub);
        assertThat(probe.probeOnce()).isTrue();
        assertThat(stub.calls).contains("openhands http://o:8080/api/agent-profiles");
        assertThat(probe.snapshot()).containsEntry(
                AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UP);
    }

    /** 本地回环探针靶机：/health=200、/sick=503、/unauthorized=401、其余 404；
     *  openhands 类路径（/api/agent-profiles、/unauthorized）抓取 X-Session-API-Key 供断言 */
    private HttpServer openhandsCaptureServer() throws Exception {
        capturedKeys = new ArrayList<>();
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = "/health".equals(path) || "/api/agent-profiles".equals(path) ? 200
                    : "/sick".equals(path) ? 503 : "/unauthorized".equals(path) ? 401 : 404;
            if ("/api/agent-profiles".equals(path) || "/unauthorized".equals(path)) {
                capturedKeys.add(exchange.getRequestHeaders().getFirst("X-Session-API-Key"));
            }
            byte[] body = "probe".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        created.start();
        return created;
    }

    /** 取一个已释放的本机端口（连接即拒绝），避免写死端口撞环境 */
    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
