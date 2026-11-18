package com.bidr.agent.runtime.config;

import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.document.spi.AgentHealthContributor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Title: OpenHandsHealthContributor
 * Description: OpenHands 沙箱腿的<b>健康自报</b>（AgentHealthContributor 腿实现，2026-11-18
 * 探活架构改造——各腿自报、中央只收集）：探测口径与 {@link OpenHandsClient} 同模块内聚——
 * {@code GET {base-url}/api/agent-profiles} 带 {@code X-Session-API-Key} 会话密钥头：
 * 200＝可达；401＝不可达（密钥无效或未配置，排障方向与「服务不在」区分）；其余状态码带码入原因；
 * 连接异常＝不可达。接线判定同 {@link AgentRuntimeConfigProvider#isConfigured()}（地址与密钥齐备，
 * 缺任一＝NOT_CONFIGURED 不发包）。⚠ 密钥是敏感值：只进请求头，原因短语/异常链路不得出现。
 * bean 注册在 {@link AgentRuntimeAutoConfiguration}（类级 enabled=true＋provider=openhands
 * matchIfMissing，与 {@link OpenHandsClient} bean 同条件）——腿没装＝contributor 不存在＝中央
 * 收集自然缺席；与 Cube 腿 contributor 按 provider 条件互斥，同一时刻至多一条 exec.sandbox 自报。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class OpenHandsHealthContributor implements AgentHealthContributor {

    /** 能力位名（快照键，与 llm AgentFileCapabilityResolver.EXEC_SANDBOX 同字面） */
    static final String CAPABILITY_EXEC_SANDBOX = "exec.sandbox";

    /** 会话密钥请求头名（OpenHandsClient 同款；敏感值只进头） */
    private static final String SESSION_KEY_HEADER = "X-Session-API-Key";

    private final AgentRuntimeConfigProvider config;

    public OpenHandsHealthContributor(AgentRuntimeConfigProvider config) {
        this.config = config;
    }

    @Override
    public String capability() {
        return CAPABILITY_EXEC_SANDBOX;
    }

    @Override
    public String probeName() {
        return "openhands";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public HealthReport probe() {
        if (!config.isConfigured()) {
            return HealthReport.notConfigured();
        }
        String base = config.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/agent-profiles"))
                    .timeout(Duration.ofSeconds(3))
                    .header(SESSION_KEY_HEADER, config.getApiKey())
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code == 200) {
                return HealthReport.reachable();
            }
            return HealthReport.unreachable(code == 401 ? "密钥无效或未配置" : "HTTP " + code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthReport.unreachable(reasonOf(e));
        } catch (Exception e) {
            return HealthReport.unreachable(reasonOf(e));
        }
    }

    /** 异常原因短语：简单名＋首段消息，截 80 字符（只进日志；异常消息可能带 URL 但不含密钥） */
    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        String reason = e.getClass().getSimpleName()
                + (StringUtils.hasText(message) ? ": " + message.split("\n")[0].trim() : "");
        return reason.length() > 80 ? reason.substring(0, 80) : reason;
    }
}
