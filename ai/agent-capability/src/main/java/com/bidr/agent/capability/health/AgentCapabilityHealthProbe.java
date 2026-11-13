package com.bidr.agent.capability.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Title: AgentCapabilityHealthProbe
 * Description: agent 依赖底座的<b>能力探活件</b>（棒2，可观测层）——对 ocr(mineru) 与
 * artifacts(gotenberg) 两个外部服务做轻量 HTTP 探测，只出日志不改任何能力位：
 * <ul>
 *     <li><b>不改能力位</b>：{@code /agents} 契约（{@code AgentFileCapabilityResolver#current()}）
 *         仍按声明级推导（Bean 在场即 on），探活结果只进日志——服务挂了能力位照常报 on，
 *         前端零感知（徽标只看 ocr 位）；</li>
 *     <li><b>不阻断启动</b>：探针异常一律吞掉记日志（ApplicationRunner 内全兜底），
 *         探活件坏了不拖垮宿主；</li>
 *     <li>探针口径：ocr(mineru) {@code GET {mineru.local.base-url}/docs}——<b>拿到任何 HTTP
 *         状态码（含 404）＝可达</b>（FastAPI 文档页有无不保证，服务在即可）；连接异常/超时＝不可达。
 *         artifacts(gotenberg) {@code GET {tool.gotenberg.base-url}/health}——200＝可达，
 *         其余状态码记入原因短语；</li>
 *     <li><b>配置空＝未配置</b>：对应探针不发包不计失败（日志标「未配置」）；</li>
 *     <li>触发：启动一次（独立 ApplicationRunner，不塞既有 reporter）＋daemon 定时刷新
 *         （周期 {@code llm.agent.health-probe.interval-seconds} 缺省 300，≤0 关定时只留启动一次）；</li>
 *     <li><b>只在状态变化时打日志</b>（一行摘要 {@code Agent 能力探活: ocr(mineru)=…, artifacts(gotenberg)=…}；
 *         含「不可达」用 WARN＝降级，其余 INFO＝恢复/首行），不刷屏。</li>
 * </ul>
 * 可测性：探针函数以 {@link BiFunction}（入参＝探针名＋完整 URL，出参 {@link ProbeResult}）
 * 构造器注入，单测打桩不打真网；缺省实现 {@link #httpProbe(String, String)} 纯 JDK
 * {@code java.net.http.HttpClient}（超时 3s），零新依赖。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class AgentCapabilityHealthProbe implements ApplicationRunner, AutoCloseable {

    /** 探针名：OCR 转写底座（mineru，GET /docs 任意状态码＝可达） */
    static final String KIND_MINERU = "mineru";

    /** 探针名：产物转换底座（gotenberg，GET /health 200＝可达） */
    static final String KIND_GOTENBERG = "gotenberg";

    private static final HttpClientShim HTTP = new HttpClientShim();

    private final String mineruBaseUrl;

    private final String gotenbergBaseUrl;

    private final long intervalSeconds;

    /** 探针函数（可注入替身）：入参（探针名, 完整 URL）→ 探测结果 */
    private final BiFunction<String, String, ProbeResult> prober;

    /** 上次探测的一行摘要（null＝尚未探测过）；与本次相同＝状态未变不打日志 */
    private volatile String lastLine;

    private volatile ScheduledExecutorService scheduler;

    public AgentCapabilityHealthProbe(String mineruBaseUrl, String gotenbergBaseUrl, long intervalSeconds) {
        this(mineruBaseUrl, gotenbergBaseUrl, intervalSeconds, AgentCapabilityHealthProbe::httpProbe);
    }

    AgentCapabilityHealthProbe(String mineruBaseUrl, String gotenbergBaseUrl, long intervalSeconds,
                               BiFunction<String, String, ProbeResult> prober) {
        this.mineruBaseUrl = trimmed(mineruBaseUrl);
        this.gotenbergBaseUrl = trimmed(gotenbergBaseUrl);
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
        String line = describe(StringUtils.hasText(mineruBaseUrl)
                ? prober.apply(KIND_MINERU, mineruBaseUrl + "/docs") : ProbeResult.notConfigured(),
                StringUtils.hasText(gotenbergBaseUrl)
                        ? prober.apply(KIND_GOTENBERG, gotenbergBaseUrl + "/health") : ProbeResult.notConfigured());
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

    /** 一行摘要（契约格式，探活日志与测试共用同一拼装口） */
    static String describe(ProbeResult mineru, ProbeResult gotenberg) {
        return "Agent 能力探活: ocr(mineru)=" + text(mineru) + ", artifacts(gotenberg)=" + text(gotenberg);
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

    private static String trimmed(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 缺省探针（纯 JDK HttpClient，超时 3s）：mineru＝任意 HTTP 状态码（含 404）即服务在＝可达；
     * gotenberg＝仅 200 可达，其余状态码入原因短语；连接异常/超时＝不可达（异常短语入行）。
     */
    static ProbeResult httpProbe(String kind, String url) {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            java.net.http.HttpResponse<Void> response =
                    HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            if (KIND_GOTENBERG.equals(kind) && response.statusCode() != 200) {
                return ProbeResult.unreachable("HTTP " + response.statusCode());
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

        java.net.http.HttpResponse<Void> send(java.net.http.HttpRequest request,
                                              java.net.http.HttpResponse.BodyHandler<Void> bodyHandler) throws Exception {
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
            return current.send(request, bodyHandler);
        }
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
