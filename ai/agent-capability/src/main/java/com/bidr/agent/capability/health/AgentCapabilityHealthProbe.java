package com.bidr.agent.capability.health;

import com.bidr.llm.agent.file.AgentCapabilityHealthProvider;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Title: AgentCapabilityHealthProbe
 * Description: agent 依赖底座的<b>能力探活件</b>（棒2，可观测层）——对 ocr(mineru)、
 * artifacts(gotenberg) 与 exec.sandbox(openhands) 三个外部底座做轻量 HTTP 探测，
 * 结果两路输出：<b>状态变化日志</b>＋<b>运行级健康快照</b>（{@link AgentCapabilityHealthProvider}
 * 端口实现，经 {@code /agents} 契约上报前端徽标），不改任何能力位：
 * <ul>
 *     <li><b>不改能力位</b>：{@code /agents} 契约的 capabilities 仍按声明级推导
 *         （{@code AgentFileCapabilityResolver#current()}），探活失败只进健康快照与日志
 *         （徽标变灰），能力位与相关工具面不受影响；</li>
 *     <li><b>不阻断启动</b>：探针异常一律吞掉记日志（ApplicationRunner 内全兜底），
 *         探活件坏了不拖垮宿主；</li>
 *     <li>探针口径：ocr(mineru) {@code GET {mineru.local.base-url}/docs}——<b>拿到任何 HTTP
 *         状态码（含 404）＝可达</b>（FastAPI 文档页有无不保证，服务在即可）；artifacts(gotenberg)
 *         {@code GET {tool.gotenberg.base-url}/health}——200＝可达，其余状态码记入原因短语；
 *         exec.sandbox(openhands) {@code GET {base}/api/agent-profiles} 带
 *         {@code X-Session-API-Key} 头——200＝可达、401＝不可达（密钥无效或未配置，排障方向与
 *         「服务不在」区分）、其余状态码带码入原因、连接异常＝不可达；</li>
 *     <li><b>配置空＝未配置</b>：对应探针不发包不计失败（日志/快照标「未配置」）；
 *         openhands 腿 base-url 与密钥<b>齐备</b>才发包（装配处经 {@code AgentRuntimeConfigProvider
 *         #isConfigured()} 判定后解析传入，探活件保持纯字符串入参的可测形态）；</li>
 *     <li>触发：启动一次（独立 ApplicationRunner，不塞既有 reporter）＋daemon 定时刷新
 *         （周期 {@code llm.agent.health-probe.interval-seconds} 缺省 300，≤0 关定时只留启动一次）；</li>
 *     <li><b>只在状态变化时打日志</b>（一行摘要 {@code Agent 能力探活: ocr(mineru)=…,
 *         artifacts(gotenberg)=…, exec.sandbox(openhands)=…}；含「不可达」用 WARN＝降级，
 *         其余 INFO＝恢复/首行），不刷屏；</li>
 *     <li><b>健康快照</b>（端口 {@link AgentCapabilityHealthProvider#snapshot()}）：能力位名→
 *         up/down/unconfigured 小写值，探测前初始化为全 unconfigured，每轮探测后整体替换；
 *         {@code snapshot()} 返回不可变副本。⚠ openhands 密钥是敏感值：只进请求头，
 *         日志/原因短语/异常链路均不得出现。</li>
 * </ul>
 * 可测性：探针函数以 {@link ProbeFunction}（入参＝探针名＋完整 URL＋会话密钥，出参 {@link ProbeResult}）
 * 构造器注入，单测打桩不打真网；缺省实现 {@link #httpProbe(String, String, String)} 纯 JDK
 * {@code java.net.http.HttpClient}（超时 3s），零新依赖。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class AgentCapabilityHealthProbe implements ApplicationRunner, AutoCloseable, AgentCapabilityHealthProvider {

    /** 探针名：OCR 转写底座（mineru，GET /docs 任意状态码＝可达） */
    static final String KIND_MINERU = "mineru";

    /** 探针名：产物转换底座（gotenberg，GET /health 200＝可达） */
    static final String KIND_GOTENBERG = "gotenberg";

    /** 探针名：沙箱执行底座（openhands，GET /api/agent-profiles 带 X-Session-API-Key，200＝可达 401＝密钥无效） */
    static final String KIND_OPENHANDS = "openhands";

    /** OpenHands 探活请求头名（会话密钥随头下发，敏感值不进 URL/日志） */
    private static final String OPENHANDS_API_KEY_HEADER = "X-Session-API-Key";

    private static final HttpClientShim HTTP = new HttpClientShim();

    private final String mineruBaseUrl;

    private final String gotenbergBaseUrl;

    /** OpenHands 探活基地址（装配处解析后的生效值；空＝腿未启用/未接线＝快照恒 unconfigured） */
    private final String openhandsBaseUrl;

    /** OpenHands 会话密钥（敏感值：只进请求头，不进日志） */
    private final String openhandsApiKey;

    private final long intervalSeconds;

    /** mineru 探活专用 HTTP 代理：外围件带代理场景的镜像口径——业务链 {@code MineruLocalClient}
     *  走 {@code mineru.proxy.*}，探活不同步走＝直连探测对外网段底座恒 timeout 误报 down
     *  （2026-11-14 pre 实锤：pre 机直连 5.103 不通、代理 200，探活误报）。null＝直连 */
    private final java.net.Proxy mineruProxy;

    /** 探针函数（可注入替身）：入参（探针名, 完整 URL, 会话密钥——仅 openhands 腿使用）→ 探测结果 */
    private final ProbeFunction prober;

    /** 上次探测的一行摘要（null＝尚未探测过）；与本次相同＝状态未变不打日志 */
    private volatile String lastLine;

    /** 运行级健康快照（探测前初始化为全 unconfigured；每轮探测后整体替换） */
    private volatile Map<String, String> lastSnapshot = initialSnapshot();

    private volatile ScheduledExecutorService scheduler;

    public AgentCapabilityHealthProbe(String mineruBaseUrl, String gotenbergBaseUrl, long intervalSeconds,
                                      String openhandsBaseUrl, String openhandsApiKey,
                                      java.net.Proxy mineruProxy) {
        this(mineruBaseUrl, gotenbergBaseUrl, intervalSeconds, openhandsBaseUrl, openhandsApiKey, mineruProxy,
                (kind, url, apiKey) -> httpProbe(kind, url, apiKey, mineruProxy));
    }

    AgentCapabilityHealthProbe(String mineruBaseUrl, String gotenbergBaseUrl, long intervalSeconds,
                               String openhandsBaseUrl, String openhandsApiKey, java.net.Proxy mineruProxy,
                               ProbeFunction prober) {
        this.mineruBaseUrl = trimmed(mineruBaseUrl);
        this.gotenbergBaseUrl = trimmed(gotenbergBaseUrl);
        this.openhandsBaseUrl = trimmed(openhandsBaseUrl);
        this.openhandsApiKey = openhandsApiKey == null ? "" : openhandsApiKey.trim();
        this.mineruProxy = mineruProxy;
        this.intervalSeconds = intervalSeconds;
        this.prober = prober;
    }

    /** 启动一次＋（interval-seconds&gt;0 时）daemon 定时刷新；任何异常吞掉不阻断启动 */
    @Override
    public void run(ApplicationArguments args) {
        safeProbe("启动探测");
        if (intervalSeconds > 0) {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-capability-health-probe");
                thread.setDaemon(true);
                return thread;
            });
            scheduler = executor;
            executor.scheduleWithFixedDelay(this::safeProbeTick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    /** 探测一轮：算出一行摘要，与上次相同不打（返回 false＝状态未变）；变化打一行（含「不可达」WARN） */
    boolean probeOnce() {
        ProbeResult mineru = StringUtils.hasText(mineruBaseUrl)
                ? prober.probe(KIND_MINERU, mineruBaseUrl + "/docs", null) : ProbeResult.notConfigured();
        ProbeResult gotenberg = StringUtils.hasText(gotenbergBaseUrl)
                ? prober.probe(KIND_GOTENBERG, gotenbergBaseUrl + "/health", null) : ProbeResult.notConfigured();
        // openhands：base-url 与密钥齐备才发包（缺任一＝未配置，不发包不计失败）
        boolean openhandsConfigured = StringUtils.hasText(openhandsBaseUrl) && StringUtils.hasText(openhandsApiKey);
        ProbeResult openhands = openhandsConfigured
                ? prober.probe(KIND_OPENHANDS, openhandsBaseUrl + "/api/agent-profiles", openhandsApiKey)
                : ProbeResult.notConfigured();
        lastSnapshot = snapshotOf(mineru, gotenberg, openhands);
        String line = describe(mineru, gotenberg, openhands);
        String previous = lastLine;
        lastLine = line;
        if (line.equals(previous)) {
            return false;
        }
        if (line.contains("不可达")) {
            log.warn("{}", line);
        } else {
            log.info("{}", line);
        }
        return true;
    }

    private void safeProbe(String phase) {
        try {
            probeOnce();
        } catch (Exception e) {
            log.warn("Agent 能力探活{}异常（不阻断启动，不改能力位）: {}", phase, String.valueOf(e.getMessage()));
        }
    }

    private void safeProbeTick() {
        try {
            probeOnce();
        } catch (Exception e) {
            // 定时轮次异常吞掉：探活绝不反向影响宿主
        }
    }

    /** 一行摘要（契约格式，探活日志与测试共用同一拼装口；第三位＝exec.sandbox(openhands)） */
    static String describe(ProbeResult mineru, ProbeResult gotenberg, ProbeResult openhands) {
        return "Agent 能力探活: ocr(mineru)=" + text(mineru)
                + ", artifacts(gotenberg)=" + text(gotenberg)
                + ", exec.sandbox(openhands)=" + text(openhands);
    }

    private static String text(ProbeResult result) {
        switch (result.status()) {
            case REACHABLE:
                return "可达";
            case NOT_CONFIGURED:
                return "未配置";
            default:
                return "不可达(" + (StringUtils.hasText(result.reason()) ? result.reason() : "原因未知") + ")";
        }
    }

    /** 探测结果→健康快照（能力位名→up/down/unconfigured；映射：mineru→ocr、gotenberg→artifacts.doc、openhands→exec.sandbox） */
    private static Map<String, String> snapshotOf(ProbeResult mineru, ProbeResult gotenberg, ProbeResult openhands) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put(AgentFileCapabilityResolver.OCR, healthValue(mineru));
        snapshot.put(AgentFileCapabilityResolver.ARTIFACTS_DOC, healthValue(gotenberg));
        snapshot.put(AgentFileCapabilityResolver.EXEC_SANDBOX, healthValue(openhands));
        return snapshot;
    }

    /** 探测结果三态→健康值小写常量（REACHABLE=up / NOT_CONFIGURED=unconfigured / UNREACHABLE=down） */
    private static String healthValue(ProbeResult result) {
        switch (result.status()) {
            case REACHABLE:
                return AgentCapabilityHealthProvider.UP;
            case NOT_CONFIGURED:
                return AgentCapabilityHealthProvider.UNCONFIGURED;
            default:
                return AgentCapabilityHealthProvider.DOWN;
        }
    }

    /** 探测前初始快照：三位全 unconfigured（未探测＝未知＝按未配置上报，前端灰态提示） */
    private static Map<String, String> initialSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UNCONFIGURED);
        snapshot.put(AgentFileCapabilityResolver.ARTIFACTS_DOC, AgentCapabilityHealthProvider.UNCONFIGURED);
        snapshot.put(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UNCONFIGURED);
        return snapshot;
    }

    /** 健康快照（端口实现）：不可变副本，读侧共享安全 */
    @Override
    public Map<String, String> snapshot() {
        return Map.copyOf(lastSnapshot);
    }

    private static String trimmed(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 缺省探针（纯 JDK HttpClient，超时 3s）：mineru＝任意 HTTP 状态码（含 404）即服务在＝可达；
     * gotenberg＝仅 200 可达，其余状态码入原因短语；openhands＝200 可达、401＝不可达
     * （密钥无效或未配置）、其余状态码带码入原因；连接异常/超时＝不可达（异常短语入行）。
     * ⚠ 密钥只进请求头：异常消息可能带 URL（不含密钥），原因短语不含密钥。
     */
    static ProbeResult httpProbe(String kind, String url, String apiKey, java.net.Proxy mineruProxy) {
        try {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            if (KIND_OPENHANDS.equals(kind)) {
                try {
                    builder.header(OPENHANDS_API_KEY_HEADER, apiKey);
                } catch (IllegalArgumentException e) {
                    // 🔴 header 值非法（控制字符/非 Latin-1，如粘贴进全角空格）时 JDK 的异常
                    // 消息含密钥原文（HttpRequestBuilderImpl 直接拼 value）——必须原地转固定
                    // 短语，原消息不得进入 reasonOf/日志（review I-1 泄漏窄路径，2026-11-14）
                    return ProbeResult.unreachable("密钥格式非法（含控制字符或非 ASCII 字符）");
                }
            }
            java.net.http.HttpResponse<Void> response =
                    HTTP.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.discarding(),
                            KIND_MINERU.equals(kind) ? mineruProxy : null);
            if (KIND_GOTENBERG.equals(kind) && response.statusCode() != 200) {
                return ProbeResult.unreachable("HTTP " + response.statusCode());
            }
            if (KIND_OPENHANDS.equals(kind) && response.statusCode() != 200) {
                return ProbeResult.unreachable(response.statusCode() == 401
                        ? "密钥无效或未配置" : "HTTP " + response.statusCode());
            }
            return ProbeResult.reachable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeResult.unreachable(reasonOf(e));
        } catch (Exception e) {
            return ProbeResult.unreachable(reasonOf(e));
        }
    }

    /** 原因短语：异常简单名＋首段消息，截到 80 字符（只进日志，不做机器消费） */
    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        String reason = e.getClass().getSimpleName()
                + (StringUtils.hasText(message) ? ": " + message.split("\n")[0].trim() : "");
        return reason.length() > 80 ? reason.substring(0, 80) : reason;
    }

    /** 上次探测的一行摘要（测试口；null＝尚未探测） */
    String lastLoggedLine() {
        return lastLine;
    }

    /** 定时器在场与否（测试口；interval-seconds≤0 恒 null） */
    ScheduledExecutorService scheduler() {
        return scheduler;
    }

    @Override
    public void close() {
        ScheduledExecutorService executor = scheduler;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** HttpClient 惰性持有壳（build 一次复用连接池；静态直建会让单测类加载即起网络栈，壳可延迟） */
    private static final class HttpClientShim {
        private volatile java.net.http.HttpClient client;

        /** 代理专用 client 缓存（key=proxy 地址串；探活频率低，数量小，不做淘汰） */
        private final java.util.concurrent.ConcurrentHashMap<String, java.net.http.HttpClient> proxyClients =
                new java.util.concurrent.ConcurrentHashMap<>();

        java.net.http.HttpResponse<Void> send(java.net.http.HttpRequest request,
                                              java.net.http.HttpResponse.BodyHandler<Void> bodyHandler,
                                              java.net.Proxy proxy) throws Exception {
            java.net.http.HttpClient current;
            if (proxy == null) {
                current = sharedClient();
            } else {
                // 代理专用 client 按 host:port 缓存（探活频率低，避免每次重建连接池）
                current = proxyClients.computeIfAbsent(proxy.address().toString(), k ->
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .proxy(java.net.ProxySelector.of((java.net.InetSocketAddress) proxy.address()))
                                .build());
            }
            return current.send(request, bodyHandler);
        }

        private java.net.http.HttpClient sharedClient() {
            java.net.http.HttpClient current = client;
            if (current == null) {
                synchronized (this) {
                    current = client;
                    if (current == null) {
                        current = java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .build();
                        client = current;
                    }
                }
            }
            return current;
        }
    }

    /** 探针函数契约（可注入替身）：入参（探针名, 完整 URL, 会话密钥——仅 openhands 腿携带）→ 探测结果 */
    @FunctionalInterface
    interface ProbeFunction {

        ProbeResult probe(String kind, String url, String apiKey);
    }

    /** 探测结果三态值对象：可达 / 未配置 / 不可达（带原因短语，日志用） */
    public record ProbeResult(Status status, String reason) {

        public enum Status {REACHABLE, NOT_CONFIGURED, UNREACHABLE}

        static ProbeResult reachable() {
            return new ProbeResult(Status.REACHABLE, null);
        }

        static ProbeResult notConfigured() {
            return new ProbeResult(Status.NOT_CONFIGURED, null);
        }

        static ProbeResult unreachable(String reason) {
            return new ProbeResult(Status.UNREACHABLE, reason);
        }
    }
}
